package parallelprpd.v2;

/**
 *
 * @author jstar
 */
public class Buffer {
    public final double[] t;
    public final double[] u;
    public final int size;

    public final double tStart;
    public final double tEnd;

    public Buffer(double[] t, double[] u, int size) {
        this.t = t;
        this.u = u;
        this.size = size;

        this.tStart = size > 0 ? t[0] : Double.NaN;
        this.tEnd   = size > 0 ? t[size - 1] : Double.NaN;
    }
}
