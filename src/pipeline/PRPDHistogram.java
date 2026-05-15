package pipeline;

import pipeline.Pulses;
import java.awt.image.BufferedImage;

/**
 *
 * @author jstar
 */
public interface PRPDHistogram {

    void reset();

    void addPulses(Pulses p);

    int[][] getHistogram();

    double getMax();

    double getMin();
    
    public double getDataMin();
    
    public double getDataMax();

    BufferedImage getPRPD(int w, int h);

}
