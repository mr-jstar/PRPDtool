package pipeline;

import dsp.FastFourierTransform;
import dsp.FastFourierTransform.SpectrumResult;
import dsp.FastFourierTransform.WindowType;

import java.util.Locale;

/**
 * Encapsulates a high-resolution, un-decimated snapshot of a single Partial Discharge (WNZ) pulse waveform.
 * Contains both raw and filtered samples, time coordinates, and computes standard IEC / CIGRE metrics:
 * Rise Time (10-90%), FWHM Pulse Width, Fall Time, Energy, Spectral Centroid, and CIGRE T-F classification.
 */
public class PulseWaveform {

    public final int id;
    public final double timestamp;        // [s] absolute time of peak
    public final double phase;            // [°] 0 to 360
    public final double peakMagnitude;    // [mV]
    public final double fs;               // [Hz]
    public final double[] timeRelativeNs; // [ns] relative to peak (peak at t=0)
    public final double[] raw;            // [mV] raw ADC signal
    public final double[] filtered;       // [mV] filtered signal
    public final int peakIndex;

    // Computed Time-Domain Metrics
    public final double riseTimeNs;       // [ns] 10% to 90% rise time
    public final double pulseWidthNs;     // [ns] Full-Width at Half-Maximum (FWHM)
    public final double fallTimeNs;       // [ns] 90% to 10% fall time
    public final double energy;           // [mV^2 * us]
    public final double equivalentTimeNs; // [ns] CIGRE T_eq
    public final String polarity;         // "+" or "-"

    public PulseWaveform(int id, double timestamp, double phase, double peakMagnitude, double fs,
                         double[] raw, double[] filtered, int peakIndex) {
        this.id = id;
        this.timestamp = timestamp;
        this.phase = phase;
        this.peakMagnitude = peakMagnitude;
        this.fs = (fs > 0) ? fs : 125e6;
        this.raw = raw;
        this.filtered = filtered;
        this.peakIndex = peakIndex;

        int len = (filtered != null) ? filtered.length : ((raw != null) ? raw.length : 0);
        this.timeRelativeNs = new double[len];
        double dtNs = (1.0 / this.fs) * 1e9;

        for (int i = 0; i < len; i++) {
            this.timeRelativeNs[i] = (i - peakIndex) * dtNs;
        }

        // Determine polarity
        double peakVal = (filtered != null && peakIndex >= 0 && peakIndex < filtered.length)
                ? filtered[peakIndex]
                : peakMagnitude;
        this.polarity = (peakVal >= 0) ? "+" : "-";

        // Analyze time domain characteristics
        double[] targetSig = (filtered != null && filtered.length > 0) ? filtered : raw;
        double[] timeMetrics = computeTimeMetrics(targetSig, peakIndex, dtNs);
        this.riseTimeNs = timeMetrics[0];
        this.pulseWidthNs = timeMetrics[1];
        this.fallTimeNs = timeMetrics[2];
        this.energy = timeMetrics[3];
        this.equivalentTimeNs = timeMetrics[4];
    }

