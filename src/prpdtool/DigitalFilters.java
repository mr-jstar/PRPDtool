package prpdtool;

/**
 *
 * @author jstar
 */
import java.util.Arrays;

public final class DigitalFilters {

    private DigitalFilters() {}

    // ============================================================
    // 1. CASCADE IIR BIQUAD FILTERS
    // ============================================================

    public static double[] lowpassIIR(double[] x, double fs, double fc, double q, int order) {
        return cascade(x, order, () -> Biquad.lowpass(fs, fc, q));
    }

    public static double[] highpassIIR(double[] x, double fs, double fc, double q, int order) {
        return cascade(x, order, () -> Biquad.highpass(fs, fc, q));
    }

    public static double[] bandpassIIR(double[] x, double fs, double fc, double q, int order) {
        return cascade(x, order, () -> Biquad.bandpass(fs, fc, q));
    }

    public static double[] lowpassIIRZeroPhase(double[] x, double fs, double fc, double q, int order) {
        return filtfiltCascade(x, order, () -> Biquad.lowpass(fs, fc, q));
    }

    public static double[] highpassIIRZeroPhase(double[] x, double fs, double fc, double q, int order) {
        return filtfiltCascade(x, order, () -> Biquad.highpass(fs, fc, q));
    }

    public static double[] bandpassIIRZeroPhase(double[] x, double fs, double fc, double q, int order) {
        return filtfiltCascade(x, order, () -> Biquad.bandpass(fs, fc, q));
    }

    private interface BiquadFactory {
        Biquad create();
    }

    private static double[] cascade(double[] x, int order, BiquadFactory factory) {
        if (order < 2 || order % 2 != 0) {
            throw new IllegalArgumentException("IIR order must be even and >= 2");
        }

        double[] y = Arrays.copyOf(x, x.length);
        int sections = order / 2;

        for (int i = 0; i < sections; i++) {
            y = factory.create().filter(y);
        }

        return y;
    }

    private static double[] filtfiltCascade(double[] x, int order, BiquadFactory factory) {
        double[] y = cascade(x, order, factory);
        reverse(y);
        y = cascade(y, order, factory);
        reverse(y);
        return y;
    }

    // ============================================================
    // 2. FIR FILTERS WITH HAMMING WINDOW
    // ============================================================

    public static double[] lowpassFIR(double[] x, double fs, double fc, int taps) {
        double[] h = designLowpassFIR(fs, fc, taps);
        return convolveFIR(x, h);
    }

    public static double[] highpassFIR(double[] x, double fs, double fc, int taps) {
        double[] h = designLowpassFIR(fs, fc, taps);

        int m = taps / 2;
        for (int i = 0; i < taps; i++) {
            h[i] = -h[i];
        }
        h[m] += 1.0;

        return convolveFIR(x, h);
    }

    public static double[] bandpassFIR(double[] x, double fs, double f1, double f2, int taps) {
        if (f1 <= 0 || f2 <= f1 || f2 >= fs / 2.0) {
            throw new IllegalArgumentException("Require 0 < f1 < f2 < fs/2");
        }

        double[] lp2 = designLowpassFIR(fs, f2, taps);
        double[] lp1 = designLowpassFIR(fs, f1, taps);

        double[] h = new double[taps];
        for (int i = 0; i < taps; i++) {
            h[i] = lp2[i] - lp1[i];
        }

        return convolveFIR(x, h);
    }

    private static double[] designLowpassFIR(double fs, double fc, int taps) {
        if (taps < 3 || taps % 2 == 0) {
            throw new IllegalArgumentException("FIR taps must be odd and >= 3");
        }
        if (fc <= 0 || fc >= fs / 2.0) {
            throw new IllegalArgumentException("Require 0 < fc < fs/2");
        }

        double[] h = new double[taps];
        int m = taps / 2;
        double normFc = fc / fs;

        for (int n = 0; n < taps; n++) {
            int k = n - m;

            double sinc;
            if (k == 0) {
                sinc = 2.0 * normFc;
            } else {
                sinc = Math.sin(2.0 * Math.PI * normFc * k) / (Math.PI * k);
            }

            double w = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * n / (taps - 1));
            h[n] = sinc * w;
        }

