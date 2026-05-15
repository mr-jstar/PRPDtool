package dsp;

import dsp.Filter;

/**
 *
 * @author jstar
 */
public class LowPassFilter implements Filter {

    private double q = 0.707;
    private double fc = 1000;
    private double fs = 1_000_000;
    private int order = 4;

    public LowPassFilter(double fs, double fc, double q, int order) {
        this.fs = fs;
        this.fc = fc;
        this.q = q;
        this.order = order;
    }

    @Override
    public double[] filter(double[] signal) {
        return DigitalFilters.lowpassIIRZeroPhase(signal, fs, fc, q, order);
    }

    @Override
    public double[] filter(double[] signal, int n) {
        double[] s = new double[n];
        System.arraycopy(signal, 0, s, 0, n);
        return DigitalFilters.lowpassIIRZeroPhase(s, fs, fc, q, order);
    }

    @Override
    public void setFs(double fs) {
        this.fs = fs;
    }

}
