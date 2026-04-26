package parallelprpd.v1;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.util.ArrayList;

public class PRPDExtractorAsync {

    private final double f0;
    private final double tPhaseZero;
    private final double threshold;
    private final double deadUs;
    private final double smoothUs;

    private PulseListener listener;

    private boolean busy = false;
    private double lastAnalyzedTime = Double.NaN;

    public PRPDExtractorAsync(
            double f0,
            double tPhaseZero,
            double threshold,
            double deadUs,
            double smoothUs
    ) {
        this.f0 = f0;
        this.tPhaseZero = tPhaseZero;
        this.threshold = threshold;
        this.deadUs = deadUs;
        this.smoothUs = smoothUs;
    }

    public void setPulseListener(PulseListener listener) {
        this.listener = listener;
    }

    public void processAsync(PRPDFileReader.Buffer buffer) {
        if (busy) {
            throw new IllegalStateException("Extractor is busy");
        }

        busy = true;

        SwingWorker<double[][], Void> worker = new SwingWorker<>() {
            @Override
            protected double[][] doInBackground() {
                return extract(buffer);
            }

            @Override
            protected void done() {
                busy = false;

                try {
                    double[][] pulses = get();

                    if (listener != null) {
                        listener.pulsesReady(pulses);
                    }

                } catch (Exception ex) {
                    if (listener != null) {
                        listener.extractionError(ex);
                    }
                }
            }
        };

        worker.execute();
    }

    private double[][] extract(PRPDFileReader.Buffer buffer) {
        int n = buffer.size;

        if (n < 3) {
            return new double[0][3];
        }

        double fs = estimateFs(buffer.t, n);

        int smoothN = Math.max(3, (int)Math.round(smoothUs * 1e-6 * fs));
        int deadN = Math.max(1, (int)Math.round(deadUs * 1e-6 * fs));

        double[] background = movingAverageCentered(buffer.u, n, smoothN);

        ArrayList<double[]> pulses = new ArrayList<>();

        int i = 1;

        while (i < n - 1) {
            double t = buffer.t[i];

            if (!Double.isNaN(lastAnalyzedTime) && t <= lastAnalyzedTime) {
                i++;
                continue;
            }

            double x = buffer.u[i] - background[i];

            if (Math.abs(x) >= threshold) {
                int j0 = i;
                int j1 = Math.min(n, i + deadN);

                int best = j0;
                double bestAbs = Math.abs(buffer.u[j0] - background[j0]);

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(buffer.u[j] - background[j]);
                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double pulseTime = buffer.t[best];
                double amp = buffer.u[best] - background[best];
                double phase = phaseDeg(pulseTime);

                pulses.add(new double[]{pulseTime, phase, amp});

                lastAnalyzedTime = pulseTime;
                i = j1;
            } else {
                lastAnalyzedTime = t;
                i++;
            }
        }

        return pulses.toArray(new double[0][3]);
    }

    private double phaseDeg(double t) {
        double phase = 360.0 * f0 * (t - tPhaseZero);
        phase %= 360.0;
        if (phase < 0.0) phase += 360.0;
        return phase;
    }

    private static double estimateFs(double[] t, int n) {
        double sum = 0.0;
        int cnt = 0;

        for (int i = 1; i < n; i++) {
            double dt = t[i] - t[i - 1];
            if (dt > 0.0) {
                sum += dt;
                cnt++;
            }
        }

        return 1.0 / (sum / cnt);
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

            if (win % 2 == 0) right--;

            if (left < 0) left = 0;
            if (right >= n) right = n - 1;

            y[i] = (ps[right + 1] - ps[left]) / (right - left + 1);
        }

        return y;
    }

    public boolean isBusy() {
        return busy;
    }

    public double getLastAnalyzedTime() {
        return lastAnalyzedTime;
    }
}
