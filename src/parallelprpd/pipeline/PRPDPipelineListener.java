
package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
public interface PRPDPipelineListener {
    void bufferRead(Buffer buffer);      // dla obwiedni
    void pulsesReady(Pulses pulses);     // dla PRPD
    void finished();
    void error(Exception ex);
}
