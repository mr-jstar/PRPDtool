package pipeline;

/**
 *
 * @author jstar
 */
public class Pulses {
    public final double[] t;
    public final double[] phase;
    public final double[] amp;
    public int size;
    public int n;
    public double fs;

    public Pulses(double[] t, double[] phase, double[] amp, int size) {
        this.t = t;
        this.phase = phase;
        this.amp = amp;
        this.size = size;
        this.n = 0;
    }
}
