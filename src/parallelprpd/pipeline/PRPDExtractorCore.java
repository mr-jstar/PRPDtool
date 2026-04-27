package parallelprpd.pipeline;

import prpdtool.DigitalFilters;

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
        int n = b.size;

        if (n < 3) {
            return new Pulses(new double[0], new double[0], new double[0], 0);
        }

        double fs = estimateFs(b.t, n);
        filter.setFs(fs);

        int deadN = Math.max(1, (int) Math.round(deadUs * 1e-6 * fs));

        double [] filtered = filter.filter(b.u);

        double[] pt = new double[n];
        double[] pp = new double[n];
        double[] pa = new double[n];

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

                pt[count] = tp;
                pp[count] = phase(tp);
                pa[count] = amp;
                count++;

                lastT = tp;
                i = j1;
            } else {
                lastT = ti;
                i++;
            }
        }

        return new Pulses(pt, pp, pa, count);
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

    private static double[] movingAverageCentered(double[] x, int n, int win) {
        double[] y = new double[n];
        double[] ps = new double[n + 1];

        for (int i = 0; i < n; i++) {
            ps[i + 1] = ps[i] + x[i];
        }

        int half = win / 2;

        for (int i = 0; i < n; i++) {
            int left = i - half;
            int right = i + half;

            if (win % 2 == 0) {
                right--;
            }

            if (left < 0) {
                left = 0;
            }

            if (right >= n) {
                right = n - 1;
            }

            y[i] = (ps[right + 1] - ps[left]) / (right - left + 1);
        }

        return y;
    }
}
