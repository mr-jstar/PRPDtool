package dsp;

import java.util.Arrays;

/**
 * High-performance Fast Fourier Transform (FFT) and spectral analysis
 * designed for Partial Discharge (WNZ) pulse waveform characterization.
 * Supports Radix-2 Cooley-Tukey, windowing functions, magnitude/dB spectra,
 * and standard CIGRE / IEC spectral parameters.
 */
public class FastFourierTransform {

    public enum WindowType {
        RECTANGULAR("Brak okna (Rectangular)"),
        HANNING("Hanning (zalecane)"),
        HAMMING("Hamming"),
        BLACKMAN_HARRIS("Blackman-Harris (niski przeciek)");

        private final String displayName;

        WindowType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static class SpectrumResult {
        public final double[] frequencies;     // [Hz]
        public final double[] magnitudes;      // [mV or V - linear peak]
        public final double[] magnitudesDb;    // [dBmV]
        public final double peakFrequency;     // [Hz]
        public final double spectralCentroid;  // [Hz]
        public final double equivalentBandwidth; // [Hz]
        public final double equivalentFrequency; // [Hz] (CIGRE F_eq)
        public final double maxMagnitude;
        public final double fs;
        public final int fftSize;

        public SpectrumResult(double[] frequencies, double[] magnitudes, double[] magnitudesDb,
                              double peakFrequency, double spectralCentroid, double equivalentBandwidth,
                              double equivalentFrequency, double maxMagnitude, double fs, int fftSize) {
            this.frequencies = frequencies;
            this.magnitudes = magnitudes;
            this.magnitudesDb = magnitudesDb;
            this.peakFrequency = peakFrequency;
            this.spectralCentroid = spectralCentroid;
            this.equivalentBandwidth = equivalentBandwidth;
            this.equivalentFrequency = equivalentFrequency;
            this.maxMagnitude = maxMagnitude;
            this.fs = fs;
            this.fftSize = fftSize;
        }
    }

    /**
     * Computes the one-sided magnitude and dB spectrum of an input signal.
     *
     * @param signal     time domain signal samples
     * @param fs         sampling frequency in Hz (e.g. 125e6)
     * @param windowType window function to apply before FFT
     * @return SpectrumResult containing frequencies, magnitudes, and spectral metrics
     */
    public static SpectrumResult computeSpectrum(double[] signal, double fs, WindowType windowType) {
        if (signal == null || signal.length < 4 || fs <= 0.0) {
            return new SpectrumResult(new double[0], new double[0], new double[0], 0, 0, 0, 0, 0, fs, 0);
        }

        int nOrig = signal.length;
        int nFft = nextPowerOfTwo(nOrig);
        if (nFft < 64) nFft = 64;

        double[] real = new double[nFft];
        double[] imag = new double[nFft];

        // Apply window function
        double[] window = getWindow(nOrig, windowType);
        double windowSum = 0.0;
        for (int i = 0; i < nOrig; i++) {
            real[i] = signal[i] * window[i];
            windowSum += window[i];
        }
        if (windowSum == 0.0) windowSum = 1.0;

        // Perform in-place Radix-2 Cooley-Tukey FFT
        fft(real, imag);

        int numBins = nFft / 2 + 1;
        double[] frequencies = new double[numBins];
        double[] magnitudes = new double[numBins];
        double[] magnitudesDb = new double[numBins];

        double df = fs / nFft;
        double maxMag = 0.0;
        double peakFreq = 0.0;

        // Scaling factor for one-sided amplitude spectrum
        double scale = 2.0 / windowSum;

        for (int k = 0; k < numBins; k++) {
            frequencies[k] = k * df;
            double r = real[k];
            double im = imag[k];
            double mag = Math.sqrt(r * r + im * im) * scale;
            if (k == 0 || k == numBins - 1) {
                // DC and Nyquist are not doubled
                mag *= 0.5;
            }
            magnitudes[k] = mag;

            if (mag > maxMag) {
                maxMag = mag;
                peakFreq = frequencies[k];
            }
        }

        // Compute dB scale (reference: 1 mV -> 0 dBmV)
        double ref = 1.0;
        for (int k = 0; k < numBins; k++) {
            double val = Math.max(magnitudes[k], 1e-12);
            magnitudesDb[k] = 20.0 * Math.log10(val / ref);
        }

        // Spectral centroid and equivalent bandwidth calculations
        double sumMag = 0.0;
        double sumFMag = 0.0;
        double sumMagSq = 0.0;
        double sumFsqMagSq = 0.0;

        for (int k = 0; k < numBins; k++) {
            double m = magnitudes[k];
            double f = frequencies[k];
            sumMag += m;
            sumFMag += f * m;

            double m2 = m * m;
            sumMagSq += m2;
            sumFsqMagSq += (f * f) * m2;
        }

        double centroid = (sumMag > 1e-12) ? (sumFMag / sumMag) : 0.0;

        double sumVar = 0.0;
        for (int k = 0; k < numBins; k++) {
            double dfCent = frequencies[k] - centroid;
            sumVar += (dfCent * dfCent) * magnitudes[k];
        }
        double eqBw = (sumMag > 1e-12) ? Math.sqrt(sumVar / sumMag) : 0.0;
        double eqFreq = (sumMagSq > 1e-12) ? Math.sqrt(sumFsqMagSq / sumMagSq) : 0.0;

        return new SpectrumResult(frequencies, magnitudes, magnitudesDb,
                peakFreq, centroid, eqBw, eqFreq, maxMag, fs, nFft);
    }

