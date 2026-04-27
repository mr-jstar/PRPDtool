package parallelprpd.v2;


/**
 *
 * @author jstar
 */
public interface BufferListener {
    void bufferReady(Buffer buffer);
    void endOfFile();
    void readError(Exception ex);
}
