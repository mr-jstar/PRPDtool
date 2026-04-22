import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Best-effort heuristic extractor for OMICRON .stm files.
 *
 * This is NOT a confirmed format parser.
 * It does three things:
 *  1) extracts obvious metadata (ASCII/UTF-16LE strings, creator string, base64 blobs),
 *  2) scores a few candidate fixed-length record layouts,
 *  3) writes the best-scoring candidate records to CSV.
 *
 * Usage:
 *   java OmicronStmHeuristicExtractor file1.stm [file2.stm ...]
 */
public class OmicronStmHeuristicExtractor {

    private static final String MAGIC = "mtronix Stream File";
    private static final long DEFAULT_SCAN_START = 8192;
    private static final int DEFAULT_SAMPLE_BYTES = 8 * 1024 * 1024;
    private static final int DEFAULT_DUMP_RECORDS = 20000;

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

    static final class Hypothesis implements Comparable<Hypothesis> {
        final Template template;
        final int baseOffset;
        final double score;
        final double phaseFraction;
        final double finiteValueFraction;
        final double nonZeroValueFraction;
        final double monotonicTimeFraction;
        final int channelCardinality;
        final int recordsScored;

        Hypothesis(Template template, int baseOffset, double score, double phaseFraction,
                   double finiteValueFraction, double nonZeroValueFraction,
                   double monotonicTimeFraction, int channelCardinality, int recordsScored) {
            this.template = template;
            this.baseOffset = baseOffset;
            this.score = score;
            this.phaseFraction = phaseFraction;
            this.finiteValueFraction = finiteValueFraction;
            this.nonZeroValueFraction = nonZeroValueFraction;
            this.monotonicTimeFraction = monotonicTimeFraction;
            this.channelCardinality = channelCardinality;
            this.recordsScored = recordsScored;
        }

        @Override public int compareTo(Hypothesis other) {
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
            System.err.println("Usage: java OmicronStmHeuristicExtractor file1.stm [file2.stm ...]");
            return;
        }

        for (String arg : args) {
            Path path = Paths.get(arg);
            analyze(path);
        }
    }

    private static void analyze(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        System.out.println("============================================================");
        System.out.println("File: " + path.getFileName());
        System.out.println("Size: " + data.length + " bytes");
        System.out.println();

        checkMagic(data);
        dumpMetadata(data);

        List<Hypothesis> hyps = findBestHypotheses(data, (int) Math.min(Integer.MAX_VALUE, data.length));
        System.out.println();
        System.out.println("Top heuristic layouts:");
        for (int i = 0; i < Math.min(8, hyps.size()); i++) {
            Hypothesis h = hyps.get(i);
            System.out.printf(Locale.US,
                    "  #%d score=%.4f base=%d template=%s records=%d phase=%.3f finiteValue=%.3f nonZeroValue=%.3f monotonicTime=%.3f channelCard=%d%n",
                    i + 1, h.score, h.baseOffset, h.template.name, h.recordsScored,
                    h.phaseFraction, h.finiteValueFraction, h.nonZeroValueFraction,
                    h.monotonicTimeFraction, h.channelCardinality);
        }

        if (!hyps.isEmpty()) {
            Hypothesis best = hyps.get(0);
            Path out = path.resolveSibling(path.getFileName().toString() + ".heuristic.csv");
            dumpCsv(data, best, out, DEFAULT_DUMP_RECORDS);
            System.out.println();
            System.out.println("Wrote candidate records to: " + out);
        }
        System.out.println();
    }

    private static void checkMagic(byte[] data) {
        String prefix = new String(data, 0, Math.min(MAGIC.length(), data.length), StandardCharsets.US_ASCII);
        System.out.println("Magic ok: " + MAGIC.equals(prefix) + " [" + prefix + "]");
    }

