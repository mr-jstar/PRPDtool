package parallelprpd.v1;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.io.*;
import java.util.Locale;

public class PRPDFileReader implements Closeable {

    public static class Buffer {
        public final double[] t;
        public final double[] u;
        public final int size;
        public final double tStart;
        public final double tEnd;

        public Buffer(double[] t, double[] u, int size) {
            this.t = t;
            this.u = u;
            this.size = size;
            this.tStart = size > 0 ? t[0] : Double.NaN;
            this.tEnd = size > 0 ? t[size - 1] : Double.NaN;
        }
    }

    private final BufferedReader reader;
    private final int bufferSize;
    private BufferListener listener;

    private boolean eof = false;
    private boolean busy = false;
    private long totalSamplesRead = 0;

    public PRPDFileReader(String filename, int bufferSize) throws IOException {
        this.bufferSize = bufferSize;
        this.reader = new BufferedReader(new FileReader(filename), 1 << 20);

        reader.mark(2048);
        String first = reader.readLine();

        if (first != null) {
            String s = first.trim().toLowerCase(Locale.US);
            if (!(s.startsWith("t") || s.contains("u"))) {
                reader.reset();
            }
        }
    }

    public void setBufferListener(BufferListener listener) {
        this.listener = listener;
    }

    public void readNextAsync() {
        if (busy || eof) {
            return;
        }

        busy = true;

        SwingWorker<Buffer, Void> worker = new SwingWorker<>() {
            @Override
            protected Buffer doInBackground() throws Exception {
                return readNextBuffer();
            }

            @Override
            protected void done() {
                busy = false;

                try {
                    Buffer buffer = get();

                    if (buffer.size == 0) {
                        eof = true;
                        if (listener != null) {
                            listener.endOfFile();
                        }
                    } else {
                        if (listener != null) {
                            listener.bufferReady(buffer);
                        }
                    }

                } catch (Exception ex) {
                    if (listener != null) {
                        listener.readError(ex);
                    }
                }
            }
        };

        worker.execute();
    }

    private Buffer readNextBuffer() throws IOException {
        double[] t = new double[bufferSize];
        double[] u = new double[bufferSize];

        int n = 0;
        String line= null;

        while (n < bufferSize && (line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] p = line.split("[,;\\s]+");
            if (p.length < 2) continue;

            t[n] = Double.parseDouble(p[0]);
            u[n] = Double.parseDouble(p[1]);
            n++;
        }

        totalSamplesRead += n;

        if (line == null) {
            eof = true;
        }

        return new Buffer(t, u, n);
    }

    public long getTotalSamplesRead() {
        return totalSamplesRead;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean isEof() {
        return eof;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
