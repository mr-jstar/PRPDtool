package pipeline;

import dsp.Filter;

/**
 *
 * @author jstar
 */
public class PRPDExtractorCore {

    private final double f0;
    private final double t0;
    private final double threshold;
    private final double deadUs;
    private final Filter filter;
    private Pulses pulses;

    private double lastT = Double.NaN;

    public PRPDExtractorCore(
            double f0,
            double t0,
            double threshold,
            double deadUs,
            Filter filter
    ) {
        this.f0 = f0;
        this.t0 = t0;
        this.threshold = threshold;
        this.deadUs = deadUs;
        this.filter = filter;
    }

    public Pulses extract(Buffer b) {
        int n = b.used;
        
        if( pulses == null || pulses.n < n ) {
            pulses = new Pulses(new double[n], new double[n], new double[n], n);
        }

        if (n < 3) {
            b.release();
            pulses.n = 0;
            return pulses;
        }

        double fs = estimateFs(b.t, n);
        filter.setFs(fs);
        pulses.fs = fs;

        int deadN = Math.max(1, (int) Math.round(deadUs * 1e-6 * fs));

        double [] filtered = filter.filter(b.u, b.used);

        int count = 0;
        int i = 1;

        while (i < n - 1) {
            double ti = b.t[i];

            if (!Double.isNaN(lastT) && ti <= lastT) {
                i++;
                continue;
            }

            double x = b.u[i] - filtered[i];

            if (Math.abs(x) >= threshold) {
                int j0 = i;
                int j1 = Math.min(n, i + deadN);

                int best = j0;
                double bestAbs = Math.abs(b.u[j0] - filtered[j0]);

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(b.u[j] - filtered[j]);

                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double tp = b.t[best];
                //double amp = b.u[best] - filtered[best];
                double amp = filtered[best];

                pulses.t[count] = tp;
                pulses.phase[count] = phase(tp);
                pulses.amp[count] = amp;
                count++;

                lastT = tp;
                i = j1;
            } else {
                lastT = ti;
                i++;
            }
        }
        pulses.n = count;
        b.release();

        return pulses;
    }

    private double phase(double t) {
        double p = 360.0 * f0 * (t - t0);
        p %= 360.0;

        if (p < 0.0) {
            p += 360.0;
        }

        return p;
    }

    private static double estimateFs(double[] t, int n) {
        double sum = 0.0;
        int count = 0;

        for (int i = 1; i < n; i++) {
            double dt = t[i] - t[i - 1];

            if (dt > 0.0) {
                sum += dt;
                count++;
            }
        }

        return 1.0 / (sum / count);
    }
}
