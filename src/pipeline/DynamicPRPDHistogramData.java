package pipeline;

/**
 *
 * @author jstar
 */
import java.util.Arrays;
import pipeline.Pulses;

public class DynamicPRPDHistogramData  {

    private final int binsPhase;
    private final int binsAmp;

    private final double ampMin;
    private final double ampMax;
    
    private double dataMin = Double.POSITIVE_INFINITY;
    private double dataMax = Double.NEGATIVE_INFINITY;

    private final int[][] hist;
    private int maxCount = 0;

    private final boolean bipolar;

    public DynamicPRPDHistogramData(
            int binsPhase,
            int binsAmp,
            double ampMin,
            double ampMax,
            boolean bipolar
    ) {
        this.binsPhase = binsPhase;
        this.binsAmp = binsAmp;
        this.ampMin = ampMin;
        this.ampMax = ampMax;
        this.bipolar = bipolar;

        this.hist = new int[binsPhase][binsAmp];
    }

    public void reset() {
        for (int i = 0; i < binsPhase; i++) {
            Arrays.fill(hist[i], 0);
        }
    }

    public double getMin() {
        return ampMin;
    }

    public double getMax() {
        return ampMax;
    }

    public int[][] getHistogram() {
        return hist;
    }

    public void addPulses(Pulses p) {

        for (int i = 0; i < p.n; i++) {

            double phase = p.phase[i];
            double amp = bipolar ? p.amp[i] : Math.abs(p.amp[i]);
            
            if( p.amp[i] < dataMin ) dataMin = p.amp[i];
            if( p.amp[i] > dataMax ) dataMax = p.amp[i];

            int xb = (int) (phase / 360.0 * binsPhase);
            int yb = (int) ((amp - ampMin) / (ampMax - ampMin) * binsAmp);

            if (xb < 0 || xb >= binsPhase) {
                continue;
            }
            if (yb < 0 || yb >= binsAmp) {
                continue;
            }

            hist[xb][yb]++;
            if (hist[xb][yb] > maxCount) {
                maxCount = hist[xb][yb];
            }
        }
    }

    public int getMaxCount() {
        return maxCount;
    }

    public int getPhaseBins() {
        return binsPhase;
    }

    public int getAmpBins() {
        return binsAmp;
    }

    public int getBin(int ph, int amp) {
        return hist[ph][amp];
    }
    
    public double getDataMin() {
        return dataMin == Double.POSITIVE_INFINITY ? -1 : dataMin;
    }
    
    public double getDataMax() {
        return dataMax == Double.NEGATIVE_INFINITY ? 1 : dataMax;
    }
}
