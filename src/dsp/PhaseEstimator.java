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
        double nT = b.t[b.used - 1] * f0;
        if( nT < 10 )
            System.out.println("t/T="+nT+" t0 estimate is uncertain");
        LowPassFilter filter = new LowPassFilter(fs, 20 * f0, 0.707, 8);
        double[] filtered = filter.filter(b.u, b.used);
        double mag = java.util.Arrays.stream(filtered).max().getAsDouble();
        if (filtered[0] / mag < 1e-6) {
            return 0;
        }
        ArrayList<Double> t0p = new ArrayList<>();
        ArrayList<Double> t0m = new ArrayList<>();
        for (int i = 1; i < b.used; i++) {  // find zero-crossing time instants
            if (filtered[i - 1] * filtered[i] > 0) {
                continue;
            }
            double ct0 = b.t[i - 1] + (b.t[i] - b.t[i - 1]) * (0.0 - filtered[i - 1]) / (filtered[i] - filtered[i - 1]);
            if (filtered[i - 1] <= 0.0) {
                t0p.add(ct0);
            } else {
                t0m.add(ct0);
            }
        }
        if (t0p.size() < 2) {
            return 0;
        }
        // calculate period
        double T = 0;
        int n = 0;
        for (int i = 1; i < t0p.size(); i++) {
            T += t0p.get(i) - t0p.get(i - 1);
            n++;
        }
        if (t0m.size() > 1) {
            for (int i = 1; i < t0m.size(); i++) {
                T += t0m.get(i) - t0m.get(i - 1);
                n++;
            }
        }
        T /= n;
        double ph0 = t0p.get(0) / T * 2 * Math.PI;
        ph0 = ph0 > 2*Math.PI ? ph0 - 2*Math.PI : ph0;
        
        //System.out.println( "u(0)=" + b.u[0] + ", f(0) =" + filtered[0] +"  mag=" + mag + " T =" + T);
        //System.out.println( "first t0p=" + t0p.get(0) + "  first t0m=" + t0m.get(0));
        //System.out.println( "ph0=" + ph0);

        return ph0;
    }
}
