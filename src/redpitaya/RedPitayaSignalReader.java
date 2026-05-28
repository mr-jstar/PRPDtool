package redpitaya;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import pipeline.Buffer;
import pipeline.BufferFactory;
import pipeline.SignalReader;

public class RedPitayaSignalReader implements SignalReader, Closeable {

    private final RedPitayaConfig config;
    private final boolean live;
    private final int consumerCount;
    private final int maxSamples;
    private RedPitayaSession session;
    private long sampleOffset;
    private volatile boolean closed;

    public RedPitayaSignalReader(RedPitayaConfig config, boolean live, int consumerCount, int maxSamples) {
        this.config = config.copy();
        this.live = live;
        this.consumerCount = consumerCount;
        this.maxSamples = maxSamples;
    }

    @Override
    public Buffer read() throws IOException {
        while (!closed) {
            if (session == null) {
                session = new RedPitayaSession(config);
                if (!live) {
                    sampleOffset = 0;
                }
            }

            RedPitayaFrame frame = session.readFrame();
            if (frame == null) {
                closeSession();
                if (live) {
                    continue;
                }
                Buffer eof = BufferFactory.acquire(consumerCount);
                eof.clear();
                eof.setEOF();
                return eof;
            }
            return frameToBuffer(frame);
        }
        Buffer eof = BufferFactory.acquire(consumerCount);
        eof.clear();
        eof.setEOF();
        return eof;
    }

    private Buffer frameToBuffer(RedPitayaFrame frame) throws IOException {
        if (frame.sampleCount > maxSamples) {
            throw new IOException("Red Pitaya frame too large: " + frame.sampleCount + " samples");
        }
        int visualIndex = config.visualChannelIndex();
        if (visualIndex >= frame.channelCount) {
            throw new IOException("Selected channel is not present in Red Pitaya frame");
        }

        Buffer buf = BufferFactory.acquire(consumerCount);
        buf.clear();
        ByteBuffer bb = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN);
        double fs = frame.sampleRate;
        for (int i = 0; i < frame.sampleCount; i++) {
            buf.t[i] = (sampleOffset + i) / fs;
            short selected = 0;
            for (int ch = 0; ch < frame.channelCount; ch++) {
                short value = bb.getShort();
                if (ch == visualIndex) {
                    selected = value;
                }
            }
            buf.u[i] = selected;
        }
        sampleOffset += frame.sampleCount;
        buf.setUsed(frame.sampleCount);
        return buf;
    }

    private void closeSession() throws IOException {
        if (session != null) {
            try {
                session.close();
            } finally {
                session = null;
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        closeSession();
    }
}
