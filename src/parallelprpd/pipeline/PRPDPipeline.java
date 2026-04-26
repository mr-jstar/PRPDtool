package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
import javax.swing.SwingUtilities;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class PRPDPipeline implements AutoCloseable {

    private final ConcurrentLinkedQueue<Buffer> queue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean readerFinished = new AtomicBoolean(false);
    private final AtomicInteger queuedBuffers = new AtomicInteger(0);

    private final ExecutorService readerExecutor =
            Executors.newSingleThreadExecutor();

    private final ExecutorService extractorExecutor =
            Executors.newSingleThreadExecutor();

    private final String filename;
    private final int bufferSize;
    private final int maxQueuedBuffers;

    private final PRPDExtractorCore extractor;
    private final PRPDPipelineListener listener;

    public PRPDPipeline(
            String filename,
            int bufferSize,
            int maxQueuedBuffers,
            PRPDExtractorCore extractor,
            PRPDPipelineListener listener
    ) {
        this.filename = filename;
        this.bufferSize = bufferSize;
        this.maxQueuedBuffers = maxQueuedBuffers;
        this.extractor = extractor;
        this.listener = listener;
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
        try (BufferedReader br = new BufferedReader(new FileReader(filename), 1 << 20)) {

            skipHeaderIfPresent(br);

            while (running.get()) {

                while (running.get() && queuedBuffers.get() >= maxQueuedBuffers) {
                    Thread.onSpinWait();
                }

                Buffer buffer = readBuffer(br);

                if (buffer.size > 0) {
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

        } catch (Exception ex) {
            running.set(false);
            readerFinished.set(true);
            SwingUtilities.invokeLater(() -> listener.error(ex));
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
            SwingUtilities.invokeLater(listener::finished);

        } catch (Exception ex) {
            running.set(false);
            SwingUtilities.invokeLater(() -> listener.error(ex));
        }
    }

    private Buffer readBuffer(BufferedReader br) throws IOException {
        double[] t = new double[bufferSize];
        double[] u = new double[bufferSize];

        int n = 0;
        boolean eof = false;

        while (n < bufferSize) {
            String line = br.readLine();

            if (line == null) {
                eof = true;
                break;
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] p = line.split("[,;\\s]+");

            if (p.length < 2) {
                continue;
            }

            t[n] = Double.parseDouble(p[0]);
            u[n] = Double.parseDouble(p[1]);
            n++;
        }

        return new Buffer(t, u, n, eof);
    }

    private static void skipHeaderIfPresent(BufferedReader br) throws IOException {
        br.mark(2048);

        String first = br.readLine();

        if (first == null) {
            return;
        }

        String s = first.trim().toLowerCase(Locale.US);

        if (!(s.startsWith("t") || s.contains("u"))) {
            br.reset();
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
}