        normalizeGain(h);
        return h;
    }

    private static void normalizeGain(double[] h) {
        double sum = 0.0;
        for (double v : h) sum += v;
        for (int i = 0; i < h.length; i++) h[i] /= sum;
    }

    private static double[] convolveFIR(double[] x, double[] h) {
        double[] y = new double[x.length];

        for (int n = 0; n < x.length; n++) {
            double acc = 0.0;
            for (int k = 0; k < h.length; k++) {
                int i = n - k;
                if (i >= 0) {
                    acc += h[k] * x[i];
                }
            }
            y[n] = acc;
        }

        return y;
    }

    // ============================================================
    // 3. REAL-TIME STREAMING BIQUAD
    // ============================================================

    public static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1 = 0.0, x2 = 0.0;
        private double y1 = 0.0, y2 = 0.0;

        private Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        public double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;

            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;

            return y;
        }

        public double[] filter(double[] x) {
            double[] y = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                y[i] = process(x[i]);
            }
            return y;
        }

        public void reset() {
            x1 = x2 = y1 = y2 = 0.0;
        }

        public static Biquad lowpass(double fs, double fc, double q) {
            validate(fs, fc, q);

            double w0 = 2.0 * Math.PI * fc / fs;
            double cos = Math.cos(w0);
            double sin = Math.sin(w0);
            double alpha = sin / (2.0 * q);

            double b0 = (1.0 - cos) / 2.0;
            double b1 = 1.0 - cos;
            double b2 = (1.0 - cos) / 2.0;
            double a0 = 1.0 + alpha;
            double a1 = -2.0 * cos;
            double a2 = 1.0 - alpha;

            return normalize(b0, b1, b2, a0, a1, a2);
        }

        public static Biquad highpass(double fs, double fc, double q) {
            validate(fs, fc, q);

            double w0 = 2.0 * Math.PI * fc / fs;
            double cos = Math.cos(w0);
            double sin = Math.sin(w0);
            double alpha = sin / (2.0 * q);

            double b0 = (1.0 + cos) / 2.0;
            double b1 = -(1.0 + cos);
            double b2 = (1.0 + cos) / 2.0;
            double a0 = 1.0 + alpha;
            double a1 = -2.0 * cos;
            double a2 = 1.0 - alpha;

            return normalize(b0, b1, b2, a0, a1, a2);
        }

        public static Biquad bandpass(double fs, double fc, double q) {
            validate(fs, fc, q);

            double w0 = 2.0 * Math.PI * fc / fs;
            double cos = Math.cos(w0);
            double sin = Math.sin(w0);
            double alpha = sin / (2.0 * q);

            double b0 = alpha;
            double b1 = 0.0;
            double b2 = -alpha;
            double a0 = 1.0 + alpha;
            double a1 = -2.0 * cos;
            double a2 = 1.0 - alpha;

            return normalize(b0, b1, b2, a0, a1, a2);
        }

        private static Biquad normalize(double b0, double b1, double b2,
                                        double a0, double a1, double a2) {
            return new Biquad(
                    b0 / a0,
                    b1 / a0,
                    b2 / a0,
                    a1 / a0,
                    a2 / a0
            );
        }
    }

    // ============================================================
    // 4. REAL-TIME STREAMING FIR
    // ============================================================

    public static final class FIR {
        private final double[] h;
        private final double[] buffer;
        private int pos = 0;

        public FIR(double[] coefficients) {
            this.h = Arrays.copyOf(coefficients, coefficients.length);
            this.buffer = new double[coefficients.length];
        }

        public double process(double x) {
            buffer[pos] = x;

            double y = 0.0;
            int index = pos;

            for (int i = 0; i < h.length; i++) {
                y += h[i] * buffer[index];
                index--;
                if (index < 0) index = buffer.length - 1;
            }

            pos++;
            if (pos >= buffer.length) pos = 0;

            return y;
        }

        public double[] filter(double[] x) {
            double[] y = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                y[i] = process(x[i]);
            }
            return y;
        }

        public void reset() {
            Arrays.fill(buffer, 0.0);
            pos = 0;
        }

        public static FIR lowpass(double fs, double fc, int taps) {
            return new FIR(designLowpassFIR(fs, fc, taps));
        }

        public static FIR highpass(double fs, double fc, int taps) {
            double[] h = designLowpassFIR(fs, fc, taps);
            int m = taps / 2;
            for (int i = 0; i < taps; i++) h[i] = -h[i];
            h[m] += 1.0;
            return new FIR(h);
        }

        public static FIR bandpass(double fs, double f1, double f2, int taps) {
            double[] lp2 = designLowpassFIR(fs, f2, taps);
            double[] lp1 = designLowpassFIR(fs, f1, taps);
            double[] h = new double[taps];

            for (int i = 0; i < taps; i++) {
                h[i] = lp2[i] - lp1[i];
            }

            return new FIR(h);
        }
    }

    // ============================================================
    // Utilities
    // ============================================================

    private static void validate(double fs, double fc, double q) {
        if (fs <= 0) throw new IllegalArgumentException("fs must be > 0, but is " + fs);
        if (fc <= 0 || fc >= fs / 2.0) throw new IllegalArgumentException("Require 0 < fc < fs/2,, but got fc=" + fc + " fs="+fs);
        if (q <= 0) throw new IllegalArgumentException("Q must be > 0 but is " + q);
    }

    private static void reverse(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            double tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }
}
