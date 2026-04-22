import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Heuristic extractor v4 for OMICRON .stm files.
 *
 * Still NOT a confirmed format parser.
 *
 * New in v4:
 *  - builds a coarse section map of the container,
 *  - extracts ASCII / UTF-16LE string evidence,
 *  - detects trailing zero padding,
 *  - proposes candidate length-prefixed chunk headers,
 *  - combines section analysis with the v3 PRPD-like segment search,
 *  - writes a richer semi-formal reverse-engineering report.
 *
 * Usage:
 *   java OmicronStmHeuristicExtractorV4 file1.stm [file2.stm ...]
 */
public class OmicronStmHeuristicExtractorV4 {

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

    private static final int SECTION_WINDOW = 4096;
    private static final int SECTION_STRIDE = 2048;
    private static final long MAX_SECTION_SCAN = 8L * 1024 * 1024;
    private static final int MAX_TOP_CHUNK_HEADERS = 40;
    private static final int MAX_STRINGS_PER_KIND = 40;

    enum FieldType { U32, U64, F32 }
    enum SectionKind { HEADER_TEXT, TEXT_LIKE, MIXED, BINARY, PRPD_LIKE, ZERO_PADDING }

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

        int endOffset() { return baseOffset + windowBytes; }
        @Override public int compareTo(WindowHypothesis other) { return Double.compare(other.score, this.score); }
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

