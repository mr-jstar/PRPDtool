package dsp;

import pipeline.Buffer;

/**
 *
 * @author jstar
 */
public class PhaseEstimator {

    public static double estimateIntialPhase(Buffer b, double f0) {
        return estimateIntialPhase(b, f0, false); // wsteczna kompatybilność
    }

    public static double estimateIntialPhase(Buffer b, double f0, boolean useHardwareReference) {
        if (b == null || b.used < 10) {
            return Double.NaN;
        }

        double duration = b.t[b.used - 1] - b.t[0];
        if (duration <= 0.0) {
            return Double.NaN;
        }

        // Wybieramy z którego strumienia próbek liczymy fazę.
        // Sprawdzamy też czy u2 faktycznie zawiera sygnał (nie same zera),
        // bo przy nagrywaniu tylko 1 kanału u2 będzie zerowe.
        double[] signalToUse = b.u;
        if (useHardwareReference && b.u2 != null) {
            double maxAbs = 0.0;
            for (int i = 0; i < b.used; i++) {
                double a = Math.abs(b.u2[i]);
                if (a > maxAbs) maxAbs = a;
            }
            if (maxAbs > 0.0) {
                signalToUse = b.u2;
            } else {
                System.out.println("WARNING: HW Phase Ref (CH2) selected but u2 is all zeros — falling back to CH1.");
            }
        }

        // Subsampling dla natychmiastowego dopasowania MNK (do 10 000 punktów w buforze)
        int stride = Math.max(1, b.used / 10000);
        double w = 2.0 * Math.PI * f0;
        double T = 1.0 / f0;

        double m00 = 0.0, m01 = 0.0, m02 = 0.0;
        double m11 = 0.0, m12 = 0.0, m22 = 0.0;
        double v0 = 0.0, v1 = 0.0, v2 = 0.0;

        for (int i = 0; i < b.used; i += stride) {
            double ti = b.t[i] % T;
            if (ti < 0.0) ti += T;
            double angle = w * ti;
            double s = Math.sin(angle);
            double c = Math.cos(angle);
            double val = signalToUse[i];

            m00 += s * s;
            m01 += s * c;
            m02 += s;
            m11 += c * c;
            m12 += c;
            m22 += 1.0;

            v0 += s * val;
            v1 += c * val;
            v2 += val;
        }

        // Wyznacznik macierzy 3x3 normalnych równań MNK
        double det = m00 * (m11 * m22 - m12 * m12)
                   - m01 * (m01 * m22 - m12 * m02)
                   + m02 * (m01 * m12 - m11 * m02);

        if (Math.abs(det) < 1e-12) {
            return Double.NaN;
        }

        double detA = v0 * (m11 * m22 - m12 * m12)
                    - m01 * (v1 * m22 - m12 * v2)
                    + m02 * (v1 * m12 - m11 * v2);
        double A = detA / det;

        double detB = m00 * (v1 * m22 - m12 * v2)
                    - v0 * (m01 * m22 - m12 * m02)
                    + m02 * (m01 * v2 - v1 * m02);
        double B = detB / det;

        double amp = Math.sqrt(A * A + B * B);
        // Jeśli amplituda składowej 50Hz jest znikoma (< 0.1 mV), uznajemy brak sygnału sieciowego
        if (amp < 0.1) {
            return Double.NaN;
        }

        // Model sygnału: u(t) = A*sin(w*t) + B*cos(w*t) + C = amp*sin(w*t + theta) + C
        // gdzie theta = atan2(B, A).
        // Dodatnie przejście przez zero (zbocze rosnące): w*t0 + theta = 0 => t0 = -theta / w
        double theta = Math.atan2(B, A);
        double t0 = -theta / w;

        t0 = t0 % T;
        if (t0 < 0.0) {
            t0 += T;
        }

        // Zwracamy ph0 = (t0 / T) * 2 * Math.PI, co w PRPDTool daje estt0 = t0
        return (t0 / T) * 2.0 * Math.PI;
    }
}

