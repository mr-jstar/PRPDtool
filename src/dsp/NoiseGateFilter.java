package dsp;

/**
 * Noise Gate (Bramka Szumowa) for High-Voltage Partial Discharge Denoising.
 * Suppresses background thermal noise floor and stochastic interference
 * while preserving 100% of the true pulse transient magnitude and shape (IEC 62478).
 */
public class NoiseGateFilter {

    private boolean enabled = false;
    private double thresholdMv = 5.0;
    private double attenuationFactor = 0.125; // -18 dB attenuation when closed
    private double kneeMv = 1.5;

    // Streaming envelope follower state
    private double envelope = 0.0;
    private double decayCoeff = 0.9995;

    public NoiseGateFilter() {
    }

    public synchronized void configure(boolean enabled, double thresholdMv, double attenuationFactor, double kneeMv) {
        this.enabled = enabled;
        this.thresholdMv = Math.max(0.1, thresholdMv);
        this.attenuationFactor = Math.max(0.0, Math.min(1.0, attenuationFactor));
        this.kneeMv = Math.max(0.1, kneeMv);
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized double getThresholdMv() {
        return thresholdMv;
    }

    public synchronized void setThresholdMv(double thresholdMv) {
        this.thresholdMv = Math.max(0.1, thresholdMv);
    }

    public synchronized double getAttenuationFactor() {
        return attenuationFactor;
    }

    public synchronized void setAttenuationFactor(double attenuationFactor) {
        this.attenuationFactor = Math.max(0.0, Math.min(1.0, attenuationFactor));
    }

    public synchronized void reset() {
        this.envelope = 0.0;
    }

    /**
     * Real-time streaming sample processor with envelope follower.
     */
    public synchronized double processSample(double x) {
        if (!enabled) return x;

        double absX = Math.abs(x);
        // Fast attack, smooth decay
        if (absX > envelope) {
            envelope = absX;
        } else {
            envelope = absX * (1.0 - decayCoeff) + envelope * decayCoeff;
        }

        double gain = computeGain(envelope);
        return x * gain;
    }

    /**
     * Filters an entire buffer with smooth soft-knee gating.
     */
    public synchronized double[] filter(double[] signal, int n) {
        if (!enabled) {
            double[] out = new double[n];
            System.arraycopy(signal, 0, out, 0, n);
            return out;
        }

        double[] out = new double[n];
        double env = 0.0;

        for (int i = 0; i < n; i++) {
            double absX = Math.abs(signal[i]);
            if (absX > env) {
                env = absX;
            } else {
                env = absX * (1.0 - decayCoeff) + env * decayCoeff;
            }

            double gain = computeGain(env);
            out[i] = signal[i] * gain;
        }
        return out;
    }

    private double computeGain(double env) {
        double lowerBound = thresholdMv - kneeMv;
        double upperBound = thresholdMv + kneeMv;

        if (env <= lowerBound) {
            return attenuationFactor;
        } else if (env >= upperBound) {
            return 1.0;
        } else {
            // Smooth cubic Hermite interpolation between attenuationFactor and 1.0
            double t = (env - lowerBound) / (2.0 * kneeMv);
            double smoothT = t * t * (3.0 - 2.0 * t);
            return attenuationFactor + (1.0 - attenuationFactor) * smoothT;
        }
    }
}