        int byteLength() { return endOffset - startOffset; }
        @Override public int compareTo(SegmentHypothesis other) { return Double.compare(other.score, this.score); }
    }

    static final class HistogramStats {
        final double entropyNorm;
        final double coverage;
        final double peakFraction;
        HistogramStats(double entropyNorm, double coverage, double peakFraction) {
            this.entropyNorm = entropyNorm;
            this.coverage = coverage;
            this.peakFraction = peakFraction;
        }
    }

    static final class SectionWindow {
        final int offset;
        final int length;
        final double asciiRatio;
        final double utf16Ratio;
        final double zeroRatio;
        final double printRatio;
        final double entropy;
        final double bestPrpdScore;
        final SectionKind kind;
        final String bestTemplate;

        SectionWindow(int offset, int length, double asciiRatio, double utf16Ratio, double zeroRatio,
                      double printRatio, double entropy, double bestPrpdScore, SectionKind kind, String bestTemplate) {
            this.offset = offset;
            this.length = length;
            this.asciiRatio = asciiRatio;
            this.utf16Ratio = utf16Ratio;
            this.zeroRatio = zeroRatio;
            this.printRatio = printRatio;
            this.entropy = entropy;
            this.bestPrpdScore = bestPrpdScore;
            this.kind = kind;
            this.bestTemplate = bestTemplate;
        }
    }

    static final class Section implements Comparable<Section> {
        final int start;
        final int end;
        final SectionKind kind;
        final int windows;
        final double meanAsciiRatio;
        final double meanUtf16Ratio;
        final double meanZeroRatio;
        final double meanEntropy;
        final double meanBestPrpdScore;
        final String dominantTemplate;

        Section(int start, int end, SectionKind kind, int windows,
                double meanAsciiRatio, double meanUtf16Ratio, double meanZeroRatio,
                double meanEntropy, double meanBestPrpdScore, String dominantTemplate) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.windows = windows;
            this.meanAsciiRatio = meanAsciiRatio;
            this.meanUtf16Ratio = meanUtf16Ratio;
            this.meanZeroRatio = meanZeroRatio;
            this.meanEntropy = meanEntropy;
            this.meanBestPrpdScore = meanBestPrpdScore;
            this.dominantTemplate = dominantTemplate;
        }
        int length() { return end - start; }
        @Override public int compareTo(Section o) { return Integer.compare(this.start, o.start); }
    }

    static final class StringHit implements Comparable<StringHit> {
        final int offset;
        final String kind;
        final String text;
        StringHit(int offset, String kind, String text) {
            this.offset = offset;
            this.kind = kind;
            this.text = text;
        }
        @Override public int compareTo(StringHit o) { return Integer.compare(this.offset, o.offset); }
    }

    static final class ChunkHeaderCandidate implements Comparable<ChunkHeaderCandidate> {
        final int offset;
        final int lengthFieldSize;
        final long claimedLength;
        final int payloadStart;
        final int payloadEnd;
        final double score;
        final String reason;

        ChunkHeaderCandidate(int offset, int lengthFieldSize, long claimedLength,
                             int payloadStart, int payloadEnd, double score, String reason) {
            this.offset = offset;
            this.lengthFieldSize = lengthFieldSize;
            this.claimedLength = claimedLength;
            this.payloadStart = payloadStart;
            this.payloadEnd = payloadEnd;
            this.score = score;
            this.reason = reason;
        }
        @Override public int compareTo(ChunkHeaderCandidate o) { return Double.compare(o.score, this.score); }
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
            System.err.println("Usage: java OmicronStmHeuristicExtractorV4 file1.stm [file2.stm ...]");
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

        List<StringHit> strings = extractInterestingStrings(data);
        int trailingZeros = trailingZeroPadding(data);
        List<Section> sections = buildSectionMap(data);
        List<ChunkHeaderCandidate> chunkHeaders = findChunkHeaderCandidates(data, sections);
        List<WindowHypothesis> windows = scanCandidateWindows(data);
        List<SegmentHypothesis> segments = buildSegments(data, windows);

        System.out.println("Interesting strings: " + strings.size());
        System.out.println("Trailing zero padding: " + trailingZeros + " bytes");
        System.out.println("Sections: " + sections.size());
        System.out.println("Candidate chunk headers: " + chunkHeaders.size());
        System.out.println("Candidate windows kept: " + windows.size());
        System.out.println("Candidate segments: " + segments.size());

        SegmentHypothesis chosen = segments.isEmpty() ? null : chooseRepresentativeSegment(segments);
        if (chosen != null) {
            System.out.printf(Locale.US,
                "Chosen segment: 0x%X..0x%X tpl=%s score=%.4f estRecs=%d windows=%d%n",
                chosen.startOffset, chosen.endOffset, chosen.template.name, chosen.score,
                chosen.estimatedRecords, chosen.windows.size());
        }

        String baseName = path.getFileName().toString();
        Path report = path.resolveSibling(baseName + ".heuristic.v4.report.txt");
        writeReport(data, strings, trailingZeros, sections, chunkHeaders, segments, chosen, report);
        System.out.println("Wrote report: " + report);

        if (chosen != null) {
            Path csv = path.resolveSibling(baseName + ".heuristic.v4.best_segment.csv");
            dumpSegmentCsv(data, chosen, csv, DEFAULT_DUMP_RECORDS);
            System.out.println("Wrote CSV:    " + csv);
        }
    }

    private static void checkMagic(byte[] data) {
        String prefix = new String(data, 0, Math.min(MAGIC.length(), data.length), StandardCharsets.US_ASCII);
        System.out.println("Magic ok: " + MAGIC.equals(prefix) + " [" + prefix + "]");
    }

    private static List<StringHit> extractInterestingStrings(byte[] data) {
        List<StringHit> hits = new ArrayList<>();
        int scanLimit = (int) Math.min(data.length, 2L * 1024 * 1024);

        // ASCII runs
        StringBuilder cur = new StringBuilder();
        int start = -1;
        for (int i = 0; i < scanLimit; i++) {
            int b = data[i] & 0xFF;
            if (b >= 32 && b <= 126) {
                if (start < 0) start = i;
                cur.append((char) b);
            } else {
                if (cur.length() >= 8) hits.add(new StringHit(start, "ASCII", cur.toString()));
                cur.setLength(0);
                start = -1;
            }
        }
        if (cur.length() >= 8) hits.add(new StringHit(start, "ASCII", cur.toString()));

        // UTF-16LE runs (printable char followed by 00)
        int i = 0;
        while (i + 1 < scanLimit) {
            int begin = i;
            StringBuilder sb = new StringBuilder();
            while (i + 1 < scanLimit) {
                int lo = data[i] & 0xFF;
                int hi = data[i + 1] & 0xFF;
                if (hi == 0 && lo >= 32 && lo <= 126) {
                    sb.append((char) lo);
                    i += 2;
                } else {
                    break;
                }
            }
            if (sb.length() >= 6) hits.add(new StringHit(begin, "UTF16LE", sb.toString()));
            i = Math.max(i + 1, begin + 1);
        }

        // Collapse duplicates / keep interesting ones
        Collections.sort(hits);
        List<StringHit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Pattern b64Like = Pattern.compile("[A-Za-z0-9+/=]{32,}");
        for (StringHit h : hits) {
            String t = h.text.trim();
            if (t.length() < 6) continue;
            boolean interesting = t.contains("OMICRON") || t.contains("MPD") || t.contains("MCU")
                || t.contains("serialization") || t.contains("Stream File")
                || b64Like.matcher(t).matches() || t.startsWith("Usb") || t.startsWith("Mpd");
            if (interesting || out.size() < 60) {
                String key = h.kind + "|" + t;
                if (seen.add(key)) out.add(new StringHit(h.offset, h.kind, shorten(t, 160)));
            }
        }
        return out;
    }

    private static int trailingZeroPadding(byte[] data) {
        int n = 0;
        for (int i = data.length - 1; i >= 0 && data[i] == 0; i--) n++;
        return n;
    }

    private static List<Section> buildSectionMap(byte[] data) {
        int scanEnd = (int) Math.min(data.length, MAX_SECTION_SCAN);
        List<SectionWindow> windows = new ArrayList<>();
        for (int off = 0; off + SECTION_WINDOW <= scanEnd; off += SECTION_STRIDE) {
            windows.add(scoreSectionWindow(data, off, SECTION_WINDOW));
        }
        if (windows.isEmpty()) return Collections.emptyList();

        List<Section> out = new ArrayList<>();
        int start = windows.get(0).offset;
        int end = windows.get(0).offset + windows.get(0).length;
        SectionKind kind = windows.get(0).kind;
        List<SectionWindow> cur = new ArrayList<>();
        cur.add(windows.get(0));

        for (int idx = 1; idx < windows.size(); idx++) {
            SectionWindow w = windows.get(idx);
            if (w.kind == kind || compatible(kind, w.kind)) {
                cur.add(w);
                end = w.offset + w.length;
            } else {
                out.add(finalizeSection(start, end, kind, cur));
                start = w.offset;
                end = w.offset + w.length;
                kind = w.kind;
                cur = new ArrayList<>();
                cur.add(w);
            }
        }
        out.add(finalizeSection(start, end, kind, cur));
        Collections.sort(out);
        return out;
    }

    private static boolean compatible(SectionKind a, SectionKind b) {
        if (a == b) return true;
        if ((a == SectionKind.HEADER_TEXT || a == SectionKind.TEXT_LIKE) && (b == SectionKind.HEADER_TEXT || b == SectionKind.TEXT_LIKE)) return true;
        if ((a == SectionKind.BINARY || a == SectionKind.MIXED) && (b == SectionKind.BINARY || b == SectionKind.MIXED)) return true;
        return false;
    }

    private static Section finalizeSection(int start, int end, SectionKind kind, List<SectionWindow> ws) {
        double ascii = 0, utf16 = 0, zero = 0, entropy = 0, prpd = 0;
        Map<String,Integer> tmplFreq = new HashMap<>();
        for (SectionWindow w : ws) {
            ascii += w.asciiRatio;
            utf16 += w.utf16Ratio;
            zero += w.zeroRatio;
            entropy += w.entropy;
            prpd += w.bestPrpdScore;
            tmplFreq.merge(w.bestTemplate, 1, Integer::sum);
        }
        String dominant = "-";
        int best = -1;
        for (Map.Entry<String,Integer> e : tmplFreq.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); dominant = e.getKey(); }
        }
        int n = ws.size();
        return new Section(start, end, kind, n, ascii/n, utf16/n, zero/n, entropy/n, prpd/n, dominant);
    }

    private static SectionWindow scoreSectionWindow(byte[] data, int off, int len) {
        int ascii = 0, zero = 0, printable = 0, utf16pairs = 0, utf16good = 0;
        int[] hist = new int[256];
        for (int i = off; i < off + len; i++) {
            int b = data[i] & 0xFF;
            hist[b]++;
            if (b == 0) zero++;
            if (b >= 32 && b <= 126) { ascii++; printable++; }
            else if (b == 9 || b == 10 || b == 13) printable++;
        }
        for (int i = off; i + 1 < off + len; i += 2) {
            int lo = data[i] & 0xFF;
            int hi = data[i+1] & 0xFF;
            utf16pairs++;
            if (hi == 0 && lo >= 32 && lo <= 126) utf16good++;
        }
        double asciiRatio = ascii / (double) len;
        double zeroRatio = zero / (double) len;
        double printRatio = printable / (double) len;
        double utf16Ratio = utf16pairs == 0 ? 0.0 : utf16good / (double) utf16pairs;
        double entropy = shannonEntropy(hist, len);

        double bestPrpd = 0.0;
        String bestTemplate = "-";
        if (off >= DEFAULT_SCAN_START && off + len < data.length) {
            for (Template t : TEMPLATES) {
                WindowHypothesis h = scoreWindow(data, off, len, t);
                if (h != null && h.score > bestPrpd) {
                    bestPrpd = h.score;
                    bestTemplate = t.name;
                }
            }
        }

        SectionKind kind;
        if (zeroRatio > 0.97) kind = SectionKind.ZERO_PADDING;
        else if (off < 65536 && (asciiRatio > 0.18 || utf16Ratio > 0.20)) kind = SectionKind.HEADER_TEXT;
        else if (bestPrpd >= 1.90) kind = SectionKind.PRPD_LIKE;
        else if (asciiRatio > 0.18 || utf16Ratio > 0.18) kind = SectionKind.TEXT_LIKE;
        else if (entropy > 6.5 && zeroRatio < 0.2) kind = SectionKind.BINARY;
        else kind = SectionKind.MIXED;

        return new SectionWindow(off, len, asciiRatio, utf16Ratio, zeroRatio, printRatio, entropy, bestPrpd, kind, bestTemplate);
    }

    private static List<ChunkHeaderCandidate> findChunkHeaderCandidates(byte[] data, List<Section> sections) {
        long scanEnd = Math.min(data.length, DEFAULT_SCAN_START + MAX_SECTION_SCAN);
        PriorityQueue<ChunkHeaderCandidate> pq = new PriorityQueue<>(Comparator.reverseOrder());

        for (int off = 0; off + 16 < scanEnd; off += 4) {
            long len32 = Integer.toUnsignedLong(readU32(data, off));
            scoreChunkHeaderCandidate(data, sections, pq, off, 4, len32);
            if (off + 8 < scanEnd) {
                long len64 = readU64(data, off);
                if (len64 > 0 && len64 < Integer.MAX_VALUE) scoreChunkHeaderCandidate(data, sections, pq, off, 8, len64);
            }
        }

        List<ChunkHeaderCandidate> out = new ArrayList<>(pq);
        Collections.sort(out);
        return out;
    }

    private static void scoreChunkHeaderCandidate(byte[] data, List<Section> sections,
                                                  PriorityQueue<ChunkHeaderCandidate> pq,
                                                  int off, int size, long claimedLength) {
        if (claimedLength < 32 || claimedLength > 4L * 1024 * 1024) return;
        long payloadStartL = off + size;
        long payloadEndL = payloadStartL + claimedLength;
        if (payloadEndL > data.length) return;
        int payloadStart = (int) payloadStartL;
        int payloadEnd = (int) payloadEndL;
        int sampleLen = Math.min(4096, payloadEnd - payloadStart);
        if (sampleLen < 128) return;

        SectionKind startKind = sectionKindAt(sections, payloadStart);
        SectionKind endKind = sectionKindAt(sections, Math.max(payloadStart, payloadEnd - 1));
        double textLike = localTextRatio(data, payloadStart, sampleLen);
        double zeroLike = localZeroRatio(data, payloadStart, sampleLen);
        double prpdLike = localPrpdScore(data, payloadStart, sampleLen);
        double boundaryPenalty = localTextRatio(data, off, Math.min(64, data.length - off));

        double score = 0.0;
        if (startKind == SectionKind.PRPD_LIKE || endKind == SectionKind.PRPD_LIKE) score += 0.9;
        if (startKind == SectionKind.BINARY || startKind == SectionKind.MIXED) score += 0.3;
        if (endKind == SectionKind.BINARY || endKind == SectionKind.PRPD_LIKE) score += 0.2;
        score += 0.6 * Math.min(1.0, prpdLike / 2.2);
        score += 0.2 * Math.max(0.0, 0.25 - textLike);
        score -= 0.5 * zeroLike;
        score -= 0.2 * boundaryPenalty;
        score += 0.15 * Math.min(1.0, claimedLength / 65536.0);

        if (score < 0.65) return;
        String reason = String.format(Locale.US,
            "startKind=%s endKind=%s text=%.3f zero=%.3f prpd=%.3f",
            startKind, endKind, textLike, zeroLike, prpdLike);
        offerDistinctChunk(pq, new ChunkHeaderCandidate(off, size, claimedLength, payloadStart, payloadEnd, score, reason));
    }

    private static void offerDistinctChunk(PriorityQueue<ChunkHeaderCandidate> pq, ChunkHeaderCandidate c) {
        for (ChunkHeaderCandidate e : pq) {
            if (Math.abs(e.offset - c.offset) < 32 && Math.abs(e.payloadEnd - c.payloadEnd) < 128) {
                if (c.score > e.score) {
                    pq.remove(e);
                    pq.offer(c);
                }
                return;
            }
        }
        pq.offer(c);
        while (pq.size() > MAX_TOP_CHUNK_HEADERS) pq.poll();
    }

    private static SectionKind sectionKindAt(List<Section> sections, int offset) {
        for (Section s : sections) if (offset >= s.start && offset < s.end) return s.kind;
        return SectionKind.MIXED;
    }

    private static double localTextRatio(byte[] data, int off, int len) {
        int good = 0;
        for (int i = off; i < off + len && i < data.length; i++) {
            int b = data[i] & 0xFF;
            if (b >= 32 && b <= 126) good++;
        }
        return good / (double) Math.max(1, len);
    }

    private static double localZeroRatio(byte[] data, int off, int len) {
        int zero = 0;
        for (int i = off; i < off + len && i < data.length; i++) if (data[i] == 0) zero++;
        return zero / (double) Math.max(1, len);
    }

    private static double localPrpdScore(byte[] data, int off, int len) {
        double best = 0.0;
        for (Template t : TEMPLATES) {
            WindowHypothesis h = scoreWindow(data, off, len, t);
            if (h != null && h.score > best) best = h.score;
        }
        return best;
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
                    if (h == null || h.score < WINDOW_KEEP_SCORE) continue;
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
        if (base < 0 || base + t.recordLength >= data.length) return null;
        int available = Math.min(windowBytes, data.length - base);
        int records = Math.min(MAX_RECORDS_PER_WINDOW, available / t.recordLength);
        if (records < 128) return null;

        Field phaseField = t.get("phase");
        Field valueField = t.get("value");
        Field timeField = t.get("time");
        Field chanField = t.get("chan");
        if (phaseField == null || valueField == null) return null;

        int phaseGood = 0, valueFinite = 0, valueNonZero = 0, monoGood = 0, monoTotal = 0, positiveDt = 0, positiveDtTotal = 0, dynamicValues = 0;
        long prevTime = Long.MIN_VALUE, firstTime = 0L, lastTime = 0L;
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
                int bin = (phase >= 360.0f) ? 35 : Math.min(35, Math.max(0, (int)(phase / 10.0f)));
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
                long time = timeField.type == FieldType.U64 ? readU64(data, off + timeField.offset)
                    : Integer.toUnsignedLong(readU32(data, off + timeField.offset));
                if (!haveTime) { firstTime = time; haveTime = true; }
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
        for (WindowHypothesis w : windows) byTemplate.computeIfAbsent(w.template, k -> new ArrayList<>()).add(w);

        List<SegmentHypothesis> segments = new ArrayList<>();
        for (Map.Entry<Template, List<WindowHypothesis>> e : byTemplate.entrySet()) {
            List<WindowHypothesis> list = e.getValue();
            list.sort(Comparator.comparingInt(w -> w.baseOffset));
            List<WindowHypothesis> current = new ArrayList<>();
            int currentEnd = -1;
            for (WindowHypothesis w : list) {
                if (current.isEmpty()) {
                    current.add(w); currentEnd = w.endOffset(); continue;
                }
                if (w.baseOffset <= currentEnd + SEGMENT_GAP_BYTES) {
                    current.add(w); currentEnd = Math.max(currentEnd, w.endOffset());
                } else {
                    SegmentHypothesis s = finalizeSegment(data, current);
                    if (s != null) segments.add(s);
                    current = new ArrayList<>(); current.add(w); currentEnd = w.endOffset();
                }
            }
            SegmentHypothesis s = finalizeSegment(data, current);
            if (s != null) segments.add(s);
        }
        Collections.sort(segments);
        return segments.size() > MAX_TOP_SEGMENTS ? new ArrayList<>(segments.subList(0, MAX_TOP_SEGMENTS)) : segments;
    }

    private static SegmentHypothesis finalizeSegment(byte[] data, List<WindowHypothesis> windows) {
        if (windows == null || windows.size() < 2) return null;
        Template t = windows.get(0).template;
        int start = Integer.MAX_VALUE, end = Integer.MIN_VALUE;
        double sumScore = 0.0, minScore = Double.POSITIVE_INFINITY, maxScore = Double.NEGATIVE_INFINITY;
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

        double score = 0.85 * agg.score + 0.45 * mean + 0.20 * median
            + 0.18 * Math.min(1.0, windows.size() / 6.0)
            + 0.12 * Math.min(1.0, approxRecords / 20000.0)
            - 0.10 * Math.max(0.0, 0.25 - agg.phaseCoverage)
            - 0.12 * Math.max(0.0, agg.phasePeakFraction - 0.45);

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
            if (sc > bestScore) { best = s; bestScore = sc; }
        }
        return best;
    }

    private static double representativeScore(SegmentHypothesis s) {
        return s.score + 0.20 * s.phaseCoverage + 0.20 * s.phaseEntropyNorm
            + 0.10 * Math.min(1.0, s.windows.size() / 8.0) - 0.20 * s.phasePeakFraction;
    }

    private static void writeReport(byte[] data, List<StringHit> strings, int trailingZeros,
                                    List<Section> sections, List<ChunkHeaderCandidate> chunkHeaders,
                                    List<SegmentHypothesis> segments, SegmentHypothesis chosen,
                                    Path out) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("Heuristic extractor v4 report\n");
            w.write("This is NOT a confirmed parser. It is a semi-formal reverse-engineering report built from heuristics.\n\n");

            w.write("Top-level observations\n");
            w.write("- Magic header: " + MAGIC + "\n");
            w.write("- Trailing zero padding: " + trailingZeros + " bytes\n");
            if (chosen != null) {
                w.write(String.format(Locale.US,
                    "- Best PRPD-like segment: 0x%X..0x%X (%d bytes), template=%s, score=%.6f\n",
                    chosen.startOffset, chosen.endOffset, chosen.byteLength(), chosen.template.name, chosen.score));
            }
            w.write("\nInteresting strings\n");
            int asciiCount = 0, utfCount = 0;
            for (StringHit s : strings) {
                if (s.kind.equals("ASCII") && asciiCount >= MAX_STRINGS_PER_KIND) continue;
                if (s.kind.equals("UTF16LE") && utfCount >= MAX_STRINGS_PER_KIND) continue;
                if (s.kind.equals("ASCII")) asciiCount++;
                if (s.kind.equals("UTF16LE")) utfCount++;
                w.write(String.format(Locale.US, "0x%X [%s] %s\n", s.offset, s.kind, s.text));
            }

            w.write("\nSection map (coarse container layout)\n");
            for (Section s : sections) {
                w.write(String.format(Locale.US,
                    "0x%X..0x%X bytes=%d kind=%s windows=%d ascii=%.3f utf16=%.3f zero=%.3f entropy=%.3f prpd=%.3f dominantTemplate=%s\n",
                    s.start, s.end, s.length(), s.kind, s.windows,
                    s.meanAsciiRatio, s.meanUtf16Ratio, s.meanZeroRatio,
                    s.meanEntropy, s.meanBestPrpdScore, s.dominantTemplate));
            }

            w.write("\nCandidate length-prefixed chunk headers\n");
            if (chunkHeaders.isEmpty()) w.write("(none)\n");
            for (int i = 0; i < Math.min(24, chunkHeaders.size()); i++) {
                ChunkHeaderCandidate c = chunkHeaders.get(i);
                w.write(String.format(Locale.US,
                    "#%d off=0x%X lenField=%d claimedLength=%d payload=0x%X..0x%X score=%.4f %s\n",
                    i + 1, c.offset, c.lengthFieldSize, c.claimedLength,
                    c.payloadStart, c.payloadEnd, c.score, c.reason));
            }

            w.write("\nTop PRPD-like segments\n");
            if (segments.isEmpty()) w.write("(none)\n");
            for (int i = 0; i < Math.min(20, segments.size()); i++) {
                SegmentHypothesis s = segments.get(i);
                w.write(String.format(Locale.US,
                    "#%d range=0x%X..0x%X bytes=%d tpl=%s score=%.6f windows=%d estRecs=%d phase=%.3f finite=%.3f nz=%.3f mono=%.3f dt+=%.3f ent=%.3f cov=%.3f peak=%.3f dyn=%.3f chan=%d\n",
                    i + 1, s.startOffset, s.endOffset, s.byteLength(), s.template.name, s.score,
                    s.windows.size(), s.estimatedRecords, s.phaseFraction, s.finiteValueFraction,
                    s.nonZeroValueFraction, s.monotonicTimeFraction, s.positiveTimeDeltaFraction,
                    s.phaseEntropyNorm, s.phaseCoverage, s.phasePeakFraction, s.valueDynamicFraction,
                    s.channelCardinality));
            }

            w.write("\nSemi-formal interpretation\n");
            w.write("1. Header and metadata occupy the early text-like region(s), including ASCII and UTF-16LE strings.\n");
            w.write("2. Later binary or PRPD-like sections are better candidates for event payload than the early metadata.\n");
            w.write("3. Candidate chunk headers are only hypotheses: offsets where a little-endian length field could delimit a binary block.\n");
            w.write("4. The best exported CSV still comes from the strongest PRPD-like segment, not from a confirmed vendor specification.\n");
        }
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
                    timeValue = time.type == FieldType.U64 ? readU64(data, off + time.offset)
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
        if (av < 1.0e-12f || av > 1.0e12f) return false;
        float threshold = Math.max(absP95 * 20.0f, 1.0e-6f);
        return av <= threshold;
    }

    private static HistogramStats histogramStats(int[] bins, int total) {
        if (total <= 0) return new HistogramStats(0.0, 0.0, 1.0);
        int nonEmpty = 0, max = 0;
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
        int i = (int)Math.floor(pos);
        int j = Math.min(n - 1, i + 1);
        double frac = pos - i;
        return (float)(sorted[i] * (1.0 - frac) + sorted[j] * frac);
    }

    private static double shannonEntropy(int[] hist, int total) {
        double h = 0.0;
        for (int c : hist) {
            if (c == 0) continue;
            double p = c / (double) total;
            h -= p * (Math.log(p) / Math.log(2.0));
        }
        return h;
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

    private static String shorten(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
