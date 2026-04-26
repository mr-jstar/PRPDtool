package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
public class Buffer {
    public final double[] t;
    public final double[] u;
    public final int size;
    public final boolean eof;

    public Buffer(double[] t, double[] u, int size, boolean eof) {
        this.t = t;
        this.u = u;
        this.size = size;
        this.eof = eof;
    }
}
