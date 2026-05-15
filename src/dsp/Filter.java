package dsp;

/**
 *
 * @author jstar
 */
public interface Filter {
    public double [] filter( double [] signal );
    public double [] filter( double [] signal, int n );
    public void setFs( double fs );
}