    /**
     * Computes Rise Time (10%-90%), FWHM Width, Fall Time, Energy, and CIGRE Equivalent Time T_eq.
     */
    private static double[] computeTimeMetrics(double[] signal, int peakIdx, double dtNs) {
        if (signal == null || signal.length < 4) {
            return new double[]{0, 0, 0, 0, 0};
        }

        // Find actual peak near peakIdx if peakIdx is not already at true maximum
        int actualPeak = peakIdx;
        if (actualPeak < 0 || actualPeak >= signal.length) {
            actualPeak = 0;
        }
        double maxAbs = Math.abs(signal[actualPeak]);
        int searchStart = Math.max(0, actualPeak - 64);
        int searchEnd = Math.min(signal.length - 1, actualPeak + 64);
        for (int i = searchStart; i <= searchEnd; i++) {
            double a = Math.abs(signal[i]);
            if (a > maxAbs) {
                maxAbs = a;
                actualPeak = i;
            }
        }

        if (maxAbs < 1e-6) {
            return new double[]{0, 0, 0, 0, 0};
        }

        double thresh10 = 0.10 * maxAbs;
        double thresh50 = 0.50 * maxAbs;
        double thresh90 = 0.90 * maxAbs;

        // 1. Rise Time (before peak): find crossing of 10% and 90%
        int idx10 = -1;
        int idx90 = -1;
        for (int i = actualPeak; i >= 0; i--) {
            double a = Math.abs(signal[i]);
            if (idx90 == -1 && a <= thresh90) {
                idx90 = i;
            }
            if (idx10 == -1 && a <= thresh10) {
                idx10 = i;
                break;
            }
        }
        double tr = (idx90 > idx10 && idx10 != -1) ? (idx90 - idx10) * dtNs : Math.max(dtNs, 2.0 * dtNs);

        // 2. FWHM Pulse Width (50% level before and after peak)
        int idxFwhmLeft = 0;
        for (int i = actualPeak; i >= 0; i--) {
            if (Math.abs(signal[i]) <= thresh50) {
                idxFwhmLeft = i;
                break;
            }
        }
        int idxFwhmRight = signal.length - 1;
        for (int i = actualPeak; i < signal.length; i++) {
            if (Math.abs(signal[i]) <= thresh50) {
                idxFwhmRight = i;
                break;
            }
        }
        double tw = Math.max(dtNs, (idxFwhmRight - idxFwhmLeft) * dtNs);

        // 3. Fall Time (after peak): find crossing of 90% and 10%
        int idxFall90 = -1;
        int idxFall10 = -1;
        for (int i = actualPeak; i < signal.length; i++) {
            double a = Math.abs(signal[i]);
            if (idxFall90 == -1 && a <= thresh90) {
                idxFall90 = i;
            }
            if (idxFall10 == -1 && a <= thresh10) {
                idxFall10 = i;
                break;
            }
        }
        double tf = (idxFall10 > idxFall90 && idxFall10 != -1) ? (idxFall10 - idxFall90) * dtNs : (tw * 1.5);

        // 4. Energy and CIGRE Equivalent Time T_eq:
        // T_eq = sqrt( sum( (t - t0)^2 * s(t)^2 ) / sum( s(t)^2 ) )
        double sumS2 = 0.0;
        double sumTs2 = 0.0;
        double sumTsqS2 = 0.0;

        for (int i = 0; i < signal.length; i++) {
            double s2 = signal[i] * signal[i];
            double t = (i - actualPeak) * dtNs; // in ns
            sumS2 += s2;
            sumTs2 += t * s2;
            sumTsqS2 += (t * t) * s2;
        }

        double dtUs = dtNs * 1e-3;
        double pulseEnergy = sumS2 * dtUs; // [mV^2 * us]

        double meanT = (sumS2 > 1e-9) ? (sumTs2 / sumS2) : 0.0;
        double varianceT = (sumS2 > 1e-9) ? Math.max(0.0, (sumTsqS2 / sumS2) - (meanT * meanT)) : 0.0;
        double teq = Math.sqrt(varianceT);

        return new double[]{tr, tw, tf, pulseEnergy, teq};
    }

    /**
     * Computes the FFT spectrum for this pulse.
     */
    public SpectrumResult getSpectrum(WindowType windowType, boolean useFiltered) {
        double[] target = (useFiltered && filtered != null && filtered.length > 0) ? filtered : raw;
        return FastFourierTransform.computeSpectrum(target, fs, windowType);
    }

    /**
     * CIGRE / IEEE Equivalent Time-Frequency Diagnostic Classification.
     * Evaluates whether the pulse corresponds to fast HF discharges (Internal / Void / Gas),
     * damped propagation (Cable / Surface), or Low-Frequency / Switching noise.
     */
    public String getCigreClassification(SpectrumResult spec) {
        double fPeakMhz = (spec != null) ? spec.peakFrequency / 1e6 : 0.0;
        double fCentMhz = (spec != null) ? spec.spectralCentroid / 1e6 : 0.0;

        if (fCentMhz >= 12.0 && riseTimeNs <= 50.0) {
            return "Wyładowanie wysokoczęstotliwościowe (szczelina gazowa / wnęka void / SF6) – Bardzo strome czoło i szerokie pasmo powyżej 12 MHz.";
        } else if (fCentMhz >= 3.0 && fCentMhz < 12.0) {
            return "Wyładowanie średnioczęstotliwościowe (powierzchniowe / drzewienie kabla) – Pasmo 3–12 MHz i tłumione oscylacje dielektryka.";
        } else if (fPeakMhz < 2.0 && riseTimeNs > 80.0) {
            return "Zakłócenie niskoczęstotliwościowe / szum sieci – Powolne czoło fali i wąskie pasmo poniżej 2 MHz.";
        } else {
            return "Wyładowanie niezupełne (WNZ) – Parametry mieszane (typowy impuls w układzie HFCT / kondensatora sprzęgającego).";
        }
    }

    public String getDisplayTitle() {
        return String.format(Locale.US, "Q=%.1f mV | φ=%.1f° | t=%.4f s", peakMagnitude, phase, timestamp);
    }

    @Override
    public String toString() {
        return getDisplayTitle();
    }
}
