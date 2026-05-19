package pipeline;

/**
 *
 * @author jstar
 */
import java.io.File;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public class PRPDPipeline implements AutoCloseable {

    private final ConcurrentLinkedQueue<Buffer> queue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean readerFinished = new AtomicBoolean(false);
    private final AtomicInteger queuedBuffers = new AtomicInteger(0);
    protected final CountDownLatch doneLatch = new CountDownLatch(1);

    private final ExecutorService readerExecutor
            = Executors.newSingleThreadExecutor();

    private final ExecutorService extractorExecutor
            = Executors.newSingleThreadExecutor();

    private final String filename;
    private final int bufferSize;
    private final int maxQueuedBuffers;
    private final int consumerCount;

    private final PRPDExtractorCore extractor;
    private final PRPDPipelineListener listener;

    private final AtomicInteger readLoops = new AtomicInteger();
    private Consumer<Integer> onReaderProgress = n -> {
    };

    public PRPDPipeline(
            String filename,
            int consumerCount,
            int bufferSize,
            int maxQueuedBuffers,
            PRPDExtractorCore extractor,
            PRPDPipelineListener listener
    ) {
        this.filename = filename;
        this.consumerCount = consumerCount;
        this.bufferSize = bufferSize;
        this.maxQueuedBuffers = maxQueuedBuffers;
        this.extractor = extractor;
        this.listener = listener;
    }

    public void setOnReaderProgress(Consumer<Integer> callback) {
        this.onReaderProgress = callback != null ? callback : n -> {
        };
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        readerFinished.set(false);
        queuedBuffers.set(0);

        readerExecutor.execute(this::readerLoop);
        extractorExecutor.execute(this::extractorLoop);
    }

    private void readerLoop() {
        try {
            SignalReader reader = null;
            if (!(new File(filename)).exists()) {
                String[] hp = filename.split(":");
                if (hp.length == 2) {
                    reader = new BinarySocketReader(
                            hp[0], // host name or IP
                            Integer.parseInt(hp[1]), // port
                            consumerCount,
                            bufferSize
                    );
                }
            } else {
                if (filename.endsWith(".csv")) {
                    reader = new TextReader(filename, consumerCount, bufferSize);
                } else {
                    reader = new BinaryReader(filename, consumerCount, bufferSize);;
                }
            }

            while (running.get()) {
                int n = readLoops.incrementAndGet();
                onReaderProgress.accept(n);

                while (running.get() && queuedBuffers.get() >= maxQueuedBuffers) {
                    Thread.onSpinWait();
                }

                Buffer buffer = reader.read();

                if (buffer.used > 0) {
                    queue.add(buffer);
                    queuedBuffers.incrementAndGet();

                    // Obwiednia dostaje surowy bufor natychmiast po odczycie.
                    SwingUtilities.invokeLater(() -> listener.bufferRead(buffer));
                }

                if (buffer.eof) {
                    readerFinished.set(true);
                    break;
                }
            }

        } catch (Throwable ex) {
            running.set(false);
            readerFinished.set(true);
            SwingUtilities.invokeLater(() -> listener.error(ex, " in readerLoop"));
        }
    }

    private void extractorLoop() {
        try {
            while (running.get()) {
                Buffer buffer = queue.poll();

                if (buffer == null) {
                    if (readerFinished.get()) {
                        break;
                    }

                    Thread.onSpinWait();
                    continue;
                }

                queuedBuffers.decrementAndGet();

                Pulses pulses = extractor.extract(buffer);

                if (pulses.size > 0) {
                    SwingUtilities.invokeLater(() -> listener.pulsesReady(pulses));
                }
            }

            running.set(false);
            SwingUtilities.invokeAndWait(listener::finished);

        } catch (Throwable ex) {
            running.set(false);
            SwingUtilities.invokeLater(() -> listener.error(ex, " in extractorLoop"));
        } finally {
            doneLatch.countDown();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getQueuedBuffers() {
        return queuedBuffers.get();
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void close() {
        stop();
        readerExecutor.shutdownNow();
        extractorExecutor.shutdownNow();
    }

    public void awaitFinished() throws InterruptedException {
        doneLatch.await();
    }
}
