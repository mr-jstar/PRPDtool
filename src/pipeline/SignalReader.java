package pipeline;

import pipeline.Buffer;
import java.io.IOException;

/**
 *
 * @author jstar
 */
public interface SignalReader {
    public Buffer read() throws IOException;
}
