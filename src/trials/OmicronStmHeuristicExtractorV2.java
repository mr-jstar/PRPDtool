import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Heuristic extractor v2 for OMICRON .stm files.
 *
 * Still NOT a confirmed format parser. This version improves on v1 by:
 *  - scanning many local windows instead of only one early sample,
 *  - scoring local candidate blocks using phase/value/time quality,
 *  - measuring phase histogram spread/entropy,
 *  - filtering obviously absurd records before CSV export,
 *  - exporting a block summary and top candidate windows.
 *
 * Usage:
 *   java OmicronStmHeuristicExtractorV2 file1.stm [file2.stm ...]
 */
public class OmicronStmHeuristicExtractorV2 {

    private static final String MAGIC = "mtronix Stream File";
    private static final long DEFAULT_SCAN_START = 8192;
    private static final int WINDOW_BYTES = 256 * 1024;
    private static final int WINDOW_STRIDE = 16384;
    private static final int MAX_BASE_JITTER = 64;
    private static final int MAX_RECORDS_PER_WINDOW = 12000;
    private static final int MAX_TOP_BLOCKS = 24;
    private static final int DEFAULT_DUMP_RECORDS = 50000;
    private static final long MAX_SCAN_BYTES = 8L * 1024 * 1024;

    enum FieldType { U32, U64, F32 }

    static final class Field {
        final String name;
        final FieldType type;
        final int offset;
        Field(String name, FieldType type, int offset) {
            this.name = name;
            this.type = type;
            this.offset = offset;
        }
    }

    static final class Template {
        final String name;
        final int recordLength;
        final Field[] fields;
        final boolean hasChannel;
        Template(String name, int recordLength, Field... fields) {
            this.name = name;
            this.recordLength = recordLength;
            this.fields = fields;
            boolean hc = false;
            for (Field f : fields) if ("chan".equals(f.name)) hc = true;
            this.hasChannel = hc;
        }
        Field get(String name) {
            for (Field f : fields) if (f.name.equals(name)) return f;
            return null;
        }
    }

    static final class BlockHypothesis implements Comparable<BlockHypothesis> {
        final Template template;
        final int baseOffset;
        final int windowBytes;
        final int recordsScored;
        final double score;
        final double phaseFraction;
        final double finiteValueFraction;
        final double nonZeroValueFraction;
        final double monotonicTimeFraction;
        final double positiveTimeDeltaFraction;
        final double phaseEntropyNorm;
        final double phaseCoverage;
        final double phasePeakFraction;
        final double valueDynamicFraction;
        final int channelCardinality;
        final float valueAbsP50;
        final float valueAbsP95;
        final long firstTime;
        final long lastTime;

        BlockHypothesis(Template template, int baseOffset, int windowBytes, int recordsScored,
                        double score, double phaseFraction, double finiteValueFraction,
                        double nonZeroValueFraction, double monotonicTimeFraction,
                        double positiveTimeDeltaFraction, double phaseEntropyNorm,
                        double phaseCoverage, double phasePeakFraction, double valueDynamicFraction,
                        int channelCardinality, float valueAbsP50, float valueAbsP95,
                        long firstTime, long lastTime) {
            this.template = template;
            this.baseOffset = baseOffset;
            this.windowBytes = windowBytes;
            this.recordsScored = recordsScored;
            this.score = score;
            this.phaseFraction = phaseFraction;
            this.finiteValueFraction = finiteValueFraction;
            this.nonZeroValueFraction = nonZeroValueFraction;
            this.monotonicTimeFraction = monotonicTimeFraction;
            this.positiveTimeDeltaFraction = positiveTimeDeltaFraction;
            this.phaseEntropyNorm = phaseEntropyNorm;
            this.phaseCoverage = phaseCoverage;
            this.phasePeakFraction = phasePeakFraction;
            this.valueDynamicFraction = valueDynamicFraction;
            this.channelCardinality = channelCardinality;
            this.valueAbsP50 = valueAbsP50;
            this.valueAbsP95 = valueAbsP95;
            this.firstTime = firstTime;
            this.lastTime = lastTime;
        }

        @Override public int compareTo(BlockHypothesis other) {
            return Double.compare(other.score, this.score);
        }
    }

