package parallelprpd.v2;

import parallelprpd.v1.*;

/**
 *
 * @author jstar
 */
public interface PulseListener {
    void pulsesReady(Pulses pulses);
    void extractionError(Exception ex);
}
