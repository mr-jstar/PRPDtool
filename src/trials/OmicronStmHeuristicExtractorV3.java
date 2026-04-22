import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Heuristic extractor v3 for OMICRON .stm files.
 *
 * This is still NOT a confirmed format parser.
 *
 * New in v3 compared with v2:
 *  - scans smaller windows and keeps many local hypotheses,
 *  - groups adjacent high-quality windows into longer candidate segments,
 *  - estimates segment boundaries rather than only one best local block,
 *  - exports one CSV for the best segment and a summary of all top segments,
 *  - prints segment-level stability metrics to help identify PRPD-like payload areas.
 *
 * Usage:
 *   java OmicronStmHeuristicExtractorV3 file1.stm [file2.stm ...]
 */
public class OmicronStmHeuristicExtractorV3 {

    private static final String MAGIC = "mtronix Stream File";
    private static final long DEFAULT_SCAN_START = 8192;
    private static final long MAX_SCAN_BYTES = 16L * 1024 * 1024;
    private static final int LOCAL_WINDOW_BYTES = 64 * 1024;
    private static final int WINDOW_STRIDE = 4096;
    private static final int MAX_BASE_JITTER = 64;
    private static final int MAX_RECORDS_PER_WINDOW = 6000;
    private static final int MAX_TOP_WINDOWS = 300;
    private static final int MAX_TOP_SEGMENTS = 24;
    private static final int DEFAULT_DUMP_RECORDS = 100000;
    private static final double WINDOW_MIN_SCORE = 1.45;
    private static final double WINDOW_KEEP_SCORE = 1.65;
    private static final int SEGMENT_GAP_BYTES = 24 * 1024;

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
        Template(String name, int recordLength, Field... fields) {
            this.name = name;
            this.recordLength = recordLength;
            this.fields = fields;
        }
        Field get(String name) {
            for (Field f : fields) if (f.name.equals(name)) return f;
            return null;
        }
    }

    static final class WindowHypothesis implements Comparable<WindowHypothesis> {
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

        WindowHypothesis(Template template, int baseOffset, int windowBytes, int recordsScored,
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

        int endOffset() {
            return baseOffset + windowBytes;
        }

        @Override public int compareTo(WindowHypothesis other) {
            return Double.compare(other.score, this.score);
        }
    }

    static final class SegmentHypothesis implements Comparable<SegmentHypothesis> {
        final Template template;
        final int startOffset;
        final int endOffset;
        final List<WindowHypothesis> windows;
        final double score;
        final double meanWindowScore;
        final double medianWindowScore;
        final double minWindowScore;
        final double maxWindowScore;
        final int estimatedRecords;
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

        SegmentHypothesis(Template template, int startOffset, int endOffset,
                          List<WindowHypothesis> windows, double score,
                          double meanWindowScore, double medianWindowScore,
                          double minWindowScore, double maxWindowScore,
                          int estimatedRecords,
                          double phaseFraction, double finiteValueFraction,
                          double nonZeroValueFraction, double monotonicTimeFraction,
                          double positiveTimeDeltaFraction, double phaseEntropyNorm,
                          double phaseCoverage, double phasePeakFraction,
                          double valueDynamicFraction, int channelCardinality,
                          float valueAbsP50, float valueAbsP95,
                          long firstTime, long lastTime) {
            this.template = template;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.windows = windows;
            this.score = score;
            this.meanWindowScore = meanWindowScore;
            this.medianWindowScore = medianWindowScore;
            this.minWindowScore = minWindowScore;
            this.maxWindowScore = maxWindowScore;
            this.estimatedRecords = estimatedRecords;
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

        int byteLength() {
            return endOffset - startOffset;
        }

        @Override public int compareTo(SegmentHypothesis other) {
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
            System.err.println("Usage: java OmicronStmHeuristicExtractorV3 file1.stm [file2.stm ...]");
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

        List<WindowHypothesis> windows = scanCandidateWindows(data);
        System.out.println("Candidate windows kept: " + windows.size());
        List<SegmentHypothesis> segments = buildSegments(data, windows);
        if (segments.isEmpty()) {
            System.out.println("No plausible segments found.");
            return;
        }

        System.out.println("\nTop candidate segments:");
        for (int i = 0; i < Math.min(10, segments.size()); i++) {
            SegmentHypothesis s = segments.get(i);
            System.out.printf(Locale.US,
                    "#%d score=%.4f range=0x%X..0x%X bytes=%d tpl=%s windows=%d estRecs=%d mean=%.3f med=%.3f phase=%.3f finite=%.3f nz=%.3f mono=%.3f dt+=%.3f ent=%.3f cov=%.3f peak=%.3f dyn=%.3f chan=%d%n",
                    i + 1, s.score, s.startOffset, s.endOffset, s.byteLength(), s.template.name,
                    s.windows.size(), s.estimatedRecords, s.meanWindowScore, s.medianWindowScore,
                    s.phaseFraction, s.finiteValueFraction, s.nonZeroValueFraction,
                    s.monotonicTimeFraction, s.positiveTimeDeltaFraction, s.phaseEntropyNorm,
                    s.phaseCoverage, s.phasePeakFraction, s.valueDynamicFraction, s.channelCardinality);
        }

        SegmentHypothesis chosen = chooseRepresentativeSegment(segments);
        System.out.println("\nChosen segment for CSV export:");
        System.out.printf(Locale.US,
                "  range=0x%X..0x%X template=%s score=%.4f estimatedRecords=%d windows=%d%n",
                chosen.startOffset, chosen.endOffset, chosen.template.name, chosen.score,
                chosen.estimatedRecords, chosen.windows.size());

        String baseName = path.getFileName().toString();
        Path summary = path.resolveSibling(baseName + ".heuristic.v3.summary.txt");
        Path csv = path.resolveSibling(baseName + ".heuristic.v3.best_segment.csv");
        writeSummary(segments, chosen, summary);
        dumpSegmentCsv(data, chosen, csv, DEFAULT_DUMP_RECORDS);

        System.out.println("Wrote summary: " + summary);
        System.out.println("Wrote CSV:     " + csv);
    }

    private static void checkMagic(byte[] data) {
        String prefix = new String(data, 0, Math.min(MAGIC.length(), data.length), StandardCharsets.US_ASCII);
        System.out.println("Magic ok: " + MAGIC.equals(prefix) + " [" + prefix + "]");
    }

    private static List<WindowHypothesis> scanCandidateWindows(byte[] data) {
        long scanEndLong = Math.min(data.length, DEFAULT_SCAN_START + MAX_SCAN_BYTES);
        int scanEnd = (int) scanEndLong;
        PriorityQueue<WindowHypothesis> pq = new PriorityQueue<>(MAX_TOP_WINDOWS, Comparator.reverseOrder());

        for (Template t : TEMPLATES) {
            int jitterLimit = Math.min(MAX_BASE_JITTER, t.recordLength);
            for (int windowBase = (int) DEFAULT_SCAN_START;
                 windowBase + LOCAL_WINDOW_BYTES + t.recordLength < scanEnd;
                 windowBase += WINDOW_STRIDE) {
                for (int jitter = 0; jitter < jitterLimit; jitter++) {
                    int base = windowBase + jitter;
                    WindowHypothesis h = scoreWindow(data, base, LOCAL_WINDOW_BYTES, t);
                    if (h == null) continue;
                    if (h.score < WINDOW_KEEP_SCORE) continue;
                    offerDistinctWindow(pq, h);
                }
            }
        }

        List<WindowHypothesis> out = new ArrayList<>(pq);
        Collections.sort(out);
        return out;
    }

    private static void offerDistinctWindow(PriorityQueue<WindowHypothesis> pq, WindowHypothesis h) {
        for (WindowHypothesis existing : pq) {
            if (existing.template == h.template && Math.abs(existing.baseOffset - h.baseOffset) < WINDOW_STRIDE) {
                if (h.score > existing.score) {
                    pq.remove(existing);
                    pq.offer(h);
                }
                return;
            }
        }
        pq.offer(h);
        while (pq.size() > MAX_TOP_WINDOWS) pq.poll();
    }

    private static WindowHypothesis scoreWindow(byte[] data, int base, int windowBytes, Template t) {
        if (base + t.recordLength >= data.length) return null;
        int available = Math.min(windowBytes, data.length - base);
        int records = Math.min(MAX_RECORDS_PER_WINDOW, available / t.recordLength);
        if (records < 800) return null;

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
        if (score < WINDOW_MIN_SCORE) return null;

        return new WindowHypothesis(t, base, windowBytes, records, score, phaseFraction,
                finiteValueFraction, nonZeroValueFraction, monotonicTimeFraction,
                positiveTimeDeltaFraction, hs.entropyNorm, hs.coverage, hs.peakFraction,
                valueDynamicFraction, channelCardinality, absP50, absP95, firstTime, lastTime);
    }

    private static List<SegmentHypothesis> buildSegments(byte[] data, List<WindowHypothesis> windows) {
        Map<Template, List<WindowHypothesis>> byTemplate = new LinkedHashMap<>();
        for (WindowHypothesis w : windows) {
            byTemplate.computeIfAbsent(w.template, k -> new ArrayList<>()).add(w);
        }

        List<SegmentHypothesis> segments = new ArrayList<>();
        for (Map.Entry<Template, List<WindowHypothesis>> e : byTemplate.entrySet()) {
            List<WindowHypothesis> list = e.getValue();
            list.sort(Comparator.comparingInt(w -> w.baseOffset));

            List<WindowHypothesis> current = new ArrayList<>();
            int currentEnd = -1;
            for (WindowHypothesis w : list) {
                if (current.isEmpty()) {
                    current.add(w);
                    currentEnd = w.endOffset();
                    continue;
                }
                if (w.baseOffset <= currentEnd + SEGMENT_GAP_BYTES) {
                    current.add(w);
                    currentEnd = Math.max(currentEnd, w.endOffset());
                } else {
                    SegmentHypothesis s = finalizeSegment(data, current);
                    if (s != null) segments.add(s);
                    current = new ArrayList<>();
                    current.add(w);
                    currentEnd = w.endOffset();
                }
            }
            SegmentHypothesis s = finalizeSegment(data, current);
            if (s != null) segments.add(s);
        }

        Collections.sort(segments);
        if (segments.size() > MAX_TOP_SEGMENTS) {
            return new ArrayList<>(segments.subList(0, MAX_TOP_SEGMENTS));
        }
        return segments;
    }

    private static SegmentHypothesis finalizeSegment(byte[] data, List<WindowHypothesis> windows) {
        if (windows == null || windows.isEmpty()) return null;
        Template t = windows.get(0).template;
        if (windows.size() < 2) return null;

        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        double sumScore = 0.0;
        double minScore = Double.POSITIVE_INFINITY;
        double maxScore = Double.NEGATIVE_INFINITY;
        List<Double> scores = new ArrayList<>(windows.size());
        for (WindowHypothesis w : windows) {
            start = Math.min(start, w.baseOffset);
            end = Math.max(end, w.endOffset());
            sumScore += w.score;
            minScore = Math.min(minScore, w.score);
            maxScore = Math.max(maxScore, w.score);
            scores.add(w.score);
        }
        Collections.sort(scores);
        double mean = sumScore / windows.size();
        double median = scores.get(scores.size() / 2);
        int approxRecords = Math.max(0, (end - start) / t.recordLength);
        if (approxRecords < 2000) return null;

        WindowHypothesis agg = scoreWindow(data, start, end - start, t);
        if (agg == null) return null;

        double score = 0.0;
        score += 0.85 * agg.score;
        score += 0.45 * mean;
        score += 0.20 * median;
        score += 0.18 * Math.min(1.0, windows.size() / 6.0);
        score += 0.12 * Math.min(1.0, approxRecords / 20000.0);
        score -= 0.10 * Math.max(0.0, 0.25 - agg.phaseCoverage);
        score -= 0.12 * Math.max(0.0, agg.phasePeakFraction - 0.45);

        return new SegmentHypothesis(t, start, end, new ArrayList<>(windows), score,
                mean, median, minScore, maxScore, approxRecords,
                agg.phaseFraction, agg.finiteValueFraction, agg.nonZeroValueFraction,
                agg.monotonicTimeFraction, agg.positiveTimeDeltaFraction,
                agg.phaseEntropyNorm, agg.phaseCoverage, agg.phasePeakFraction,
                agg.valueDynamicFraction, agg.channelCardinality,
                agg.valueAbsP50, agg.valueAbsP95, agg.firstTime, agg.lastTime);
    }

    private static SegmentHypothesis chooseRepresentativeSegment(List<SegmentHypothesis> segments) {
        SegmentHypothesis best = segments.get(0);
        double bestScore = representativeScore(best);
        for (int i = 1; i < Math.min(8, segments.size()); i++) {
            SegmentHypothesis s = segments.get(i);
            double sc = representativeScore(s);
            if (sc > bestScore) {
                best = s;
                bestScore = sc;
            }
        }
        return best;
    }

    private static double representativeScore(SegmentHypothesis s) {
        return s.score
                + 0.20 * s.phaseCoverage
                + 0.20 * s.phaseEntropyNorm
                + 0.10 * Math.min(1.0, s.windows.size() / 8.0)
                - 0.20 * s.phasePeakFraction;
    }

    private static void writeSummary(List<SegmentHypothesis> segments, SegmentHypothesis chosen, Path out) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Heuristic extractor v3 summary\n");
            w.write("This is NOT a confirmed parser. These are candidate segments built from nearby high-scoring windows.\n\n");
            w.write("Chosen segment:\n");
            writeSegmentDetails(w, chosen);
            w.write("\nTop segments:\n");
            for (int i = 0; i < Math.min(20, segments.size()); i++) {
                w.write("\nSegment #" + (i + 1) + "\n");
                writeSegmentDetails(w, segments.get(i));
            }
        }
    }

    private static void writeSegmentDetails(BufferedWriter w, SegmentHypothesis s) throws IOException {
        w.write(String.format(Locale.US,
                "range=0x%X..0x%X\nbytes=%d\ntemplate=%s\nscore=%.6f\nwindows=%d\nmeanWindowScore=%.6f\nmedianWindowScore=%.6f\nminWindowScore=%.6f\nmaxWindowScore=%.6f\nestimatedRecords=%d\nphaseFraction=%.6f\nfiniteValueFraction=%.6f\nnonZeroValueFraction=%.6f\nmonotonicTimeFraction=%.6f\npositiveTimeDeltaFraction=%.6f\nphaseEntropyNorm=%.6f\nphaseCoverage=%.6f\nphasePeakFraction=%.6f\nvalueDynamicFraction=%.6f\nchannelCardinality=%d\nabsValueP50=%.9g\nabsValueP95=%.9g\nfirstTime=%s\nlastTime=%s\n",
                s.startOffset, s.endOffset, s.byteLength(), s.template.name, s.score,
                s.windows.size(), s.meanWindowScore, s.medianWindowScore,
                s.minWindowScore, s.maxWindowScore, s.estimatedRecords,
                s.phaseFraction, s.finiteValueFraction, s.nonZeroValueFraction,
                s.monotonicTimeFraction, s.positiveTimeDeltaFraction,
                s.phaseEntropyNorm, s.phaseCoverage, s.phasePeakFraction,
                s.valueDynamicFraction, s.channelCardinality,
                s.valueAbsP50, s.valueAbsP95,
                Long.toUnsignedString(s.firstTime), Long.toUnsignedString(s.lastTime)));
    }

    private static void dumpSegmentCsv(byte[] data, SegmentHypothesis s, Path out, int limit) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            Field time = s.template.get("time");
            Field phase = s.template.get("phase");
            Field value = s.template.get("value");
            Field chan = s.template.get("chan");

            w.write("index,offset,time,phase,value");
            if (chan != null) w.write(",channel");
            w.write(",record_ok");
            w.newLine();

            int available = Math.min(data.length, s.endOffset) - s.startOffset;
            int records = Math.min(limit, available / s.template.recordLength);
            for (int i = 0; i < records; i++) {
                int off = s.startOffset + i * s.template.recordLength;
                float phaseValue = readF32(data, off + phase.offset);
                float valueValue = readF32(data, off + value.offset);
                long timeValue = 0L;
                if (time != null) {
                    timeValue = time.type == FieldType.U64
                            ? readU64(data, off + time.offset)
                            : Integer.toUnsignedLong(readU32(data, off + time.offset));
                }
                boolean ok = isPlausibleRecord(phaseValue, valueValue, s.valueAbsP95);
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
