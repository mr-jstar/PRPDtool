package parallelprpd.pipeline;

import prpdtool.DigitalFilters;

/**
 *
 * @author jstar
 */
public class HighPassFilter implements Filter {
    private double q= 0.707;
    private double fc= 1000;
    private double fs= 1_000_000;
    private int order= 4;
    
    public HighPassFilter(double fs, double fc, double q, int order) {
        this.fs = fs;
        this.fc = fc;
        this.q = q;
        this.order = order;
    }
    
    @Override
    public double [] filter( double [] signal ) {
        return DigitalFilters.highpassIIRZeroPhase(signal, fs, fc, q, order);
    }
         
    @Override
    public void setFs(double fs) {
        this.fs= fs;
    }

}
