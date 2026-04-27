package parallelprpd.v2;

/**
 *
 * @author jstar
 */
public class Pulses {
    public final double[] t;
    public final double[] phase;
    public final double[] amp;
    public final int size;

    public Pulses(double[] t, double[] phase, double[] amp, int size) {
        this.t = t;
        this.phase = phase;
        this.amp = amp;
        this.size = size;
    }
}
