package pipeline;

import pipeline.BufferFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author jstar
 */
public class Buffer {

    public final double[] t;
    public final double[] u;
    private final int size;
    public int used;
    public boolean eof;

    private final AtomicInteger refs = new AtomicInteger(0);

    public Buffer(double[] t, double[] u, int size, boolean eof) {
        this.t = t;
        this.u = u;
        this.size = size;
        this.eof = eof;
        this.used = 0;
    }

    public Buffer(int size) {
        this.t = new double[size];
        this.u = new double[size];
        this.size = size;
        this.eof = false;
        this.used = 0;
    }

    public void resetForUse(int consumerCount) {
        this.eof = false;
        this.used = 0;
        this.refs.set(consumerCount);
        //System.out.println( this + ": buffer's counter set to " + consumerCount);
    }

    public void release() {
        int r = refs.decrementAndGet();
        //System.out.println(this + ": buffer released, r= " + refs.get());
        if (r == 0) {
            //System.out.println(this + ": returned to pool");
            BufferFactory.returnToPool(this);
        } else if (r < 0) {
           // throw new IllegalStateException("Buffer released too many times");
        }
    }

    public int size() {
        return size;
    }

    public void clear() {
        this.eof = false;
        this.used = 0;
    }

    public void setEOF() {
        this.eof = true;
    }

    public void setUsed(int n) {
        this.used = n;
    }

}
