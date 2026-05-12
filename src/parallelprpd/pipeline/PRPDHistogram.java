package parallelprpd.pipeline;

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

    BufferedImage getPRPD(int w, int h);
    
}
