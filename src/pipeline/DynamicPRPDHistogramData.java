package pipeline;

import java.util.Arrays;

public class DynamicPRPDHistogramData {

    private final double ampMin;
    private final double ampMax;
    
    private double dataMin = Double.POSITIVE_INFINITY;
    private double dataMax = Double.NEGATIVE_INFINITY;

    private double[] phases = new double[1024];
    private double[] amps = new double[1024];
    private double[] times = new double[1024];
    private int size = 0;

    private final boolean bipolar;

    public DynamicPRPDHistogramData(
            int binsPhase, // kept for signature compatibility if needed, but unused for binning
            int binsAmp,
            double ampMin,
            double ampMax,
            boolean bipolar
    ) {
        this.ampMin = ampMin;
        this.ampMax = ampMax;
        this.bipolar = bipolar;
    }

    public void reset() {
        size = 0;
        dataMin = Double.POSITIVE_INFINITY;
        dataMax = Double.NEGATIVE_INFINITY;
    }

    public double getMin() {
        return ampMin;
    }

    public double getMax() {
        return ampMax;
    }

    public void addPulses(Pulses p) {
        ensureCapacity(size + p.n);
        
        for (int i = 0; i < p.n; i++) {
            double phase = p.phase[i];
            double amp = bipolar ? p.amp[i] : Math.abs(p.amp[i]);
            
            if( p.amp[i] < dataMin ) dataMin = p.amp[i];
            if( p.amp[i] > dataMax ) dataMax = p.amp[i];

            phases[size] = phase;
            amps[size] = amp;
            times[size] = p.t[i];
            size++;
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > phases.length) {
            int newCapacity = Math.max(phases.length * 2, minCapacity);
            phases = Arrays.copyOf(phases, newCapacity);
            amps = Arrays.copyOf(amps, newCapacity);
            times = Arrays.copyOf(times, newCapacity);
        }
    }

    public double[] getPhases() {
        return phases;
    }

    public double[] getAmps() {
        return amps;
    }

    public double[] getTimes() {
        return times;
    }

    public int getSize() {
        return size;
    }
    
    public double getDataMin() {
        return dataMin == Double.POSITIVE_INFINITY ? -1 : dataMin;
    }
    
    public double getDataMax() {
        return dataMax == Double.NEGATIVE_INFINITY ? 1 : dataMax;
    }
    
    // Legacy methods to satisfy interface if still used elsewhere, otherwise return dummies
    public int[][] getHistogram() {
        return new int[0][0]; 
    }

    public int getMaxCount() {
        return 1;
    }

    public int getPhaseBins() {
        return 360;
    }

    public int getAmpBins() {
        return 200;
    }

    public int getBin(int ph, int amp) {
        return 0;
    }
}