    private static final Template[] TEMPLATES = new Template[] {
            new Template("u32_time_f32_phase_f32_value", 12,
                    new Field("time", FieldType.U32, 0),
                    new Field("phase", FieldType.F32, 4),
                    new Field("value", FieldType.F32, 8)),

            new Template("f32_phase_f32_value_u32_time", 12,
                    new Field("phase", FieldType.F32, 0),
                    new Field("value", FieldType.F32, 4),
                    new Field("time", FieldType.U32, 8)),

            new Template("u32_time_f32_phase_f32_value_u32_chan", 16,
                    new Field("time", FieldType.U32, 0),
                    new Field("phase", FieldType.F32, 4),
                    new Field("value", FieldType.F32, 8),
                    new Field("chan", FieldType.U32, 12)),

            new Template("u64_time_f32_phase_f32_value", 16,
                    new Field("time", FieldType.U64, 0),
                    new Field("phase", FieldType.F32, 8),
                    new Field("value", FieldType.F32, 12)),

            new Template("u64_time_f32_phase_f32_value_u32_chan", 20,
                    new Field("time", FieldType.U64, 0),
                    new Field("phase", FieldType.F32, 8),
                    new Field("value", FieldType.F32, 12),
                    new Field("chan", FieldType.U32, 16)),

            new Template("u32_time_u32_chan_f32_phase_f32_value", 16,
                    new Field("time", FieldType.U32, 0),
                    new Field("chan", FieldType.U32, 4),
                    new Field("phase", FieldType.F32, 8),
                    new Field("value", FieldType.F32, 12))
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java OmicronStmHeuristicExtractorV2 file1.stm [file2.stm ...]");
            return;
        }
        for (String arg : args) analyze(Paths.get(arg));
    }

    private static void analyze(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        System.out.println("============================================================");
        System.out.println("File: " + path.getFileName());
        System.out.println("Size: " + data.length + " bytes");
        checkMagic(data);

        List<BlockHypothesis> best = scanBestBlocks(data);
        if (best.isEmpty()) {
            System.out.println("No plausible blocks found.");
            return;
        }

        System.out.println("\nTop local candidate blocks:");
        for (int i = 0; i < Math.min(12, best.size()); i++) {
            BlockHypothesis h = best.get(i);
            System.out.printf(Locale.US,
                    "#%d score=%.4f off=0x%X len=%d recLen=%d tpl=%s recs=%d phase=%.3f finite=%.3f nz=%.3f mono=%.3f dt+=%.3f ent=%.3f cov=%.3f peak=%.3f dyn=%.3f chan=%d |v|p50=%.6g |v|p95=%.6g%n",
                    i + 1, h.score, h.baseOffset, h.windowBytes, h.template.recordLength,
                    h.template.name, h.recordsScored, h.phaseFraction, h.finiteValueFraction,
                    h.nonZeroValueFraction, h.monotonicTimeFraction, h.positiveTimeDeltaFraction,
                    h.phaseEntropyNorm, h.phaseCoverage, h.phasePeakFraction,
                    h.valueDynamicFraction, h.channelCardinality, h.valueAbsP50, h.valueAbsP95);
        }

        BlockHypothesis chosen = chooseRepresentative(best);
        System.out.println("\nChosen block for CSV export:");
        System.out.printf(Locale.US,
                "  off=0x%X template=%s score=%.4f records=%d%n",
                chosen.baseOffset, chosen.template.name, chosen.score, chosen.recordsScored);

        Path csv = path.resolveSibling(path.getFileName().toString() + ".heuristic.v2.csv");
        Path txt = path.resolveSibling(path.getFileName().toString() + ".heuristic.v2.summary.txt");
        writeSummary(best, chosen, txt);
        dumpCsvFiltered(data, chosen, csv, DEFAULT_DUMP_RECORDS);

        System.out.println("Wrote summary: " + txt);
        System.out.println("Wrote CSV:     " + csv);
        System.out.println();
    }

    private static void checkMagic(byte[] data) {
        String prefix = new String(data, 0, Math.min(MAGIC.length(), data.length), StandardCharsets.US_ASCII);
        System.out.println("Magic ok: " + MAGIC.equals(prefix) + " [" + prefix + "]");
    }

    private static List<BlockHypothesis> scanBestBlocks(byte[] data) {
        long scanEndLong = Math.min(data.length, DEFAULT_SCAN_START + MAX_SCAN_BYTES);
        int scanEnd = (int) scanEndLong;
        PriorityQueue<BlockHypothesis> pq = new PriorityQueue<>(MAX_TOP_BLOCKS, Comparator.reverseOrder());

        for (Template t : TEMPLATES) {
            for (int windowBase = (int) DEFAULT_SCAN_START;
                 windowBase + WINDOW_BYTES + t.recordLength < scanEnd;
                 windowBase += WINDOW_STRIDE) {
                for (int jitter = 0; jitter < Math.min(MAX_BASE_JITTER, t.recordLength); jitter++) {
                    int base = windowBase + jitter;
                    BlockHypothesis h = scoreBlock(data, base, WINDOW_BYTES, t);
                    if (h == null) continue;
                    offerDistinct(pq, h);
                }
            }
        }

        List<BlockHypothesis> out = new ArrayList<>(pq);
        Collections.sort(out);
        return out;
    }

    private static void offerDistinct(PriorityQueue<BlockHypothesis> pq, BlockHypothesis h) {
        for (BlockHypothesis existing : pq) {
            if (existing.template == h.template && Math.abs(existing.baseOffset - h.baseOffset) < WINDOW_BYTES / 4) {
                if (h.score > existing.score) {
                    pq.remove(existing);
                    pq.offer(h);
                }
                return;
            }
        }
        pq.offer(h);
        while (pq.size() > MAX_TOP_BLOCKS) pq.poll();
    }

    private static BlockHypothesis scoreBlock(byte[] data, int base, int windowBytes, Template t) {
        if (base + t.recordLength >= data.length) return null;
        int available = Math.min(windowBytes, data.length - base);
        int records = Math.min(MAX_RECORDS_PER_WINDOW, available / t.recordLength);
        if (records < 2000) return null;

        Field phaseField = t.get("phase");
        Field valueField = t.get("value");
        Field timeField = t.get("time");
        Field chanField = t.get("chan");
        if (phaseField == null || valueField == null) return null;

        int phaseGood = 0;
        int valueFinite = 0;
        int valueNonZero = 0;
        int monoGood = 0;
        int monoTotal = 0;
        int positiveDt = 0;
        int positiveDtTotal = 0;
        int dynamicValues = 0;
        long prevTime = Long.MIN_VALUE;
        long firstTime = 0L;
        long lastTime = 0L;
        boolean haveTime = false;
        Set<Long> channelSet = new HashSet<>();
        int[] phaseHist = new int[36];
        float[] absValues = new float[records];
        int absCount = 0;

        for (int i = 0; i < records; i++) {
            int off = base + i * t.recordLength;
            float phase = readF32(data, off + phaseField.offset);
            float value = readF32(data, off + valueField.offset);

            if (Float.isFinite(phase) && phase >= 0.0f && phase <= 360.0f) {
                phaseGood++;
                int bin = (phase >= 360.0f) ? 35 : Math.min(35, Math.max(0, (int) (phase / 10.0f)));
                phaseHist[bin]++;
            }
            if (Float.isFinite(value) && Math.abs(value) < 1.0e12f) {
                valueFinite++;
                float av = Math.abs(value);
                absValues[absCount++] = av;
                if (av > 1.0e-12f) valueNonZero++;
                if (av > 1.0e-6f && av < 1.0e9f) dynamicValues++;
            }

            if (timeField != null) {
                long time = timeField.type == FieldType.U64
                        ? readU64(data, off + timeField.offset)
                        : Integer.toUnsignedLong(readU32(data, off + timeField.offset));
                if (!haveTime) {
                    firstTime = time;
                    haveTime = true;
                }
                lastTime = time;
                if (prevTime != Long.MIN_VALUE) {
                    if (Long.compareUnsigned(time, prevTime) >= 0) monoGood++;
                    monoTotal++;
                    if (Long.compareUnsigned(time, prevTime) > 0) positiveDt++;
                    positiveDtTotal++;
                }
                prevTime = time;
            }

            if (chanField != null && channelSet.size() <= 512) {
                channelSet.add(Integer.toUnsignedLong(readU32(data, off + chanField.offset)));
            }
        }

        if (absCount == 0) return null;
        Arrays.sort(absValues, 0, absCount);
        float absP50 = percentileSorted(absValues, absCount, 0.50);
        float absP95 = percentileSorted(absValues, absCount, 0.95);

        double phaseFraction = phaseGood / (double) records;
        double finiteValueFraction = valueFinite / (double) records;
        double nonZeroValueFraction = valueNonZero / (double) records;
        double monotonicTimeFraction = monoTotal == 0 ? 0.0 : monoGood / (double) monoTotal;
        double positiveTimeDeltaFraction = positiveDtTotal == 0 ? 0.0 : positiveDt / (double) positiveDtTotal;
        double valueDynamicFraction = dynamicValues / (double) records;
        int channelCardinality = chanField == null ? -1 : channelSet.size();

        HistogramStats hs = histogramStats(phaseHist, phaseGood);

        double score = 0.0;
        score += 1.10 * phaseFraction;
        score += 0.50 * finiteValueFraction;
        score += 0.30 * nonZeroValueFraction;
        score += 0.50 * monotonicTimeFraction;
        score += 0.25 * positiveTimeDeltaFraction;
        score += 0.55 * hs.entropyNorm;
        score += 0.35 * hs.coverage;
        score += 0.25 * valueDynamicFraction;
        score -= 0.35 * hs.peakFraction;

        if (chanField != null && channelCardinality > 0 && channelCardinality <= 32) score += 0.20;
        if (absP95 > absP50 * 1.25f) score += 0.10;
        if (phaseFraction < 0.30 || finiteValueFraction < 0.55) return null;
        if (score < 1.55) return null;

        return new BlockHypothesis(t, base, windowBytes, records, score, phaseFraction,
                finiteValueFraction, nonZeroValueFraction, monotonicTimeFraction,
                positiveTimeDeltaFraction, hs.entropyNorm, hs.coverage, hs.peakFraction,
                valueDynamicFraction, channelCardinality, absP50, absP95, firstTime, lastTime);
    }

    private static BlockHypothesis chooseRepresentative(List<BlockHypothesis> blocks) {
        // Prefer the best score, but slightly favor candidate blocks that do not collapse phase into one bin.
        BlockHypothesis best = blocks.get(0);
        double bestScore = representativeScore(best);
        for (int i = 1; i < Math.min(8, blocks.size()); i++) {
            BlockHypothesis h = blocks.get(i);
            double s = representativeScore(h);
            if (s > bestScore) {
                best = h;
                bestScore = s;
            }
        }
        return best;
    }

    private static double representativeScore(BlockHypothesis h) {
        return h.score + 0.20 * h.phaseCoverage + 0.20 * h.phaseEntropyNorm - 0.25 * h.phasePeakFraction;
    }

    private static void writeSummary(List<BlockHypothesis> best, BlockHypothesis chosen, Path out) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Heuristic extractor v2 summary\n");
            w.write("This is NOT a confirmed parser. These are candidate local blocks.\n\n");
            w.write("Chosen block:\n");
            w.write(String.format(Locale.US,
                    "offset=0x%X\ntemplate=%s\nscore=%.6f\nrecords=%d\nphaseFraction=%.6f\nfiniteValueFraction=%.6f\nnonZeroValueFraction=%.6f\nmonotonicTimeFraction=%.6f\npositiveTimeDeltaFraction=%.6f\nphaseEntropyNorm=%.6f\nphaseCoverage=%.6f\nphasePeakFraction=%.6f\nvalueDynamicFraction=%.6f\nchannelCardinality=%d\nabsValueP50=%.9g\nabsValueP95=%.9g\nfirstTime=%s\nlastTime=%s\n\n",
                    chosen.baseOffset, chosen.template.name, chosen.score, chosen.recordsScored,
                    chosen.phaseFraction, chosen.finiteValueFraction, chosen.nonZeroValueFraction,
                    chosen.monotonicTimeFraction, chosen.positiveTimeDeltaFraction,
                    chosen.phaseEntropyNorm, chosen.phaseCoverage, chosen.phasePeakFraction,
                    chosen.valueDynamicFraction, chosen.channelCardinality,
                    chosen.valueAbsP50, chosen.valueAbsP95,
                    Long.toUnsignedString(chosen.firstTime), Long.toUnsignedString(chosen.lastTime)));

            w.write("Top blocks:\n");
            for (int i = 0; i < Math.min(20, best.size()); i++) {
                BlockHypothesis h = best.get(i);
                w.write(String.format(Locale.US,
                        "%2d. score=%.6f off=0x%X tpl=%s recs=%d phase=%.4f finite=%.4f nz=%.4f mono=%.4f dt+=%.4f ent=%.4f cov=%.4f peak=%.4f dyn=%.4f chan=%d\n",
                        i + 1, h.score, h.baseOffset, h.template.name, h.recordsScored,
                        h.phaseFraction, h.finiteValueFraction, h.nonZeroValueFraction,
                        h.monotonicTimeFraction, h.positiveTimeDeltaFraction, h.phaseEntropyNorm,
                        h.phaseCoverage, h.phasePeakFraction, h.valueDynamicFraction,
                        h.channelCardinality));
            }
        }
    }

    private static void dumpCsvFiltered(byte[] data, BlockHypothesis h, Path out, int limit) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            Field time = h.template.get("time");
            Field phase = h.template.get("phase");
            Field value = h.template.get("value");
            Field chan = h.template.get("chan");

            w.write("index,offset,time,phase,value");
            if (chan != null) w.write(",channel");
            w.write(",record_ok");
            w.newLine();

            int available = data.length - h.baseOffset;
            int records = Math.min(limit, available / h.template.recordLength);
            for (int i = 0; i < records; i++) {
                int off = h.baseOffset + i * h.template.recordLength;
                float phaseValue = readF32(data, off + phase.offset);
                float valueValue = readF32(data, off + value.offset);
                long timeValue = 0L;
                if (time != null) {
                    timeValue = time.type == FieldType.U64
                            ? readU64(data, off + time.offset)
                            : Integer.toUnsignedLong(readU32(data, off + time.offset));
                }
                boolean ok = isPlausibleRecord(phaseValue, valueValue, h.valueAbsP95);
                StringBuilder sb = new StringBuilder();
                sb.append(i).append(',').append(off).append(',');
                if (time != null) sb.append(Long.toUnsignedString(timeValue));
                sb.append(',').append(formatFloat(phaseValue)).append(',').append(formatFloat(valueValue));
                if (chan != null) sb.append(',').append(Integer.toUnsignedString(readU32(data, off + chan.offset)));
                sb.append(',').append(ok ? 1 : 0);
                w.write(sb.toString());
                w.newLine();
            }
        }
    }

    private static boolean isPlausibleRecord(float phase, float value, float absP95) {
        if (!Float.isFinite(phase) || phase < 0.0f || phase > 360.0f) return false;
        if (!Float.isFinite(value)) return false;
        float av = Math.abs(value);
        if (av < 1.0e-12f) return false;
        if (av > 1.0e12f) return false;
        float threshold = Math.max(absP95 * 20.0f, 1.0e-6f);
        return av <= threshold;
    }

    private static final class HistogramStats {
        final double entropyNorm;
        final double coverage;
        final double peakFraction;
        HistogramStats(double entropyNorm, double coverage, double peakFraction) {
            this.entropyNorm = entropyNorm;
            this.coverage = coverage;
            this.peakFraction = peakFraction;
        }
    }

    private static HistogramStats histogramStats(int[] bins, int total) {
        if (total <= 0) return new HistogramStats(0.0, 0.0, 1.0);
        int nonEmpty = 0;
        int max = 0;
        double entropy = 0.0;
        for (int c : bins) {
            if (c > 0) {
                nonEmpty++;
                max = Math.max(max, c);
                double p = c / (double) total;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        double entropyNorm = entropy / (Math.log(bins.length) / Math.log(2.0));
        double coverage = nonEmpty / (double) bins.length;
        double peakFraction = max / (double) total;
        return new HistogramStats(entropyNorm, coverage, peakFraction);
    }

    private static float percentileSorted(float[] sorted, int n, double q) {
        if (n <= 0) return Float.NaN;
        if (n == 1) return sorted[0];
        double pos = q * (n - 1);
        int i = (int) Math.floor(pos);
        int j = Math.min(n - 1, i + 1);
        double frac = pos - i;
        return (float) (sorted[i] * (1.0 - frac) + sorted[j] * frac);
    }

    private static int readU32(byte[] data, int off) {
        return ByteBuffer.wrap(data, off, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static long readU64(byte[] data, int off) {
        return ByteBuffer.wrap(data, off, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static float readF32(byte[] data, int off) {
        return ByteBuffer.wrap(data, off, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static String formatFloat(float f) {
        if (Float.isNaN(f)) return "NaN";
        if (Float.isInfinite(f)) return f > 0 ? "+Inf" : "-Inf";
        return String.format(Locale.US, "%.9g", f);
    }
}
