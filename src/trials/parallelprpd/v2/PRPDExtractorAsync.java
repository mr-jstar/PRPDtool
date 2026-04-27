package parallelprpd.v2;

/**
 *
 * @author jstar
 */
import javax.swing.*;

public class PRPDExtractorAsync {

    private final double f0;
    private final double t0;
    private final double threshold;
    private final double deadUs;
    private final double smoothUs;

    private PulseListener listener;

    private boolean busy = false;
    private double lastT = Double.NaN;

    public PRPDExtractorAsync(double f0, double t0,
                              double threshold,
                              double deadUs,
                              double smoothUs) {
        this.f0 = f0;
        this.t0 = t0;
        this.threshold = threshold;
        this.deadUs = deadUs;
        this.smoothUs = smoothUs;
    }

    public void setPulseListener(PulseListener l) {
        this.listener = l;
    }

    public void processAsync(Buffer buf) {
        if (busy) throw new IllegalStateException("busy");

        busy = true;

        new SwingWorker<Pulses, Void>() {
            @Override
            protected Pulses doInBackground() {
                return extract(buf);
            }

            @Override
            protected void done() {
                busy = false;

                try {
                    listener.pulsesReady(get());
                } catch (Exception e) {
                    listener.extractionError(e);
                }
            }
        }.execute();
    }

    private Pulses extract(Buffer b) {
        int n = b.size;
        if (n < 3) return new Pulses(new double[0], new double[0], new double[0], 0);

        double fs = estimateFs(b.t, n);

        int smoothN = Math.max(3, (int)(smoothUs * 1e-6 * fs));
        int deadN   = Math.max(1, (int)(deadUs * 1e-6 * fs));

        double[] bg = movingAverage(b.u, n, smoothN);

        double[] pt = new double[n];
        double[] ph = new double[n];
        double[] pa = new double[n];

        int count = 0;
        int i = 1;

        while (i < n - 1) {
            double t = b.t[i];

            if (!Double.isNaN(lastT) && t <= lastT) {
                i++;
                continue;
            }

            double x = b.u[i] - bg[i];

            if (Math.abs(x) >= threshold) {

                int j0 = i;
                int j1 = Math.min(n, i + deadN);

                int best = j0;
                double bestAbs = Math.abs(b.u[j0] - bg[j0]);

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(b.u[j] - bg[j]);
                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double tp = b.t[best];
                double amp = b.u[best] - bg[best];

                pt[count] = tp;
                pa[count] = amp;
                ph[count] = phase(tp);

                count++;

                lastT = tp;
                i = j1;
            } else {
                lastT = t;
                i++;
            }
        }

        return new Pulses(pt, ph, pa, count);
    }

    private double phase(double t) {
        double p = 360.0 * f0 * (t - t0);
        p %= 360.0;
        if (p < 0) p += 360.0;
        return p;
    }

    private static double estimateFs(double[] t, int n) {
        double sum = 0;
        int c = 0;

        for (int i = 1; i < n; i++) {
            double dt = t[i] - t[i - 1];
            if (dt > 0) {
                sum += dt;
                c++;
            }
        }

        return 1.0 / (sum / c);
    }

    private static double[] movingAverage(double[] x, int n, int w) {
        double[] y = new double[n];
        double[] ps = new double[n + 1];

        for (int i = 0; i < n; i++)
            ps[i + 1] = ps[i] + x[i];

        int h = w / 2;

        for (int i = 0; i < n; i++) {
            int l = Math.max(0, i - h);
            int r = Math.min(n - 1, i + h);

            y[i] = (ps[r + 1] - ps[l]) / (r - l + 1);
        }

        return y;
    }
}
