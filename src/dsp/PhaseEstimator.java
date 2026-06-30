package dsp;

import java.util.ArrayList;
import pipeline.Buffer;

/**
 *
 * @author jstar
 */
public class PhaseEstimator {

    public static double estimateIntialPhase(Buffer b, double f0) {
        double nT = b.t[b.used - 1] * f0;
        if( nT < 10 )
            System.out.println("t/T="+nT+" t0 estimate is uncertain");
            
        // Calculate the fundamental component of the signal at f0 using DFT
        double re = 0.0;
        double im = 0.0;
        for (int i = 0; i < b.used; i++) {
            double angle = 2 * Math.PI * f0 * b.t[i];
            re += b.u[i] * Math.cos(angle);
            im += b.u[i] * Math.sin(angle);
        }
        
        // Signal can be approximated as A * cos(2*pi*f0*t + phi)
        // DFT gives X = sum(u * e^(-j * 2*pi*f0*t)) = sum(u * cos) - j * sum(u * sin)
        double phi = Math.atan2(-im, re);
        
        // We want the time of the positive-slope zero crossing.
        // For A * cos(wt + phi), positive zero crossing occurs when wt + phi = -pi/2
        double t0 = (-Math.PI / 2.0 - phi) / (2 * Math.PI * f0);
        
        // Wrap t0 to the first period [0, T)
        double T = 1.0 / f0;
        t0 = t0 % T;
        if (t0 < 0) {
            t0 += T;
        }
        
        // Return ph0 as expected by PRPDTool: ph0 = t0 / T * 2 * Math.PI
        double ph0 = t0 / T * 2 * Math.PI;
        
        return ph0;
    }
}
