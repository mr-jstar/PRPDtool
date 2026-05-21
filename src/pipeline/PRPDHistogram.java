package pipeline;

import java.awt.Color;
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
    
    public void addLabel( double xc, double yc, double w, double h, String label, Color color );
    
    public boolean removeLabel( String label );
    
    public void clearLabels();
    
    public void drawF0(boolean draw);
    
    public void resize(int w, int h);

    BufferedImage getPRPD(int w, int h);
    
    BufferedImage getImage();

}
