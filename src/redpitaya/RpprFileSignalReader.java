package redpitaya;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import pipeline.Buffer;
import pipeline.BufferFactory;
import pipeline.SignalReader;

public class RpprFileSignalReader implements SignalReader, Closeable {

    private static final int FILE_HEADER_SIZE = 10;
    private static final int FRAME_HEADER_SIZE = 34;

    private final FileChannel channel;
    private final int consumerCount;
    private final int visualChannel;
    private final List<Integer> channels;
    private long sampleOffset;

    public RpprFileSignalReader(String filename, int consumerCount, int visualChannel) throws IOException {
        this.channel = new FileInputStream(new File(filename)).getChannel();
        this.consumerCount = consumerCount;
        Map<String, Object> metadata = readFileHeader();
        this.channels = parseChannels(metadata.get("channels"));
        this.visualChannel = channels.contains(visualChannel) ? visualChannel : channels.get(0);
    }

    public static boolean isRpprFile(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".rppr.bin");
    }

    public static double sampleRate(String filename, double fallback) {
        try {
            Object raw = readMetadata(filename).get("sample_rate");
            if (raw instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ex) {
        }
        return fallback;
    }

    public static Map<String, Object> readMetadata(String filename) throws IOException {
        try (FileChannel ch = new FileInputStream(new File(filename)).getChannel()) {
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, header);
            header.flip();
            byte[] magic = new byte[4];
            header.get(magic);
            if (magic[0] != 'R' || magic[1] != 'P' || magic[2] != 'P' || magic[3] != 'R') {
                throw new IOException("Not an RPPR file");
            }
            int version = Short.toUnsignedInt(header.getShort());
            if (version != 1) {
                throw new IOException("Unsupported RPPR version: " + version);
            }
            int metadataLen = header.getInt();
            ByteBuffer rawMetadata = ByteBuffer.allocate(metadataLen);
            readFully(ch, rawMetadata);
            rawMetadata.flip();
            return SimpleJson.parseObject(StandardCharsets.UTF_8.decode(rawMetadata).toString());
        }
    }

    private Map<String, Object> readFileHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        readFully(header);
        header.flip();
        byte[] magic = new byte[4];
        header.get(magic);
        if (magic[0] != 'R' || magic[1] != 'P' || magic[2] != 'P' || magic[3] != 'R') {
            throw new IOException("Not an RPPR file");
        }
        int version = Short.toUnsignedInt(header.getShort());
        if (version != 1) {
            throw new IOException("Unsupported RPPR version: " + version);
        }
        int metadataLen = header.getInt();
        if (metadataLen < 0 || metadataLen > 10_000_000) {
            throw new IOException("Bad RPPR metadata length");
        }
        ByteBuffer rawMetadata = ByteBuffer.allocate(metadataLen);
        readFully(rawMetadata);
        rawMetadata.flip();
        String json = StandardCharsets.UTF_8.decode(rawMetadata).toString();
        return SimpleJson.parseObject(json);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> parseChannels(Object raw) {
        ArrayList<Integer> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number n) {
                    result.add(n.intValue());
                }
            }
        }
        if (result.isEmpty()) {
            result.add(1);
        }
        return result;
    }

    @Override
    public Buffer read() throws IOException {
        RedPitayaFrame frame = readFrame();
        if (frame == null) {
            Buffer eof = BufferFactory.acquire(consumerCount);
            eof.clear();
            eof.setEOF();
            return eof;
        }
        int visualIndex = channels.indexOf(visualChannel);
        if (visualIndex < 0 || visualIndex >= frame.channelCount) {
            visualIndex = 0;
        }
        Buffer buffer = BufferFactory.acquire(consumerCount);
        buffer.clear();
        ByteBuffer payload = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frame.sampleCount; i++) {
            buffer.t[i] = (sampleOffset + i) / frame.sampleRate;
            short selected = 0;
            for (int ch = 0; ch < frame.channelCount; ch++) {
                short value = payload.getShort();
                if (ch == visualIndex) {
                    selected = value;
                }
            }
            buffer.u[i] = selected;
        }
        sampleOffset += frame.sampleCount;
        buffer.setUsed(frame.sampleCount);
        return buffer;
    }

    private RedPitayaFrame readFrame() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(FRAME_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int first = channel.read(header);
        if (first < 0) {
            return null;
        }
        while (header.hasRemaining()) {
            if (channel.read(header) < 0) {
                throw new EOFException("Truncated RPPR frame header");
            }
        }
        header.flip();
        byte[] magic = new byte[4];
        header.get(magic);
        if (magic[0] != 'R' || magic[1] != 'P' || magic[2] != 'D' || magic[3] != 'F') {
            throw new IOException("Bad RPPR frame magic");
        }
        int version = Short.toUnsignedInt(header.getShort());
        int headerSize = Short.toUnsignedInt(header.getShort());
        int seq = header.getInt();
        int channelCount = Short.toUnsignedInt(header.getShort());
        double sampleRate = header.getDouble();
        int sampleCount = header.getInt();
        int payloadLen = header.getInt();
        long expectedCrc = Integer.toUnsignedLong(header.getInt());
        if (version != 1 || headerSize != FRAME_HEADER_SIZE) {
            throw new IOException("Unsupported RPPR frame header");
        }
        int expectedPayloadLen = Math.multiplyExact(Math.multiplyExact(sampleCount, channelCount), 2);
        if (payloadLen != expectedPayloadLen) {
            throw new IOException("Bad RPPR payload size in frame " + seq);
        }
        byte[] payload = new byte[payloadLen];
        ByteBuffer rawPayload = ByteBuffer.wrap(payload);
        readFully(rawPayload);
        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != expectedCrc) {
            throw new IOException("CRC mismatch in RPPR frame " + seq);
        }
        return new RedPitayaFrame(seq, channelCount, sampleRate, sampleCount, payload);
    }

    private void readFully(ByteBuffer buffer) throws IOException {
        readFully(channel, buffer);
    }

    private static void readFully(FileChannel ch, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (ch.read(buffer) < 0) {
                throw new EOFException("Unexpected end of RPPR file");
            }
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
