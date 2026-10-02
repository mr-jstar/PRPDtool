package dsp;

import java.util.ArrayList;
import pipeline.Buffer;

/**
 *
 * @author jstar
 */
public class PhaseEstimator {

    public static double estimateIntialPhase(Buffer b, double f0) {
        return estimateIntialPhase(b, f0, false); // wsteczna kompatybilność
    }

    public static double estimateIntialPhase(Buffer b, double f0, boolean useHardwareReference) {
        double nT = b.t[b.used - 1] * f0;
        if( nT < 10 )
            System.out.println("t/T="+nT+" t0 estimate is uncertain");
            
        // Wybieramy z którego strumienia próbek liczymy fazę.
        // Sprawdzamy też czy u2 faktycznie zawiera sygnał (nie same zera),
        // bo przy nagrywaniu tylko 1 kanału u2 będzie zerowe.
        double[] signalToUse = b.u;
        if (useHardwareReference && b.u2 != null) {
            double maxAbs = 0.0;
            for (int i = 0; i < b.used; i++) {
                double a = Math.abs(b.u2[i]);
                if (a > maxAbs) maxAbs = a;
            }
            if (maxAbs > 0.0) {
                signalToUse = b.u2;
            } else {
                System.out.println("WARNING: HW Phase Ref (CH2) selected but u2 is all zeros — falling back to CH1. Is CH2 connected and IN1+IN2 mode selected?");
            }
        }
            
        // To prevent spectral leakage, compute DFT over an EXACT integer number of periods
        double duration = b.t[b.used - 1] - b.t[0];
        double T_period = 1.0 / f0;
        int periods = (int) (duration / T_period);
        int limit = b.used;
        if (periods > 0) {
            double exactDuration = periods * T_period;
            for (int i = 0; i < b.used; i++) {
                if (b.t[i] - b.t[0] > exactDuration) {
                    limit = i;
                    break;
                }
            }
        }
        
        // Calculate the fundamental component of the signal at f0 using DFT
        double re = 0.0;
        double im = 0.0;
        for (int i = 0; i < limit; i++) {
            double angle = 2 * Math.PI * f0 * b.t[i];
            re += signalToUse[i] * Math.cos(angle);
            im += signalToUse[i] * Math.sin(angle);
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
