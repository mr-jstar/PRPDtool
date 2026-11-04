package dsp;

import dsp.FastFourierTransform.SpectrumResult;
import dsp.FastFourierTransform.WindowType;

import java.util.*;

/**
 * Adaptive Multi-Notch Filter Bank for Partial Discharge (WNZ) Denoising.
 * Eliminates discrete narrowband continuous interference (NBI) caused by
 * radio broadcasts, power electronics switching harmonics, and PLC communications
 * without attenuating wideband partial discharge transients (IEC 62478 / CIGRE WG D1.33).
 */
public class AdaptiveNotchFilter {

    public static class NotchSlot {
        public boolean enabled;
        public double freqHz;
        public double q;
        public String description;

        private DigitalFilters.Biquad streamingBiquad;

        public NotchSlot(boolean enabled, double freqHz, double q, String description) {
            this.enabled = enabled;
            this.freqHz = freqHz;
            this.q = q;
            this.description = description;
        }

        public void initStreaming(double fs) {
            if (enabled && freqHz > 0 && freqHz < fs / 2.0) {
                streamingBiquad = DigitalFilters.Biquad.notch(fs, freqHz, q);
            } else {
                streamingBiquad = null;
            }
        }

        public double processSample(double sample) {
            if (streamingBiquad != null) {
                return streamingBiquad.process(sample);
            }
            return sample;
        }

        public void reset() {
            if (streamingBiquad != null) {
                streamingBiquad.reset();
            }
        }
    }

    private final List<NotchSlot> slots = new ArrayList<>();
    private double currentFs = 125e6;

    public AdaptiveNotchFilter() {
        // Default standard industrial interference slots
        slots.add(new NotchSlot(false, 100_000.0, 20.0, "Falownik PWM (100 kHz)"));
        slots.add(new NotchSlot(false, 500_000.0, 25.0, "Przetwornica DC/DC (500 kHz)"));
        slots.add(new NotchSlot(false, 1_250_000.0, 30.0, "Nadajnik radiowy AM (1.25 MHz)"));
        slots.add(new NotchSlot(false, 2_500_000.0, 30.0, "Radiolinia NBI (2.5 MHz)"));
        slots.add(new NotchSlot(false, 10_700_000.0, 35.0, "Częstotliwość p.cz. (10.7 MHz)"));
    }

    public synchronized void setFs(double fs) {
        this.currentFs = fs;
        for (NotchSlot slot : slots) {
            slot.initStreaming(fs);
        }
    }

    public synchronized List<NotchSlot> getSlots() {
        return slots;
    }

    public synchronized void setSlot(int index, boolean enabled, double freqHz, double q, String desc) {
        if (index >= 0 && index < slots.size()) {
            NotchSlot s = slots.get(index);
            s.enabled = enabled;
            s.freqHz = freqHz;
            s.q = q;
            s.description = desc;
            s.initStreaming(currentFs);
        }
    }

    public synchronized void reset() {
        for (NotchSlot s : slots) {
            s.reset();
        }
    }

    /**
     * Filters a single sample in real-time streaming pipeline.
     */
    public synchronized double filterSample(double x) {
        double y = x;
        for (NotchSlot s : slots) {
            if (s.enabled) {
                y = s.processSample(y);
            }
        }
        return y;
    }

    /**
     * Filters an entire signal buffer through all active notch filters in cascade.
     */
    public synchronized double[] filter(double[] signal, int n) {
        double[] out = new double[n];
        System.arraycopy(signal, 0, out, 0, n);

        for (NotchSlot s : slots) {
            if (s.enabled && s.freqHz > 0 && s.freqHz < currentFs / 2.0) {
                out = DigitalFilters.notchIIRZeroPhase(out, currentFs, s.freqHz, s.q, 2);
            }
        }
        return out;
    }

    /**
     * Automatic Spectral Spike Scanner (NBI Detection):
     * Analyzes FFT spectrum, estimates noise floor baseline, and finds prominent
     * sinusoidal narrowband spikes sticking out by thresholdDb above baseline.
     */
    public static List<Double> detectHarmonicSpikes(double[] signal, double fs, double thresholdDbAboveBaseline, int maxSpikes) {
        List<Double> spikes = new ArrayList<>();
        if (signal == null || signal.length < 512 || fs <= 0) return spikes;

        int len = Math.min(signal.length, 4096);
        // Ensure radix-2
        int p2 = 1;
        while (p2 * 2 <= len) p2 *= 2;
        if (p2 < 256) return spikes;

        double[] slice = new double[p2];
        System.arraycopy(signal, 0, slice, 0, p2);

        SpectrumResult spec = FastFourierTransform.computeSpectrum(slice, fs, WindowType.BLACKMAN_HARRIS);
        if (spec == null || spec.frequencies.length < 10) return spikes;

        double[] freqs = spec.frequencies;
        double[] db = spec.magnitudesDb;
        int nBins = freqs.length;

        // Compute median noise floor across active range (100 kHz to 0.45 * fs)
        List<Double> validDbs = new ArrayList<>();
        for (int i = 0; i < nBins; i++) {
            if (freqs[i] >= 100_000.0 && freqs[i] <= fs * 0.45) {
                validDbs.add(db[i]);
            }
        }
        if (validDbs.isEmpty()) return spikes;

        Collections.sort(validDbs);
        double medianNoiseDb = validDbs.get(validDbs.size() / 2);

        // Find prominent local maxima
        class Peak {
            final double freq;
            final double magnitudeDb;
            final double prominence;
            Peak(double freq, double mag, double prom) {
                this.freq = freq;
                this.magnitudeDb = mag;
                this.prominence = prom;
            }
        }
        List<Peak> peaks = new ArrayList<>();

        int minBin = 0;
        while (minBin < nBins && freqs[minBin] < 100_000.0) minBin++;
        int maxBin = nBins - 2;
        while (maxBin > 0 && freqs[maxBin] > fs * 0.45) maxBin--;

        for (int i = minBin + 1; i < maxBin; i++) {
            if (db[i] > db[i - 1] && db[i] > db[i + 1]) {
                double prom = db[i] - medianNoiseDb;
                if (prom >= thresholdDbAboveBaseline) {
                    // Quadratic peak interpolation for exact frequency
                    double y1 = db[i - 1], y2 = db[i], y3 = db[i + 1];
                    double delta = 0.5 * (y1 - y3) / (y1 - 2 * y2 + y3);
                    double binW = fs / p2;
                    double exactFreq = freqs[i] + delta * binW;
                    peaks.add(new Peak(exactFreq, db[i], prom));
                }
            }
        }

        // Sort by prominence descending
        peaks.sort((a, b) -> Double.compare(b.prominence, a.prominence));

        // Select top non-adjacent peaks
        for (Peak p : peaks) {
            boolean tooClose = false;
            for (double existing : spikes) {
                if (Math.abs(existing - p.freq) < (p.freq * 0.05 + 50_000.0)) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                spikes.add(p.freq);
                if (spikes.size() >= maxSpikes) break;
            }
        }

        return spikes;
    }
}
