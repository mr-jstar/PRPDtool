package dsp;

import java.util.Arrays;

/**
 * Fast Fourier Transform (FFT) Spectral Subtraction and Masking Filter for High-Voltage PD Signals.
 * Operates entirely in the frequency domain using Radix-2 Cooley-Tukey FFT / IFFT.
 * 
 * Implements non-linear spectral subtraction:
 * |S(f)| = max( beta * |Y(f)|, |Y(f)| - alpha * |N(f)| )
 * preserving the instantaneous phase angle theta(f) = atan2(Im, Re).
 */
public class FFTSpectralFilter implements Filter {

    public enum FilterMode {
        SPECTRAL_SUBTRACTION("Odejmowanie widmowe (Spectral Subtraction - zalecane)"),
        THRESHOLD_MASKING("Progowanie widmowe (Spectral Masking)"),
        BRICKWALL_BANDPASS("Filtr pasmowy w domenie FFT (Brickwall)");

        private final String displayName;

        FilterMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }

        @Override
        public String toString() { return displayName; }
    }

    private boolean enabled = true;
    private FilterMode mode = FilterMode.SPECTRAL_SUBTRACTION;
    private double oversubtractionAlpha = 1.8; // alpha parameter
    private double spectralFloorBeta = 0.05;    // beta parameter (prevents musical noise)
    private double cutoffLowHz = 100e3;        // 100 kHz
    private double cutoffHighHz = 20e6;        // 20 MHz
    private double fs = 125e6;                 // 125 MS/s

    // Diagnostics
    private double lastSnrImprovementDb = 0.0;
    private double lastNoiseFloorEstimate = 0.0;

    public FFTSpectralFilter() {
    }

    public FFTSpectralFilter(FilterMode mode, double alpha, double beta) {
        this.mode = mode;
        this.oversubtractionAlpha = Math.max(0.5, Math.min(5.0, alpha));
        this.spectralFloorBeta = Math.max(0.01, Math.min(0.3, beta));
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

        // Segmented processing for large buffers to preserve local non-stationarity
        if (len > 4096) {
            int blockSize = 2048;
            int hopSize = 1024;
            double[] out = new double[len];
            double[] weights = new double[len];

            for (int start = 0; start < len; start += hopSize) {
                int curLen = Math.min(blockSize, len - start);
                double[] block = new double[curLen];
                System.arraycopy(signal, start, block, 0, curLen);

                double[] filtered = processFftBlock(block, curLen);

                for (int i = 0; i < curLen; i++) {
                    int oIdx = start + i;
                    if (oIdx < len) {
                        double w = 1.0 - Math.abs((2.0 * i / (curLen - 1)) - 1.0);
                        out[oIdx] += filtered[i] * w;
                        weights[oIdx] += w;
                    }
                }
            }

            for (int i = 0; i < len; i++) {
                if (weights[i] > 1e-6) out[i] /= weights[i];
                else out[i] = signal[i];
            }
            return out;
        }

        return processFftBlock(signal, len);
    }

    private double[] processFftBlock(double[] input, int len) {
        int nFft = FastFourierTransform.nextPowerOfTwo(len);
        double[] real = new double[nFft];
        double[] imag = new double[nFft];
        System.arraycopy(input, 0, real, 0, len);

        // Forward FFT
        FastFourierTransform.fft(real, imag);

        // Magnitudes and phases
        int halfFft = nFft / 2;
        double[] mag = new double[nFft];
        double[] phase = new double[nFft];

        for (int i = 0; i < nFft; i++) {
            mag[i] = Math.hypot(real[i], imag[i]);
            phase[i] = Math.atan2(imag[i], real[i]);
        }

        // Estimate stationary noise floor from the upper 25% frequency bins
        double noiseSum = 0.0;
        int noiseCount = 0;
        int startNoiseBin = (int) (halfFft * 0.75);
        for (int i = startNoiseBin; i < halfFft; i++) {
            noiseSum += mag[i];
            noiseCount++;
        }
        double noiseFloor = (noiseCount > 0) ? (noiseSum / noiseCount) : 1e-6;
        this.lastNoiseFloorEstimate = noiseFloor;

        double deltaF = fs / nFft;
        int lowBin = (int) (cutoffLowHz / deltaF);
        int highBin = (int) (cutoffHighHz / deltaF);

        // Apply spectral modification
        for (int i = 0; i <= halfFft; i++) {
            double origMag = mag[i];
            double newMag = origMag;

            switch (mode) {
                case SPECTRAL_SUBTRACTION:
                    double sub = origMag - oversubtractionAlpha * noiseFloor;
                    double floor = spectralFloorBeta * origMag;
                    newMag = Math.max(floor, sub);
                    break;

                case THRESHOLD_MASKING:
                    if (origMag < (oversubtractionAlpha * noiseFloor)) {
                        newMag = spectralFloorBeta * origMag;
                    }
                    break;

                case BRICKWALL_BANDPASS:
                    if (i < lowBin || i > highBin) {
                        newMag *= 0.01;
                    }
                    break;
            }

            mag[i] = newMag;
            // Mirror for conjugate symmetry
            if (i > 0 && i < halfFft) {
                mag[nFft - i] = newMag;
            }
        }

        // Reconstruct real and imag from new magnitude and original phase
        for (int i = 0; i < nFft; i++) {
            real[i] = mag[i] * Math.cos(phase[i]);
            imag[i] = mag[i] * Math.sin(phase[i]);
        }

        // Inverse FFT
        FastFourierTransform.ifft(real, imag);

        // Truncate to original length
        double[] out = new double[len];
        System.arraycopy(real, 0, out, 0, len);

        // SNR estimate
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

    @Override
    public void setFs(double fs) {
        this.fs = fs;
    }

    public double getFs() { return fs; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public FilterMode getMode() { return mode; }
    public void setMode(FilterMode mode) { this.mode = mode; }

    public double getOversubtractionAlpha() { return oversubtractionAlpha; }
    public void setOversubtractionAlpha(double alpha) { this.oversubtractionAlpha = Math.max(0.1, Math.min(10.0, alpha)); }

    public double getSpectralFloorBeta() { return spectralFloorBeta; }
    public void setSpectralFloorBeta(double beta) { this.spectralFloorBeta = Math.max(0.001, Math.min(0.5, beta)); }

    public double getLastSnrImprovementDb() { return lastSnrImprovementDb; }
    public double getLastNoiseFloorEstimate() { return lastNoiseFloorEstimate; }
}
