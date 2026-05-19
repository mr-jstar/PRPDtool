package pipeline;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

/**
 *
 * @author jstar
 */
public class BinarySocketReader implements Closeable, SignalReader {

    private final Socket socket;

    private final byte[] rawBuffer;
    private final ByteBuffer byteBuffer;

    private final int consumerCount;

    public BinarySocketReader(
            String host,
            int port,
            int consumerCount,
            int maxSamples
    ) throws IOException {

        this.consumerCount = consumerCount;

        this.socket = new Socket(host, port);

        // większe bufory TCP
        socket.setReceiveBufferSize(8 * 1024 * 1024);
        socket.setTcpNoDelay(true);

        // 2 double na próbkę
        this.rawBuffer =
                new byte[maxSamples * 2 * Double.BYTES];

        this.byteBuffer =
                ByteBuffer.wrap(rawBuffer)
                        .order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public Buffer read() throws IOException {

        int targetBytes = rawBuffer.length;

        int off = 0;

        while (off < targetBytes) {

            int n = socket.getInputStream().read(
                    rawBuffer,
                    off,
                    targetBytes - off
            );

            if (n < 0) {

                if (off == 0) {

                    Buffer eofBuf =
                            BufferFactory.acquire(consumerCount);

                    eofBuf.clear();
                    eofBuf.setEOF();

                    return eofBuf;
                }

                throw new EOFException(
                        "Unexpected EOF in middle of chunk"
                );
            }

            off += n;
        }

        int doublesRead = targetBytes / Double.BYTES;
        int samplesRead = doublesRead / 2;

        Buffer buf = BufferFactory.acquire(consumerCount);

        if (buf.size() < samplesRead) {

            int size = buf.size();

            for (int i = 0; i < consumerCount; i++) {
                buf.release();
            }

            throw new IOException(
                    "Buffer too small: max="
                            + size
                            + " samples"
            );
        }

        buf.clear();

        byteBuffer.clear();

        DoubleBuffer db =
                byteBuffer.asDoubleBuffer();

        for (int i = 0; i < samplesRead; i++) {
            buf.t[i] = db.get();
            buf.u[i] = db.get();
        }

        buf.setUsed(samplesRead);

        return buf;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
