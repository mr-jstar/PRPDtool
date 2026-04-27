package parallelprpd.v0;

/**
 *
 * @author jstar
 */
import java.io.*;
import java.util.*;

public class PRPDFile implements Closeable {

    public static class Buffer {
        public final double[][] data;   // [n][2] = t,u
        public final int size;
        public final double tStart;
        public final double tEnd;

        public Buffer(double[][] data, int size) {
            this.data = data;
            this.size = size;
            this.tStart = size > 0 ? data[0][0] : Double.NaN;
            this.tEnd   = size > 0 ? data[size - 1][0] : Double.NaN;
        }
    }

    private final String filename;
    private final BufferedReader reader;
    private final int bufferSize;

    private boolean eof = false;
    private long totalSamplesRead = 0;

    public PRPDFile(String filename, int bufferSize) throws IOException {
        this.filename = filename;
        this.bufferSize = bufferSize;
        this.reader = new BufferedReader(new FileReader(filename), 1 << 20);

        // pominięcie nagłówka, jeśli jest
        reader.mark(1024);
        String first = reader.readLine();

        if (first != null) {
            String s = first.trim().toLowerCase(Locale.US);
            if (!(s.startsWith("t") || s.contains("u"))) {
                reader.reset();
            }
        }
    }

    public Buffer readNextBuffer() throws IOException {
        if (eof) {
            return new Buffer(new double[0][2], 0);
        }

        double[][] buf = new double[bufferSize][2];
        int n = 0;

        String line=null;

        while (n < bufferSize && (line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] p = line.split("[,;\\s]+");
            if (p.length < 2) continue;

            buf[n][0] = Double.parseDouble(p[0]);
            buf[n][1] = Double.parseDouble(p[1]);
            n++;
        }

        if (line == null) {
            eof = true;
        }

        totalSamplesRead += n;
        return new Buffer(buf, n);
    }

    public boolean isEof() {
        return eof;
    }

    public long getTotalSamplesRead() {
        return totalSamplesRead;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
