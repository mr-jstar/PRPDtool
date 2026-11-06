package dsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Empirical Mode Decomposition (EMD) Denoising Filter for High-Voltage Partial Discharges (IEC 62478).
 * Decomposes non-linear and non-stationary PD pulse signals into Intrinsic Mode Functions (IMFs)
 * using the Huang sifting process with natural cubic spline interpolation.
 * 
 * In partial discharge measurements, high-frequency stochastic noise concentrates heavily in IMF 1,
 * while the physical damped oscillating transient dominates in IMF 2 and IMF 3.
 */
public class EmpiricalModeDecompositionFilter implements Filter {

    public enum DenoisingStrategy {
        REJECT_IMF1("Odrzucenie IMF 1 (Usuń szum w.cz. - zalecane dla WNZ)"),
        SOFT_THRESHOLD_IMFS("Adaptacyjne progowanie modów IMF"),
        KEEP_IMF2_IMF3("Tylko IMF 2 i IMF 3 (Pasmo oscylacji WNZ)");

        private final String displayName;

        DenoisingStrategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }

        @Override
        public String toString() { return displayName; }
    }

    private boolean enabled = true;
    private DenoisingStrategy strategy = DenoisingStrategy.REJECT_IMF1;
    private int maxImfs = 4;
    private int maxSiftingIterations = 6;
    private double fs = 125e6;

    // Diagnostics
    private double lastSnrImprovementDb = 0.0;
    private int lastImfsExtracted = 0;
    private double[] lastImfEnergies = new double[0];

    public EmpiricalModeDecompositionFilter() {
    }

    public EmpiricalModeDecompositionFilter(DenoisingStrategy strategy, int maxImfs) {
        this.strategy = strategy;
        this.maxImfs = Math.max(2, Math.min(6, maxImfs));
    }

    @Override
    public double[] filter(double[] signal) {
        if (signal == null) return new double[0];
        return filter(signal, signal.length);
    }

    @Override
    public double[] filter(double[] signal, int n) {
        if (signal == null || n <= 0) return new double[0];
        int len = Math.min(signal.length, n);
        if (!enabled || len < 32) {
            return Arrays.copyOf(signal, len);
        }

        // To maintain real-time responsiveness (<30ms) on very large Red Pitaya buffers (e.g. 50k+ pts),
        // we process in sliding/segmented windows if len > 4096, or directly on pulse transients.
        if (len <= 4096) {
            return processBlock(signal, len);
        }

        int blockSize = 2048;
        int hopSize = 1024;
        double[] output = new double[len];
        double[] weights = new double[len];

        for (int start = 0; start < len; start += hopSize) {
            int curBlockLen = Math.min(blockSize, len - start);
            double[] block = new double[curBlockLen];
            System.arraycopy(signal, start, block, 0, curBlockLen);

            double[] filteredBlock = processBlock(block, curBlockLen);

            // Overlap-add with triangular window
            for (int i = 0; i < curBlockLen; i++) {
                int outIdx = start + i;
                if (outIdx < len) {
                    double w = 1.0 - Math.abs((2.0 * i / (curBlockLen - 1)) - 1.0);
                    output[outIdx] += filteredBlock[i] * w;
                    weights[outIdx] += w;
                }
            }
        }

        for (int i = 0; i < len; i++) {
            if (weights[i] > 1e-6) {
                output[i] /= weights[i];
            } else {
                output[i] = signal[i];
            }
        }

        return output;
    }

    private double[] processBlock(double[] input, int len) {
        double[] residual = Arrays.copyOf(input, len);
        List<double[]> imfs = new ArrayList<>();

        for (int imfIdx = 0; imfIdx < maxImfs; imfIdx++) {
            double[] h = Arrays.copyOf(residual, len);
            boolean isImfValid = false;

            for (int iter = 0; iter < maxSiftingIterations; iter++) {
                // Find local extrema
                List<Integer> maxIndices = new ArrayList<>();
                List<Integer> minIndices = new ArrayList<>();

                for (int i = 1; i < len - 1; i++) {
                    if (h[i] > h[i - 1] && h[i] > h[i + 1]) maxIndices.add(i);
                    else if (h[i] < h[i - 1] && h[i] < h[i + 1]) minIndices.add(i);
                }

                if (maxIndices.size() < 2 || minIndices.size() < 2) {
                    break; // Cannot construct spline envelopes
                }

                // Boundary extension for upper and lower envelopes
                double[] upperEnv = cubicSplineEnvelope(maxIndices, h, len, true);
                double[] lowerEnv = cubicSplineEnvelope(minIndices, h, len, false);

                // Compute mean envelope and subtract
                double meanDiffSum = 0.0;
                for (int i = 0; i < len; i++) {
                    double mean = 0.5 * (upperEnv[i] + lowerEnv[i]);
                    h[i] -= mean;
                    meanDiffSum += Math.abs(mean);
                }

                isImfValid = true;
                if (meanDiffSum / len < 1e-5) {
                    break; // Converged
                }
            }

            if (!isImfValid) {
                break;
            }

            imfs.add(h);
            for (int i = 0; i < len; i++) {
                residual[i] -= h[i];
            }
        }

        this.lastImfsExtracted = imfs.size();
        this.lastImfEnergies = new double[imfs.size()];
        for (int k = 0; k < imfs.size(); k++) {
            double sumSq = 0.0;
            double[] imfK = imfs.get(k);
            for (double val : imfK) sumSq += val * val;
            lastImfEnergies[k] = sumSq / len;
        }

        // Reconstruct according to strategy
        double[] out = new double[len];

        switch (strategy) {
            case REJECT_IMF1:
                // Sum IMF 2, IMF 3... + residual (rejecting IMF 1 noise)
                for (int k = 1; k < imfs.size(); k++) {
                    double[] imfK = imfs.get(k);
                    for (int i = 0; i < len; i++) out[i] += imfK[i];
                }
                for (int i = 0; i < len; i++) out[i] += residual[i];
                break;

            case KEEP_IMF2_IMF3:
                // Only IMF 2 and IMF 3
                for (int k = 1; k < Math.min(3, imfs.size()); k++) {
                    double[] imfK = imfs.get(k);
                    for (int i = 0; i < len; i++) out[i] += imfK[i];
                }
                break;

            case SOFT_THRESHOLD_IMFS:
            default:
                // Threshold IMF 1 heavily, IMF 2 moderately
                for (int k = 0; k < imfs.size(); k++) {
                    double[] imfK = imfs.get(k);
                    double thresh = (k == 0) ? 0.05 * maxAbs(imfK) : (0.01 * maxAbs(imfK));
                    for (int i = 0; i < len; i++) {
                        double v = imfK[i];
                        if (Math.abs(v) > thresh) {
                            out[i] += Math.signum(v) * (Math.abs(v) - thresh);
                        }
                    }
                }
                for (int i = 0; i < len; i++) out[i] += residual[i];
                break;
        }

        // Calculate SNR gain
        double pIn = 0.0, pDiff = 0.0;
        for (int i = 0; i < len; i++) {
            pIn += input[i] * input[i];
            double d = input[i] - out[i];
            pDiff += d * d;
        }
        if (pDiff > 1e-12 && pIn > 1e-12) {
            this.lastSnrImprovementDb = 10.0 * Math.log10(pIn / pDiff);
        } else {
            this.lastSnrImprovementDb = 0.0;
        }

        return out;
    }

    private static double maxAbs(double[] arr) {
        double max = 0.0;
        for (double v : arr) {
            double a = Math.abs(v);
            if (a > max) max = a;
        }
        return max;
    }

    /**
     * Natural Cubic Spline Interpolation for EMD Extrema Envelopes.
     * Uses Thomas algorithm to solve tridiagonal system in O(N) linear time.
     */
    private double[] cubicSplineEnvelope(List<Integer> extremaIdx, double[] signal, int len, boolean isMax) {
        int m = extremaIdx.size();
        int nKnots = m + 2;
        double[] xK = new double[nKnots];
        double[] yK = new double[nKnots];

        // Boundary reflection
        xK[0] = 0;
        yK[0] = signal[extremaIdx.get(0)];

        for (int i = 0; i < m; i++) {
            int idx = extremaIdx.get(i);
            xK[i + 1] = idx;
            yK[i + 1] = signal[idx];
        }

        xK[nKnots - 1] = len - 1;
        yK[nKnots - 1] = signal[extremaIdx.get(m - 1)];

        // Compute spline coefficients (a + b*dx + c*dx^2 + d*dx^3)
        double[] h = new double[nKnots - 1];
        for (int i = 0; i < nKnots - 1; i++) {
            h[i] = Math.max(1.0, xK[i + 1] - xK[i]);
        }

        double[] alpha = new double[nKnots];
        for (int i = 1; i < nKnots - 1; i++) {
            alpha[i] = (3.0 / h[i]) * (yK[i + 1] - yK[i]) - (3.0 / h[i - 1]) * (yK[i] - yK[i - 1]);
        }

        double[] l = new double[nKnots];
        double[] mu = new double[nKnots];
        double[] z = new double[nKnots];
        l[0] = 1.0;

        for (int i = 1; i < nKnots - 1; i++) {
            l[i] = 2.0 * (xK[i + 1] - xK[i - 1]) - h[i - 1] * mu[i - 1];
            mu[i] = h[i] / l[i];
            z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i];
        }
        l[nKnots - 1] = 1.0;

        double[] c = new double[nKnots];
        double[] b = new double[nKnots - 1];
        double[] d = new double[nKnots - 1];

        for (int j = nKnots - 2; j >= 0; j--) {
            c[j] = z[j] - mu[j] * c[j + 1];
            b[j] = (yK[j + 1] - yK[j]) / h[j] - h[j] * (c[j + 1] + 2.0 * c[j]) / 3.0;
            d[j] = (c[j + 1] - c[j]) / (3.0 * h[j]);
        }

        // Interpolate across whole range [0, len-1]
        double[] env = new double[len];
        int segment = 0;
        for (int i = 0; i < len; i++) {
            while (segment < nKnots - 2 && i > xK[segment + 1]) {
                segment++;
            }
            double dx = i - xK[segment];
            env[i] = yK[segment] + b[segment] * dx + c[segment] * dx * dx + d[segment] * dx * dx * dx;
        }

        return env;
    }

    @Override
    public void setFs(double fs) {
        this.fs = fs;
    }

    public double getFs() { return fs; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public DenoisingStrategy getStrategy() { return strategy; }
    public void setStrategy(DenoisingStrategy strategy) { this.strategy = strategy; }

    public int getMaxImfs() { return maxImfs; }
    public void setMaxImfs(int maxImfs) { this.maxImfs = Math.max(2, Math.min(6, maxImfs)); }

    public int getMaxSiftingIterations() { return maxSiftingIterations; }
    public void setMaxSiftingIterations(int iters) { this.maxSiftingIterations = Math.max(2, Math.min(15, iters)); }

    public double getLastSnrImprovementDb() { return lastSnrImprovementDb; }
    public int getLastImfsExtracted() { return lastImfsExtracted; }
    public double[] getLastImfEnergies() { return lastImfEnergies; }
}
