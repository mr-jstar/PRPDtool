package parallelprpd.v1;

/**
 *
 * @author jstar
 */
public interface PulseListener {
    void pulsesReady(double[][] pulses);
    void extractionError(Exception ex);
}
