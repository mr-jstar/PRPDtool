package redpitaya;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;

public class RedPitayaSession implements Closeable {

    private static final byte[] FRAME_MAGIC = {'R', 'P', 'D', 'F'};
    private static final int VERSION = 1;
    private static final int FRAME_HEADER_SIZE = 34;

    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;
    private final Map<String, Object> metadata;

    public RedPitayaSession(RedPitayaConfig config) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(config.host, config.port), 10_000);
        socket.setReceiveBufferSize(8 * 1024 * 1024);
        socket.setTcpNoDelay(true);
        this.in = new BufferedInputStream(socket.getInputStream(), 1 << 20);
        this.out = new BufferedOutputStream(socket.getOutputStream(), 1 << 20);

        sendJson(config.toPayload());
        Map<String, Object> response = SimpleJson.parseObject(readJsonLine());
        if (!Boolean.TRUE.equals(response.get("ok"))) {
            Object error = response.get("error");
            throw new IOException(error == null ? "Agent rejected acquisition" : error.toString());
        }
        Object rawMetadata = response.get("metadata");
        if (rawMetadata instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            this.metadata = typed;
        } else {
            this.metadata = Map.of();
        }
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public RedPitayaFrame readFrame() throws IOException {
        byte[] header = readExactOrNull(FRAME_HEADER_SIZE);
        if (header == null) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        bb.get(magic);
        for (int i = 0; i < FRAME_MAGIC.length; i++) {
            if (magic[i] != FRAME_MAGIC[i]) {
                throw new IOException("Bad Red Pitaya frame magic");
            }
        }
        int version = Short.toUnsignedInt(bb.getShort());
        int headerSize = Short.toUnsignedInt(bb.getShort());
        int seq = bb.getInt();
        int channelCount = Short.toUnsignedInt(bb.getShort());
        double sampleRate = bb.getDouble();
        int sampleCount = bb.getInt();
        int payloadLen = bb.getInt();
        long expectedCrc = Integer.toUnsignedLong(bb.getInt());

        if (version != VERSION) {
            throw new IOException("Unsupported Red Pitaya frame version: " + version);
        }
        if (headerSize != FRAME_HEADER_SIZE) {
            throw new IOException("Bad Red Pitaya frame header size: " + headerSize);
        }
        int expectedPayloadLen = Math.multiplyExact(Math.multiplyExact(sampleCount, channelCount), 2);
        if (payloadLen != expectedPayloadLen) {
            throw new IOException("Bad Red Pitaya payload size in frame " + seq);
        }

        byte[] payload = readExact(payloadLen);
        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != expectedCrc) {
            throw new IOException("CRC mismatch in Red Pitaya frame " + seq);
        }
        return new RedPitayaFrame(seq, channelCount, sampleRate, sampleCount, payload);
    }

    private void sendJson(Object payload) throws IOException {
        out.write(SimpleJson.stringify(payload).getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }

    private String readJsonLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("Socket closed while reading JSON response");
            }
            if (b == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            line.write(b);
            if (line.size() > 1_000_000) {
                throw new IOException("JSON response is too large");
            }
        }
    }

    private byte[] readExactOrNull(int size) throws IOException {
        byte[] data = new byte[size];
        int off = 0;
        while (off < size) {
            int n = in.read(data, off, size - off);
            if (n < 0) {
                if (off == 0) {
                    return null;
                }
                throw new EOFException("Unexpected EOF in Red Pitaya frame");
            }
            off += n;
        }
        return data;
    }

    private byte[] readExact(int size) throws IOException {
        byte[] data = readExactOrNull(size);
        if (data == null) {
            throw new EOFException("Unexpected EOF");
        }
        return data;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
