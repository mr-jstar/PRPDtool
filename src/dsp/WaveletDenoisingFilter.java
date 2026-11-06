package dsp;

import java.util.Arrays;

/**
 * Discrete Wavelet Transform (DWT) Denoising Filter for High-Voltage Partial Discharges (IEC 62478).
 * Implements Mallat's multi-resolution pyramidal algorithm with Donoho-Johnstone VisuShrink
 * adaptive thresholding and median absolute deviation (MAD) noise estimation.
 * 
 * Wavelet decomposition is uniquely suited for PD transients because orthogonal wavelets
 * (especially Daubechies db4) closely match the physical damped oscillatory impulse waveform.
 */
public class WaveletDenoisingFilter implements Filter {

    public enum WaveletType {
        DB4("Daubechies db4 (Zalecana dla WNZ - IEC 62478)", 8),
        DB2("Daubechies db2 (Szybka, 4 współczynniki)", 4),
        HAAR("Haar / db1 (Schodkowa)", 2);

        private final String displayName;
        private final int length;

        WaveletType(String displayName, int length) {
            this.displayName = displayName;
            this.length = length;
        }

        public String getDisplayName() { return displayName; }
        public int getLength() { return length; }

        @Override
        public String toString() { return displayName; }
    }

    public enum ThresholdMode {
        SOFT("Miękkie (Soft Thresholding - zalecane)"),
        HARD("Twarde (Hard Thresholding)");

        private final String displayName;

        ThresholdMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }

