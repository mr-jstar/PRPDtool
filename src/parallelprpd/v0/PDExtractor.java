package parallelprpd.v0;

/**
 *
 * @author jstar
 */
import parallelprpd.v0.PRPDFile;
import java.util.*;

public class PDExtractor {

    private final double f0;
    private final double tPhaseZero;

    private final double threshold;
    private final double deadTimeSec;
    private final double smoothTimeSec;

    private double lastAnalyzedTime = Double.NaN;

    public PDExtractor(
            double f0,
            double tPhaseZero,
            double threshold,
            double deadUs,
            double smoothUs
    ) {
        this.f0 = f0;
        this.tPhaseZero = tPhaseZero;
        this.threshold = threshold;
        this.deadTimeSec = deadUs * 1e-6;
        this.smoothTimeSec = smoothUs * 1e-6;
    }

    public double[][] extract(PRPDFile.Buffer buffer) {
        if (buffer == null || buffer.size < 3) {
            return new double[0][3];
        }

        int n = buffer.size;
        double[][] data = buffer.data;

        double fs = estimateFs(data, n);

        int smoothN = Math.max(3, (int)Math.round(smoothTimeSec * fs));
        int deadN = Math.max(1, (int)Math.round(deadTimeSec * fs));

        double[] u = new double[n];
        for (int i = 0; i < n; i++) {
            u[i] = data[i][1];
        }

        double[] background = movingAverageCentered(u, smoothN);

        ArrayList<double[]> pulses = new ArrayList<>();

        int i = 1;

        while (i < n - 1) {
            double t = data[i][0];

            if (!Double.isNaN(lastAnalyzedTime) && t <= lastAnalyzedTime) {
                i++;
                continue;
            }

            double x = u[i] - background[i];

            if (Math.abs(x) >= threshold) {
                int j0 = i;
                int j1 = Math.min(n, i + deadN);

                int best = j0;
                double bestAbs = Math.abs(u[j0] - background[j0]);

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(u[j] - background[j]);
                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double pulseTime = data[best][0];
                double amp = u[best] - background[best];
                double phase = phaseDeg(pulseTime);

                pulses.add(new double[]{pulseTime, phase, amp});

                lastAnalyzedTime = pulseTime;
                i = j1;
            } else {
                lastAnalyzedTime = t;
                i++;
            }
        }

        double[][] out = new double[pulses.size()][3];
        for (int k = 0; k < pulses.size(); k++) {
            out[k] = pulses.get(k);
        }

        return out;
    }

    public double getLastAnalyzedTime() {
        return lastAnalyzedTime;
    }

    public double phaseDeg(double t) {
        double phase = 360.0 * f0 * (t - tPhaseZero);
        phase %= 360.0;
        if (phase < 0.0) phase += 360.0;
        return phase;
    }

    private static double estimateFs(double[][] data, int n) {
        double sum = 0.0;
        int cnt = 0;

        for (int i = 1; i < n; i++) {
            double dt = data[i][0] - data[i - 1][0];
            if (dt > 0.0) {
                sum += dt;
                cnt++;
            }
        }

        if (cnt == 0) {
            throw new IllegalArgumentException("Cannot estimate sampling frequency");
        }

        return 1.0 / (sum / cnt);
    }

    private static double[] movingAverageCentered(double[] x, int win) {
        int n = x.length;
        double[] y = new double[n];

        if (win <= 1) {
            System.arraycopy(x, 0, y, 0, n);
            return y;
        }

        double[] ps = new double[n + 1];

        for (int i = 0; i < n; i++) {
            ps[i + 1] = ps[i] + x[i];
        }

        int half = win / 2;

        for (int i = 0; i < n; i++) {
            int left = i - half;
            int right = i + half;

            if (win % 2 == 0) {
                right -= 1;
            }

            if (left < 0) left = 0;
            if (right >= n) right = n - 1;

            double sum = ps[right + 1] - ps[left];
            y[i] = sum / (right - left + 1);
        }

        return y;
    }
}
