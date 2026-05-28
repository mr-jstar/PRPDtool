package redpitaya;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.function.Consumer;
import pipeline.Buffer;
import pipeline.BufferFactory;
import pipeline.SignalReader;

public class RedPitayaSignalReader implements SignalReader, Closeable {

    private final RedPitayaConfig config;
    private final boolean live;
    private final int consumerCount;
    private final int maxSamples;
    private final Path saveDirectory;
    private final Consumer<Path> savedCaptureConsumer;
    private final long liveRestartDelayMillis;
    private RedPitayaSession session;
    private RedPitayaFileWriter writer;
    private long sampleOffset;
    private volatile boolean closed;

    public RedPitayaSignalReader(RedPitayaConfig config, boolean live, int consumerCount, int maxSamples) {
        this(config, live, consumerCount, maxSamples, null, null, 250L);
    }

    public RedPitayaSignalReader(
            RedPitayaConfig config,
            boolean live,
            int consumerCount,
            int maxSamples,
            Path saveDirectory,
            Consumer<Path> savedCaptureConsumer,
            long liveRestartDelayMillis
    ) {
        this.config = config.copy();
        this.live = live;
        this.consumerCount = consumerCount;
        this.maxSamples = maxSamples;
        this.saveDirectory = saveDirectory;
        this.savedCaptureConsumer = savedCaptureConsumer;
        this.liveRestartDelayMillis = Math.max(0L, liveRestartDelayMillis);
    }

    @Override
    public Buffer read() throws IOException {
        while (!closed) {
            if (session == null) {
                session = new RedPitayaSession(config);
                openWriter();
                if (!live) {
                    sampleOffset = 0;
                }
            }

            RedPitayaFrame frame = session.readFrame();
            if (frame == null) {
                closeSession();
                if (live) {
                    sleepBeforeNextLiveWindow();
                    continue;
                }
                Buffer eof = BufferFactory.acquire(consumerCount);
                eof.clear();
                eof.setEOF();
                return eof;
            }
            if (writer != null) {
                writer.writeFrame(frame);
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
        closeWriter();
        if (session != null) {
            try {
                session.close();
            } finally {
                session = null;
            }
        }
    }

    private void openWriter() throws IOException {
        if (saveDirectory == null || session == null) {
            return;
        }
        Path path = RedPitayaFileWriter.buildOutputPath(saveDirectory.toFile(), session.metadata());
        writer = new RedPitayaFileWriter(path, session.metadata());
    }

    private void closeWriter() throws IOException {
        if (writer == null) {
            return;
        }
        Path savedPath = writer.path();
        try {
            writer.close();
        } finally {
            writer = null;
        }
        if (savedCaptureConsumer != null && savedPath != null) {
            savedCaptureConsumer.accept(savedPath);
        }
    }

    private void sleepBeforeNextLiveWindow() throws IOException {
        if (liveRestartDelayMillis <= 0L || closed) {
            return;
        }
        try {
            Thread.sleep(liveRestartDelayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted before next live acquisition", ex);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        closeSession();
    }
}