        @Override
        public String toString() { return displayName; }
    }

    // Standard orthonormal Daubechies 4 scaling filter coefficients (sum h = sqrt(2), sum h^2 = 1)
    private static final double[] H_DB4 = {
        -0.010597401785069032,
         0.0328830116668852,
         0.030841381835560764,
        -0.18703481171909309,
        -0.027983769416859854,
         0.6308807679298589,
         0.7148465705529157,
         0.2303778133088965
    };

    // Standard orthonormal Daubechies 2 scaling filter coefficients (sum h = sqrt(2), sum h^2 = 1)
    private static final double[] H_DB2 = {
        0.4829629131445341,
        0.8365163037378077,
        0.2241438680420134,
       -0.1294095225512603
    };

    // Haar scaling filter
    private static final double[] H_HAAR = {
        0.7071067811865475,
        0.7071067811865475
    };

    private boolean enabled = true;
    private WaveletType waveletType = WaveletType.DB4;
    private ThresholdMode thresholdMode = ThresholdMode.SOFT;
    private int decompositionLevels = 4;
    private double thresholdMultiplier = 1.0;
    private double fs = 125e6; // Default 125 MS/s

    // Diagnostic metrics from last run
    private double lastNoiseSigma = 0.0;
    private double lastThreshold = 0.0;
    private double lastZeroedPercent = 0.0;
    private double lastSnrImprovementDb = 0.0;

    public WaveletDenoisingFilter() {
    }

    public WaveletDenoisingFilter(WaveletType waveletType, ThresholdMode mode, int levels) {
        this.waveletType = waveletType;
        this.thresholdMode = mode;
        this.decompositionLevels = Math.max(1, Math.min(8, levels));
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
        if (!enabled || len < 16) {
            return Arrays.copyOf(signal, len);
        }

        // Get filter coefficients
        double[] h = getLowPassFilter(waveletType);
        int filterLen = h.length;
        double[] g = new double[filterLen];
        for (int k = 0; k < filterLen; k++) {
            g[k] = ((k % 2 == 1) ? -1.0 : 1.0) * h[filterLen - 1 - k];
        }

        // Pad signal to power of 2 for clean multi-level Mallat decomposition
        int paddedLen = 1;
        while (paddedLen < len) {
            paddedLen <<= 1;
        }
        // At least 2^decompositionLevels
        int minPadded = 1 << decompositionLevels;
        if (paddedLen < minPadded) paddedLen = minPadded;

        double[] padded = new double[paddedLen];
        System.arraycopy(signal, 0, padded, 0, len);
        // Symmetric boundary reflection
        for (int i = len; i < paddedLen; i++) {
            int refIdx = len - 1 - (i - len) % len;
            padded[i] = signal[Math.max(0, refIdx)];
        }

        // Multi-level DWT decomposition
        int maxL = Math.min(decompositionLevels, Integer.numberOfTrailingZeros(paddedLen) - 1);
        if (maxL < 1) maxL = 1;

        double[][] details = new double[maxL][];
        double[] currentApprox = Arrays.copyOf(padded, paddedLen);

        for (int level = 0; level < maxL; level++) {
            int currLen = currentApprox.length;
            int halfLen = currLen / 2;
            double[] nextApprox = new double[halfLen];
            double[] nextDetail = new double[halfLen];

            for (int i = 0; i < halfLen; i++) {
                double aSum = 0.0;
                double dSum = 0.0;
                for (int k = 0; k < filterLen; k++) {
                    int sIdx = (2 * i + k) % currLen;
                    aSum += h[k] * currentApprox[sIdx];
                    dSum += g[k] * currentApprox[sIdx];
                }
                nextApprox[i] = aSum;
                nextDetail[i] = dSum;
            }
            details[level] = nextDetail;
            currentApprox = nextApprox;
        }

        // Estimate noise standard deviation sigma from detail level 1 (highest frequency) using MAD:
        // sigma_hat = median(|d_1|) / 0.6745
        double[] d1 = details[0];
        double[] absD1 = new double[d1.length];
        for (int i = 0; i < d1.length; i++) {
            absD1[i] = Math.abs(d1[i]);
        }
        Arrays.sort(absD1);
        double median = (absD1.length % 2 == 0)
                ? 0.5 * (absD1[absD1.length / 2 - 1] + absD1[absD1.length / 2])
                : absD1[absD1.length / 2];
        double sigma = median / 0.6745;
        this.lastNoiseSigma = sigma;

        // Universal VisuShrink Threshold: lambda = sigma * sqrt(2 * ln(N)) * multiplier
        double lambda = sigma * Math.sqrt(2.0 * Math.log(Math.max(2, paddedLen))) * thresholdMultiplier;
        this.lastThreshold = lambda;

        // Apply thresholding to detail coefficients
        long totalCoeffs = 0;
        long zeroedCoeffs = 0;

        for (int level = 0; level < maxL; level++) {
            double[] d = details[level];
            // Level-dependent scale: slight relaxation for deeper levels to retain pulse energy
            double levelLambda = lambda / Math.sqrt(level + 1);

            for (int i = 0; i < d.length; i++) {
                totalCoeffs++;
                double val = d[i];
                double absVal = Math.abs(val);

                if (absVal < levelLambda) {
                    d[i] = 0.0;
                    zeroedCoeffs++;
                } else if (thresholdMode == ThresholdMode.SOFT) {
                    d[i] = Math.signum(val) * (absVal - levelLambda);
                }
            }
        }
        this.lastZeroedPercent = totalCoeffs > 0 ? (100.0 * zeroedCoeffs / totalCoeffs) : 0.0;

        // Multi-level IDWT Reconstruction
        for (int level = maxL - 1; level >= 0; level--) {
            double[] d = details[level];
            int halfLen = d.length;
            int fullLen = halfLen * 2;
            double[] rec = new double[fullLen];

            for (int i = 0; i < halfLen; i++) {
                double aVal = currentApprox[i];
                double dVal = d[i];
                for (int k = 0; k < filterLen; k++) {
                    int sIdx = (2 * i + k) % fullLen;
                    rec[sIdx] += h[k] * aVal + g[k] * dVal;
                }
            }
            currentApprox = rec;
        }

        // Output truncated to original length
        double[] output = new double[len];
        System.arraycopy(currentApprox, 0, output, 0, len);

        // Estimate SNR improvement
        double powerOrig = 0.0;
        double powerDiff = 0.0;
        for (int i = 0; i < len; i++) {
            powerOrig += signal[i] * signal[i];
            double diff = signal[i] - output[i];
            powerDiff += diff * diff;
        }
        if (powerDiff > 1e-12 && powerOrig > 1e-12) {
            this.lastSnrImprovementDb = 10.0 * Math.log10(powerOrig / powerDiff);
        } else {
            this.lastSnrImprovementDb = 0.0;
        }

        return output;
    }

    private static double[] getLowPassFilter(WaveletType type) {
        switch (type) {
            case DB2: return H_DB2;
            case HAAR: return H_HAAR;
            case DB4:
            default:
                return H_DB4;
        }
    }

    @Override
    public void setFs(double fs) {
        this.fs = fs;
    }

    public double getFs() { return fs; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public WaveletType getWaveletType() { return waveletType; }
    public void setWaveletType(WaveletType waveletType) { this.waveletType = waveletType; }

    public ThresholdMode getThresholdMode() { return thresholdMode; }
    public void setThresholdMode(ThresholdMode thresholdMode) { this.thresholdMode = thresholdMode; }

    public int getDecompositionLevels() { return decompositionLevels; }
    public void setDecompositionLevels(int decompositionLevels) {
        this.decompositionLevels = Math.max(1, Math.min(8, decompositionLevels));
    }

    public double getThresholdMultiplier() { return thresholdMultiplier; }
    public void setThresholdMultiplier(double thresholdMultiplier) {
        this.thresholdMultiplier = Math.max(0.1, Math.min(5.0, thresholdMultiplier));
    }

    public double getLastNoiseSigma() { return lastNoiseSigma; }
    public double getLastThreshold() { return lastThreshold; }
    public double getLastZeroedPercent() { return lastZeroedPercent; }
    public double getLastSnrImprovementDb() { return lastSnrImprovementDb; }
}
