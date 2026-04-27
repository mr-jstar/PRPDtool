package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
public interface Filter {
    public double [] filter( double [] signal );
    public void setFs( double fs );
}