    private static void dumpMetadata(byte[] data) {
        System.out.println("\nMetadata / strings:");
        String creator = extractUtf16(data, 0x1F, 180);
        if (creator != null && !creator.isBlank()) {
            System.out.println("  Creator: " + creator.replace('\0', ' ').trim());
        }

        List<String> utf16 = extractUtf16Strings(data, 4, 80);
        for (int i = 0; i < Math.min(12, utf16.size()); i++) {
            System.out.println("  UTF16:   " + utf16.get(i));
        }

        List<String> ascii = extractAsciiStrings(data, 6, 120);
        for (int i = 0; i < Math.min(8, ascii.size()); i++) {
            System.out.println("  ASCII:   " + ascii.get(i));
        }

        byte[] decoded = findAndDecodeBase64Utf16(data);
        if (decoded != null) {
            String s = new String(decoded, StandardCharsets.UTF_8).replace("\n", " ").trim();
            System.out.println("  Base64->UTF8: " + (s.length() > 160 ? s.substring(0, 160) + "..." : s));
        }

        int trailingZeros = trailingZeroCount(data);
        System.out.println("  Trailing zero padding: " + trailingZeros + " bytes");
    }

    private static List<Hypothesis> findBestHypotheses(byte[] data, int maxBytes) {
        int scanStart = (int) Math.min(DEFAULT_SCAN_START, data.length);
        int scanEnd = (int) Math.min((long) scanStart + DEFAULT_SAMPLE_BYTES, data.length);
        List<Hypothesis> out = new ArrayList<>();

        for (int base = scanStart; base < Math.min(scanStart + 256, scanEnd); base++) {
            for (Template t : TEMPLATES) {
                Hypothesis h = score(data, base, scanEnd, t);
                if (h != null) out.add(h);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static Hypothesis score(byte[] data, int base, int scanEnd, Template t) {
        if (base >= scanEnd) return null;
        int available = scanEnd - base;
        int records = available / t.recordLength;
        records = Math.min(records, 200_000);
        if (records < 5_000) return null;

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
        long prevTime = Long.MIN_VALUE;
        Set<Long> channelSet = new HashSet<>();

        for (int i = 0; i < records; i++) {
            int off = base + i * t.recordLength;
            float phase = readF32(data, off + phaseField.offset);
            float value = readF32(data, off + valueField.offset);
            if (phase >= 0.0f && phase <= 360.0f) phaseGood++;
            if (Float.isFinite(value) && Math.abs(value) < 1.0e9f) valueFinite++;
            if (Float.isFinite(value) && Math.abs(value) > 1.0e-12f) valueNonZero++;

            if (timeField != null) {
                long time = timeField.type == FieldType.U64
                        ? readU64(data, off + timeField.offset)
                        : Integer.toUnsignedLong(readU32(data, off + timeField.offset));
                if (prevTime != Long.MIN_VALUE) {
                    if (Long.compareUnsigned(time, prevTime) >= 0) monoGood++;
                    monoTotal++;
                }
                prevTime = time;
            }

            if (chanField != null && channelSet.size() <= 1024) {
                long ch = Integer.toUnsignedLong(readU32(data, off + chanField.offset));
                channelSet.add(ch);
            }
        }

        double phaseFraction = phaseGood / (double) records;
        double finiteValueFraction = valueFinite / (double) records;
        double nonZeroValueFraction = valueNonZero / (double) records;
        double monotonicTimeFraction = monoTotal == 0 ? 0.0 : monoGood / (double) monoTotal;
        int channelCard = chanField == null ? -1 : channelSet.size();

        double score = phaseFraction
                + 0.50 * finiteValueFraction
                + 0.20 * nonZeroValueFraction
                + 0.40 * monotonicTimeFraction;

        if (chanField != null && channelCard > 0 && channelCard <= 32) {
            score += 0.20;
        }

        if (score < 1.10) return null;
        return new Hypothesis(t, base, score, phaseFraction, finiteValueFraction,
                nonZeroValueFraction, monotonicTimeFraction, channelCard, records);
    }

    private static void dumpCsv(byte[] data, Hypothesis h, Path out, int limit) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            Field time = h.template.get("time");
            Field phase = h.template.get("phase");
            Field value = h.template.get("value");
            Field chan = h.template.get("chan");

            w.write("index,offset,time,phase,value");
            if (chan != null) w.write(",channel");
            w.newLine();

            int available = data.length - h.baseOffset;
            int records = Math.min(limit, available / h.template.recordLength);
            for (int i = 0; i < records; i++) {
                int off = h.baseOffset + i * h.template.recordLength;
                StringBuilder sb = new StringBuilder();
                sb.append(i).append(',').append(off).append(',');
                if (time != null) {
                    if (time.type == FieldType.U64) {
                        sb.append(Long.toUnsignedString(readU64(data, off + time.offset)));
                    } else {
                        sb.append(Integer.toUnsignedString(readU32(data, off + time.offset)));
                    }
                }
                sb.append(',');
                sb.append(formatFloat(readF32(data, off + phase.offset))).append(',');
                sb.append(formatFloat(readF32(data, off + value.offset)));
                if (chan != null) {
                    sb.append(',').append(Integer.toUnsignedString(readU32(data, off + chan.offset)));
                }
                w.write(sb.toString());
                w.newLine();
            }
        }
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

    private static int trailingZeroCount(byte[] data) {
        int c = 0;
        for (int i = data.length - 1; i >= 0 && data[i] == 0; i--) c++;
        return c;
    }

    private static String extractUtf16(byte[] data, int offset, int maxChars) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = offset; i + 1 < data.length && out.size() / 2 < maxChars; i += 2) {
            byte b0 = data[i];
            byte b1 = data[i + 1];
            if (b0 == 0 && b1 == 0) break;
            out.write(b0);
            out.write(b1);
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length == 0) return null;
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    private static List<String> extractUtf16Strings(byte[] data, int minChars, int limit) {
        List<String> out = new ArrayList<>();
        for (int parity = 0; parity < 2; parity++) {
            int i = parity;
            while (i + 1 < data.length && out.size() < limit) {
                int start = i;
                StringBuilder sb = new StringBuilder();
                while (i + 1 < data.length) {
                    int lo = data[i] & 0xFF;
                    int hi = data[i + 1] & 0xFF;
                    if (hi == 0 && lo >= 32 && lo < 127) {
                        sb.append((char) lo);
                        i += 2;
                    } else if (lo == 0 && hi == 0) {
                        i += 2;
                        break;
                    } else {
                        if (sb.length() == 0) i += 2;
                        break;
                    }
                }
                if (sb.length() >= minChars) {
                    out.add(String.format("0x%X: %s", start, sb));
                }
            }
        }
        return out;
    }

    private static List<String> extractAsciiStrings(byte[] data, int minLen, int limit) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < data.length && out.size() < limit) {
            int start = i;
            while (i < data.length && data[i] >= 32 && data[i] < 127) i++;
            int len = i - start;
            if (len >= minLen) {
                out.add(String.format("0x%X: %s", start,
                        new String(data, start, len, StandardCharsets.US_ASCII)));
            }
            i++;
        }
        return out;
    }

    private static byte[] findAndDecodeBase64Utf16(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < data.length; i += 2) {
            int lo = data[i] & 0xFF;
            int hi = data[i + 1] & 0xFF;
            if (hi == 0 && ((lo >= 'A' && lo <= 'Z') || (lo >= 'a' && lo <= 'z') || (lo >= '0' && lo <= '9') || lo == '+' || lo == '/' || lo == '=')) {
                sb.append((char) lo);
            } else {
                if (sb.length() > 64) break;
                sb.setLength(0);
            }
        }
        if (sb.length() < 64) return null;
        try {
            return Base64.getDecoder().decode(sb.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
