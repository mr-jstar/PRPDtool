package prpdtool;

/**
 *
 * @author jstar
 */
public class Sample {

    double s[];

    Sample(double t, double u) {
        s = new double[2];
        s[0] = t;
        s[1] = u;
    }

    Sample(double t, double ph, double amp) {
        s = new double[3];
        s[0] = t;
        s[1] = ph;
        s[1] = amp;
    }

    Sample(double[] s) {
        this.s = s;
    }
}
