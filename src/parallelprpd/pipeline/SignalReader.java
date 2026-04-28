package parallelprpd.pipeline;

import java.io.IOException;

/**
 *
 * @author jstar
 */
public interface SignalReader {
    public Buffer read() throws IOException;
}