    /**
     * Radix-2 in-place Cooley-Tukey FFT.
     */
    public static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n <= 1) return;

        // Bit-reversal permutation
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                double tempR = real[i];
                real[i] = real[j];
                real[j] = tempR;

                double tempI = imag[i];
                imag[i] = imag[j];
                imag[j] = tempI;
            }
            int k = n >> 1;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        // Butterfly computations
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            double wlenR = Math.cos(angle);
            double wlenI = Math.sin(angle);

            int halfLen = len >> 1;
            for (int i = 0; i < n; i += len) {
                double wR = 1.0;
                double wI = 0.0;
                for (int m = 0; m < halfLen; m++) {
                    int uIdx = i + m;
                    int vIdx = i + m + halfLen;

                    double uR = real[uIdx];
                    double uI = imag[uIdx];

                    double vR = real[vIdx] * wR - imag[vIdx] * wI;
                    double vI = real[vIdx] * wI + imag[vIdx] * wR;

                    real[uIdx] = uR + vR;
                    imag[uIdx] = uI + vI;

                    real[vIdx] = uR - vR;
                    imag[vIdx] = uI - vI;

                    double nextWR = wR * wlenR - wI * wlenI;
                    wI = wR * wlenI + wI * wlenR;
                    wR = nextWR;
                }
            }
        }
    }

    /**
     * Returns window function array of specified length.
     */
    public static double[] getWindow(int length, WindowType type) {
        double[] w = new double[length];
        if (length <= 1) {
            Arrays.fill(w, 1.0);
            return w;
        }

        switch (type) {
            case HANNING:
                for (int i = 0; i < length; i++) {
                    w[i] = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (length - 1)));
                }
                break;
            case HAMMING:
                for (int i = 0; i < length; i++) {
                    w[i] = 0.54 - 0.46 * Math.cos((2.0 * Math.PI * i) / (length - 1));
                }
                break;
            case BLACKMAN_HARRIS:
                double a0 = 0.35875;
                double a1 = 0.48829;
                double a2 = 0.14128;
                double a3 = 0.01168;
                for (int i = 0; i < length; i++) {
                    double theta = (2.0 * Math.PI * i) / (length - 1);
                    w[i] = a0 - a1 * Math.cos(theta) + a2 * Math.cos(2.0 * theta) - a3 * Math.cos(3.0 * theta);
                }
                break;
            case RECTANGULAR:
            default:
                Arrays.fill(w, 1.0);
                break;
        }
        return w;
    }

    public static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }
}
