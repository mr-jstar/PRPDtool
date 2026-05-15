package pipeline;

import pipeline.SignalReader;
import pipeline.Buffer;
import pipeline.BufferFactory;
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
    private final int consumerCount;

    public TextReader(String filename, int consumerCount, int bufferSize) throws IOException {
        this.br = new BufferedReader(new FileReader(filename), 1 << 20);
        this.consumerCount = consumerCount;
        this.bufferSize = bufferSize;
        skipHeaderIfPresent();
    }

    @Override
    public void close() throws IOException {
        br.close();
    }

    @Override
    public Buffer read() throws IOException {
        Buffer buf = BufferFactory.acquire(consumerCount);
        if (buf.size() < bufferSize) {
            int size = buf.size();
            for( int i= 0; i < consumerCount; i++ )
                buf.release();
            throw new IOException("Buffer is too small: cat read at max " + size + " samples");
        }

        int n = 0;
        boolean eof = false;

        while (n < buf.size()) {
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

            buf.t[n] = Double.parseDouble(p[0]);
            buf.u[n] = Double.parseDouble(p[1]);
            n++;
        }
        if( eof )
            buf.setEOF();
        buf.setUsed(n);

        return buf;
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
