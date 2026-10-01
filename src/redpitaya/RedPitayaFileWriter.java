package redpitaya;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.zip.CRC32;

public class RedPitayaFileWriter implements Closeable {

    private static final byte[] FILE_MAGIC = {'R', 'P', 'P', 'R'};
    private static final byte[] FRAME_MAGIC = {'R', 'P', 'D', 'F'};
    private static final int VERSION = 1;
    private static final int FILE_HEADER_SIZE = 10;
    private static final int FRAME_HEADER_SIZE = 34;
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path path;
    private final FileChannel channel;

    public RedPitayaFileWriter(Path path, Map<String, Object> metadata) throws IOException {
        this.path = path;
        Files.createDirectories(path.getParent());
        this.channel = new FileOutputStream(path.toFile()).getChannel();
        writeFileHeader(metadata);
    }

    public static Path buildOutputPath(File directory, Map<String, Object> metadata) {
        String stamp = LocalDateTime.now().format(STAMP_FORMAT);
        String rawDefect = (String) metadata.getOrDefault("defect_type", "unknown");
        String rawLabel = (String) metadata.getOrDefault("session_label", "");
        String label = sanitizeForFilename(rawLabel);

        double v = metadata.containsKey("voltage_kv") ? ((Number) metadata.get("voltage_kv")).doubleValue() : 0.0;
        String rawSensor = (String) metadata.getOrDefault("sensor", "");
        String rawVariant = (String) metadata.getOrDefault("variant", "");
        
        String vStr = (v % 1 == 0) ? String.format(java.util.Locale.US, "%.0fkV", v) : String.format(java.util.Locale.US, "%.1fkV", v);
        
        StringBuilder extrasBuilder = new StringBuilder(vStr);
        if (!rawSensor.isBlank() && !"<None>".equals(rawSensor)) {
            extrasBuilder.append("_").append(sanitizeForFilename(rawSensor));
        }
        if (!rawVariant.isBlank() && !"<None>".equals(rawVariant)) {
            extrasBuilder.append("_").append(sanitizeForFilename(rawVariant));
        }
        String extras = extrasBuilder.toString();

        if ("<None / Custom>".equals(rawDefect)) {
            String prefix = label.isEmpty() ? "rp" : label;
            return directory.toPath().resolve(prefix + "_" + extras + "_" + stamp + ".rppr.bin");
        } else {
            String defect = sanitizeForFilename(rawDefect);
            String filename = label.isEmpty()
                    ? defect + "_" + extras + "_" + stamp + ".rppr.bin"
                    : defect + "_" + extras + "_" + label + "_" + stamp + ".rppr.bin";
            Path subdir = directory.toPath().resolve("sessions").resolve(defect);
            return subdir.resolve(filename);
        }
    }

    /** Zastępuje znaki niedozwolone w nazwach plików/folderów podkreślnikiem. */
    public static String sanitizeForFilename(String name) {
        if (name == null || name.isBlank()) return "unknown";
        return name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public Path path() {
        return path;
    }

    public void writeFrame(RedPitayaFrame frame) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(frame.payload);

        ByteBuffer header = ByteBuffer.allocate(FRAME_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.put(FRAME_MAGIC);
        header.putShort((short) VERSION);
        header.putShort((short) FRAME_HEADER_SIZE);
        header.putInt(frame.seq);
        header.putShort((short) frame.channelCount);
        header.putDouble(frame.sampleRate);
        header.putInt(frame.sampleCount);
        header.putInt(frame.payload.length);
        header.putInt((int) crc.getValue());
        header.flip();

        channel.write(header);
        channel.write(ByteBuffer.wrap(frame.payload));
    }

    private void writeFileHeader(Map<String, Object> metadata) throws IOException {
        byte[] rawMetadata = SimpleJson.stringify(metadata).getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.put(FILE_MAGIC);
        header.putShort((short) VERSION);
        header.putInt(rawMetadata.length);
        header.flip();

        channel.write(header);
        channel.write(ByteBuffer.wrap(rawMetadata));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static String channelSuffix(Object channels) {
        StringBuilder suffix = new StringBuilder("ch");
        if (channels instanceof Iterable<?> iterable) {
            for (Object channel : iterable) {
                if (channel instanceof Number n) {
                    suffix.append(n.intValue());
                }
            }
        }
        return suffix.length() > 2 ? suffix.toString() : "ch1";
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
