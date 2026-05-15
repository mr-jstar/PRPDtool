package pipeline;

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
    private final int consumerCount;

    public BinaryReader(String filename, int consumerCount, int maxSamples) throws IOException {
        this.channel = new FileInputStream(new File(filename)).getChannel();
        this.consumerCount = consumerCount;

        // 2 double na próbkę: t,u
        this.byteBuffer = ByteBuffer.allocateDirect(maxSamples * 2 * Double.BYTES);
        this.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Czyta maksymalnie maxSamples próbek do out.
     *
     * out[i][0] = t out[i][1] = u
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

        Buffer buf = BufferFactory.acquire(consumerCount);
        if (buf.size() < samplesRead) {
            int size = buf.size();
            for( int i= 0; i < consumerCount; i++ )
                buf.release();
            throw new IOException("Buffer is too small: cat read at max " + size + " samples");
        }
        buf.clear();

        DoubleBuffer db = byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();

        for (int i = 0; i < samplesRead; i++) {
            buf.t[i] = db.get();
            buf.u[i] = db.get();
        }
        if (eof) {
            buf.setEOF();
        }
        buf.setUsed(samplesRead);

        return buf;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
