package dsp;

import java.util.ArrayList;
import pipeline.Buffer;

/**
 *
 * @author jstar
 */
public class PhaseEstimator {

    public static double estimateIntialPhase(Buffer b, double f0) {
        double fs = 1 / (b.t[1] - b.t[0]);
        LowPassFilter filter = new LowPassFilter(fs, 20 * f0, 0.707, 8);
        double[] filtered = filter.filter(b.u, b.used );
        double mag = java.util.Arrays.stream(filtered).max().getAsDouble();
        if( filtered[0] / mag < 1e-6 )
            return 0;
        ArrayList<Double> t0 = new ArrayList<>();
        for (int i = 1; i < b.used; i++) {  // find zero-crossing time instants
            if (filtered[i - 1] * filtered[i] > 0) {
                continue;
            }
            t0.add(b.t[i - 1] + (b.t[i] - b.t[i - 1]) * (0.0 - filtered[i - 1]) / (filtered[i] - filtered[i - 1]));
        }
        if( t0.size() < 2 )
            return 0;
        // calculate half-period
        double T2 = 0;
        for( int i= 1; i < t0.size(); i++ )
            T2 += t0.get(i) - t0.get(i-1);
        T2 /= (t0.size()-1);
        //System.out.println( "u(0)=" + b.u[0] + ", f(0) =" + filtered[0] +"  mag=" + mag + " T2 =" + T2 + "  first t0=" + t0.get(0) + " ph0=" + t0.get(0)/T2 * Math.PI);
        return t0.get(0)/T2 * Math.PI;
    }
}
