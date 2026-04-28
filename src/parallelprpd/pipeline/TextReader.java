package parallelprpd.pipeline;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/**
 *
 * @author jstar
 */
public class TextReader implements Closeable, SignalReader {
    private final BufferedReader br;
    private final int bufferSize; 
    
    public TextReader( String filename, int bufferSize ) throws IOException {
        this.br = new BufferedReader(new FileReader(filename), 1 << 20);
        this.bufferSize = bufferSize;
        skipHeaderIfPresent();
    }

    @Override
    public void close() throws IOException {
        br.close();
    }

    @Override
    public Buffer read() throws IOException {
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
    
    private final void skipHeaderIfPresent() throws IOException {
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

}
