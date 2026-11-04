package pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe central repository for high-resolution Partial Discharge (WNZ) pulse waveforms.
 * Stores up to MAX_CAPACITY pulses using an intelligent significance-aware retention strategy:
 * - High-amplitude characteristic pulses (|Q|) are prioritized and protected against being evicted by low background noise.
 * - The global maximum pulse (Qmax) is permanently preserved.
 * - Supports multiple diagnostic sorting orders: Amplitude Descending (Top Q), Chronological, and WNZ Filtered.
 */
public class PulseWaveformRepository {

    public enum SortOrder {
        AMPLITUDE_DESC("Najwyższe amplitudy (|Q| malejąco)"),
        CHRONOLOGICAL("Chronologicznie (czas t)"),
        FILTER_WNZ("Tylko WNZ (|Q| ≥ 10 mV)");

        public final String label;
        SortOrder(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final PulseWaveformRepository INSTANCE = new PulseWaveformRepository();

    public static PulseWaveformRepository getInstance() {
        return INSTANCE;
    }

    public static final int DEFAULT_MAX_CAPACITY = 800;
    private final int maxCapacity;

    private final List<PulseWaveform> pulses = new ArrayList<>();
    private PulseWaveform maxPulse = null;
    private PulseWaveform latestPulse = null;
    private int pulseCounter = 0;

    private final List<Consumer<PulseWaveform>> listeners = new CopyOnWriteArrayList<>();

    public PulseWaveformRepository() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public PulseWaveformRepository(int maxCapacity) {
        this.maxCapacity = (maxCapacity > 10) ? maxCapacity : DEFAULT_MAX_CAPACITY;
    }

    public synchronized void clear() {
        pulses.clear();
        maxPulse = null;
        latestPulse = null;
        pulseCounter = 0;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public synchronized double getMinPeakMagnitude() {
        if (pulses.size() < maxCapacity) return 0.0;
        double min = Double.MAX_VALUE;
        for (PulseWaveform p : pulses) {
            double amp = Math.abs(p.peakMagnitude);
            if (amp < min) min = amp;
        }
        return (min == Double.MAX_VALUE) ? 0.0 : min;
    }

    public synchronized boolean shouldCapture(double amp, int count) {
        if (pulses.size() < maxCapacity) return true;
        double absAmp = Math.abs(amp);
        if (maxPulse == null || absAmp > Math.abs(maxPulse.peakMagnitude)) return true;
        if (absAmp > getMinPeakMagnitude()) return true;
        if (absAmp >= 10.0 && count % 2 == 0) return true;
        return (count % 32 == 0);
    }

    public synchronized void addPulse(double timestamp, double phase, double peakMagnitude, double fs,
                                      double[] raw, double[] filtered, int peakIndex) {
        pulseCounter++;
        PulseWaveform pw = new PulseWaveform(pulseCounter, timestamp, phase, peakMagnitude, fs, raw, filtered, peakIndex);
        addPulse(pw);
    }

    public synchronized void addPulse(PulseWaveform pw) {
        if (pw == null) return;

        pulseCounter++;
        latestPulse = pw;
        if (maxPulse == null || Math.abs(pw.peakMagnitude) > Math.abs(maxPulse.peakMagnitude)) {
            maxPulse = pw;
        }

        if (pulses.size() < maxCapacity) {
            pulses.add(pw);
        } else {
            // Find pulse with minimum absolute amplitude (ignoring maxPulse)
            int minIdx = -1;
            double minAmp = Double.MAX_VALUE;
            for (int i = 0; i < pulses.size(); i++) {
                PulseWaveform p = pulses.get(i);
                if (p == maxPulse || p.id == maxPulse.id) continue;
                double amp = Math.abs(p.peakMagnitude);
                if (amp < minAmp) {
                    minAmp = amp;
                    minIdx = i;
                }
            }

            double newAmp = Math.abs(pw.peakMagnitude);
            if (minIdx >= 0 && newAmp > minAmp) {
                // Significance-aware replacement: stronger discharge replaces the weakest noise
                pulses.set(minIdx, pw);
            } else if (minIdx >= 0 && minAmp < 4.0) {
                // If weakest pulse is negligible background noise (< 4 mV), replace it
                pulses.set(minIdx, pw);
            } else if (pulses.size() > 100) {
                // Dedicated rolling live window for the last 100 slots
                int replaceIdx = maxCapacity - 100 + (pulseCounter % 100);
                if (replaceIdx >= 0 && replaceIdx < pulses.size()) {
                    if (pulses.get(replaceIdx) != maxPulse) {
                        pulses.set(replaceIdx, pw);
                    }
                }
            }
        }

        notifyListeners(pw);
    }

    private void notifyListeners(PulseWaveform pw) {
        for (Consumer<PulseWaveform> l : listeners) {
            try {
                l.accept(pw);
            } catch (Exception ignored) {}
        }
    }

    public synchronized List<PulseWaveform> getSnapshot() {
        return getSnapshot(SortOrder.AMPLITUDE_DESC);
    }

    public synchronized List<PulseWaveform> getSnapshot(SortOrder order) {
        List<PulseWaveform> list = new ArrayList<>(pulses);
        if (order == null || order == SortOrder.AMPLITUDE_DESC) {
            list.sort((a, b) -> Double.compare(Math.abs(b.peakMagnitude), Math.abs(a.peakMagnitude)));
        } else if (order == SortOrder.CHRONOLOGICAL) {
            list.sort(Comparator.comparingDouble(a -> a.timestamp));
        } else if (order == SortOrder.FILTER_WNZ) {
            list.removeIf(p -> Math.abs(p.peakMagnitude) < 10.0);
            list.sort((a, b) -> Double.compare(Math.abs(b.peakMagnitude), Math.abs(a.peakMagnitude)));
            if (list.isEmpty() && !pulses.isEmpty()) {
                // Fallback: show top pulses if all are under 10 mV
                list.addAll(pulses);
                list.sort((a, b) -> Double.compare(Math.abs(b.peakMagnitude), Math.abs(a.peakMagnitude)));
            }
        }
        return list;
    }

    public synchronized PulseWaveform getMaxPulse() {
        return maxPulse;
    }

    public synchronized PulseWaveform getLatestPulse() {
        return latestPulse;
    }

    public synchronized int getCount() {
        return pulses.size();
    }

    public synchronized PulseWaveform getById(int id) {
        for (PulseWaveform p : pulses) {
            if (p.id == id) return p;
        }
        return null;
    }

    public void addListener(Consumer<PulseWaveform> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<PulseWaveform> listener) {
        listeners.remove(listener);
    }
}
