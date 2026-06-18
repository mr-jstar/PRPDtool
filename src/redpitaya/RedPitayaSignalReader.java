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

    private static final long DEFAULT_AXI_USABLE_BYTES = 0x6400000L;
    private static final String AXI_BYTES_PROPERTY = "prpd.axiDmaBytes";
    private static final String AXI_BYTES_ENV = "PRPD_AXI_DMA_BYTES";
    private static final int BYTES_PER_SAMPLE = 2;

    private final RedPitayaConfig config;
    private final boolean live;
    private final int consumerCount;
    private final int maxSamples;
    private final Path saveDirectory;
    private final Consumer<Path> savedCaptureConsumer;
    private final long liveRestartDelayMillis;
    private final long requestedSamplesPerWindow;
    private final long maxSamplesPerAcquisition;
    private RedPitayaSession session;
    private RedPitayaFileWriter writer;
    private long sampleOffset;
    private long windowSamplesRemaining;
    private boolean windowActive;
    private boolean onceWindowDone;
    private volatile boolean closed;
    private final String filePrefix;

    public RedPitayaSignalReader(RedPitayaConfig config, boolean live, int consumerCount, int maxSamples) {
        this(config, live, consumerCount, maxSamples, null, null, 250L, "rp_");
    }

    public RedPitayaSignalReader(
            RedPitayaConfig config,
            boolean live,
            int consumerCount,
            int maxSamples,
            Path saveDirectory,
            Consumer<Path> savedCaptureConsumer,
            long liveRestartDelayMillis,
            String filePrefix
    ) {
        this.config = config.copy();
        this.live = live;
        this.consumerCount = consumerCount;
        this.maxSamples = maxSamples;
        this.saveDirectory = saveDirectory;
        this.savedCaptureConsumer = savedCaptureConsumer;
        this.liveRestartDelayMillis = Math.max(0L, liveRestartDelayMillis);
        this.requestedSamplesPerWindow = this.config.totalSamples();
        this.maxSamplesPerAcquisition = maxSamplesPerAcquisition(this.config);
        this.filePrefix = filePrefix;
    }

    @Override
    public Buffer read() throws IOException {
        while (!closed) {
            if (!windowActive) {
                if (!live && onceWindowDone) {
                    return eofBuffer();
                }
                startWindow();
            }

            if (session == null) {
                openNextSession();
                if (session == null) {
                    continue;
                }
            }

            RedPitayaFrame frame = session.readFrame();
            if (frame == null) {
                closeSession();
                if (windowSamplesRemaining <= 0) {
                    finishWindow();
                }
                continue;
            }
            if (writer != null) {
                writer.writeFrame(frame);
            }
            windowSamplesRemaining = Math.max(0L, windowSamplesRemaining - frame.sampleCount);
            return frameToBuffer(frame);
        }
        return eofBuffer();
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

    private void startWindow() {
        windowSamplesRemaining = requestedSamplesPerWindow;
        windowActive = true;
        if (!live) {
            sampleOffset = 0;
        }
    }

    private void finishWindow() throws IOException {
        closeWriter();
        windowActive = false;
        if (live && !closed) {
            sleepBeforeNextLiveWindow();
        } else {
            onceWindowDone = true;
        }
    }

    private void openNextSession() throws IOException {
        long sessionSamples = Math.min(windowSamplesRemaining, maxSamplesPerAcquisition);
        if (sessionSamples <= 0) {
            finishWindow();
            return;
        }
        RedPitayaConfig sessionConfig = config.forTotalSamples(sessionSamples);
        session = new RedPitayaSession(sessionConfig);
        openWriter(session.metadata());
    }

    private void openWriter(java.util.Map<String, Object> metadata) throws IOException {
        if (writer != null || saveDirectory == null) {
            return;
        }
        java.util.Map<String, Object> fileMetadata = new java.util.LinkedHashMap<>(metadata);
        fileMetadata.put("total_samples", requestedSamplesPerWindow);
        fileMetadata.put("frame_count", (requestedSamplesPerWindow + config.normalizedFrameSize() - 1L) / config.normalizedFrameSize());
        fileMetadata.put("duration_s", requestedSamplesPerWindow / config.sampleRate());
        fileMetadata.put("java_chunked_acquisition", requestedSamplesPerWindow > maxSamplesPerAcquisition);
        fileMetadata.put("java_max_samples_per_acquisition", maxSamplesPerAcquisition);
        fileMetadata.put("java_axi_dma_bytes", configuredAxiUsableBytes());
        if (filePrefix != null) {
            fileMetadata.put("file_prefix", filePrefix);
        }
        Path path = RedPitayaFileWriter.buildOutputPath(saveDirectory.toFile(), fileMetadata);
        writer = new RedPitayaFileWriter(path, fileMetadata);
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

    private static long maxSamplesPerAcquisition(RedPitayaConfig config) {
        int channelCount = Math.max(1, config.channels.length);
        long samples = configuredAxiUsableBytes() / channelCount / BYTES_PER_SAMPLE;
        samples = Math.max(2L, samples);
        if ((samples & 1L) != 0L) {
            samples--;
        }
        return samples;
    }

    private static long configuredAxiUsableBytes() {
        String configured = System.getProperty(AXI_BYTES_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(AXI_BYTES_ENV);
        }
        if (configured != null && !configured.isBlank()) {
            try {
                long bytes = Long.decode(configured.trim());
                if (bytes > 0L) {
                    return bytes;
                }
            } catch (NumberFormatException ex) {
            }
        }
        return DEFAULT_AXI_USABLE_BYTES;
    }

    private Buffer eofBuffer() {
        Buffer eof = BufferFactory.acquire(consumerCount);
        eof.clear();
        eof.setEOF();
        return eof;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        closeSession();
        closeWriter();
    }
}
