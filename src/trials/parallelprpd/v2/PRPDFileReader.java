package parallelprpd.v2;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.io.*;
import java.util.Locale;

public class PRPDFileReader {

    private final BufferedReader reader;
    private final int bufferSize;

    private BufferListener listener;

    private boolean eof = false;
    private boolean busy = false;

    public PRPDFileReader(String file, int bufferSize) throws IOException {
        this.bufferSize = bufferSize;
        this.reader = new BufferedReader(new FileReader(file), 1 << 20);

        reader.mark(2048);
        String first = reader.readLine();

        if (first != null) {
            String s = first.trim().toLowerCase(Locale.US);
            if (!(s.startsWith("t") || s.contains("u"))) {
                reader.reset();
            }
        }
    }

    public void setBufferListener(BufferListener l) {
        this.listener = l;
    }

    public void readNextAsync() {
        if (busy || eof) return;

        busy = true;

        new SwingWorker<Buffer, Void>() {
            @Override
            protected Buffer doInBackground() throws Exception {
                return readNext();
            }

            @Override
            protected void done() {
                busy = false;

                try {
                    Buffer b = get();

                    if (b.size == 0) {
                        eof = true;
                        listener.endOfFile();
                    } else {
                        listener.bufferReady(b);
                    }

                } catch (Exception e) {
                    listener.readError(e);
                }
            }
        }.execute();
    }

    private Buffer readNext() throws IOException {
        double[] t = new double[bufferSize];
        double[] u = new double[bufferSize];

        int n = 0;
        String line= null;

        while (n < bufferSize && (line = reader.readLine()) != null) {
            String[] p = line.trim().split("[,;\\s]+");
            if (p.length < 2) continue;

            t[n] = Double.parseDouble(p[0]);
            u[n] = Double.parseDouble(p[1]);
            n++;
        }

        if (line == null) eof = true;

        return new Buffer(t, u, n);
    }
}
