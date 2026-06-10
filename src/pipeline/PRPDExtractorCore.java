package pipeline;

import dsp.Filter;

/**
 *
 * @author jstar
 */
public class PRPDExtractorCore {

    private double f0;
    private double t0;
    private double threshold;
    private double deadUs;
    private Filter filter;
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
    
    public void setT0( double t0 ) {
        this.t0 = t0;
    }
    
    public void setThreshold( double threshold ) {
        this.threshold = threshold;
    }

    private double[] tmpT = new double[0];
    private double[] tmpPhase = new double[0];
    private double[] tmpAmp = new double[0];

    public Pulses extract(Buffer b) {
        int n = b.used;

        if (n < 3) {
            b.release();
            Pulses p = new Pulses(new double[0], new double[0], new double[0], 0);
            p.n = 0;
            return p;
        }

        double fs = estimateFs(b.t, n);
        filter.setFs(fs);

        int deadN = Math.max(1, (int) Math.round(deadUs * 1e-6 * fs));

        if (tmpT.length < n) {
            tmpT = new double[n];
            tmpPhase = new double[n];
            tmpAmp = new double[n];
        }

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
            double y = filtered[i];

            if (Math.abs(y) >= threshold) { // x <->y
                int j0 = i;
                int j1 = Math.min(n, i + deadN);

                int best = j0;
                double bestAbs = Math.abs(filtered[j0]); //Math.abs(b.u[j0] - filtered[j0]);

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(filtered[j]); //Math.abs(b.u[j] - filtered[j]);

                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double tp = b.t[best];
                //double amp = b.u[best] - filtered[best];
                double amp = filtered[best];

                tmpT[count] = tp;
                tmpPhase[count] = phase(tp);
                tmpAmp[count] = amp;
                count++;

                lastT = tp;
                i = j1;
            } else {
                lastT = ti;
                i++;
            }
        }
        
        double[] finalT = new double[count];
        double[] finalPhase = new double[count];
        double[] finalAmp = new double[count];
        System.arraycopy(tmpT, 0, finalT, 0, count);
        System.arraycopy(tmpPhase, 0, finalPhase, 0, count);
        System.arraycopy(tmpAmp, 0, finalAmp, 0, count);
        
        Pulses p = new Pulses(finalT, finalPhase, finalAmp, count);
        p.n = count;
        p.fs = fs;
        
        b.release();

        return p;
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
