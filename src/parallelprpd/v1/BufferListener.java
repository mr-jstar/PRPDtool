package parallelprpd.v1;

/**
 *
 * @author jstar
 */
public interface BufferListener {
    void bufferReady(PRPDFileReader.Buffer buffer);
    void endOfFile();
    void readError(Exception ex);
}
