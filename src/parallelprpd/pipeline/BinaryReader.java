package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;

public class BinaryReader implements Closeable, SignalReader {

    private final FileChannel channel;
    private final ByteBuffer byteBuffer;
    private final DoubleBuffer doubleBuffer;

    public BinaryReader(String filename, int maxSamples) throws IOException {
        this.channel = new FileInputStream(new File(filename)).getChannel();

        // 2 double na próbkę: t,u
        this.byteBuffer = ByteBuffer.allocateDirect(maxSamples * 2 * Double.BYTES);
        this.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        this.doubleBuffer = byteBuffer.asDoubleBuffer();
    }

    /**
     * Czyta maksymalnie maxSamples próbek do out.
     *
     * out[i][0] = t
     * out[i][1] = u
     *
     * Zwraca liczbę przeczytanych próbek albo -1 przy EOF.
     */
    @Override
    public Buffer read() throws IOException {
        byteBuffer.clear();

        boolean eof = false;

        int bytesRead = channel.read(byteBuffer);

        if (bytesRead < 0) {
            eof = true;
        }

        byteBuffer.flip();

        int doublesRead = byteBuffer.remaining() / Double.BYTES;
        int samplesRead = doublesRead / 2;
        
        double[] t = new double[samplesRead];
        double[] u = new double[samplesRead];

        DoubleBuffer db = byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();

        for (int i = 0; i < samplesRead; i++) {
            t[i] = db.get();
            u[i] = db.get();
        }

        return new Buffer(t, u, samplesRead, eof);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
