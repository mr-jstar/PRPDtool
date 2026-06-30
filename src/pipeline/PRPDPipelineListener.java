
package pipeline;

/**
 *
 * @author jstar
 */
public interface PRPDPipelineListener {
    default void preExtract(Buffer buffer) {}
    void bufferRead(Buffer buffer);      // dla obwiedni
    void pulsesReady(Pulses pulses);     // dla PRPD
    void finished();
    void error(Throwable ex, String message);
}
