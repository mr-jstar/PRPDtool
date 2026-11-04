package dsp;

import prpdtool.Configuration;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central Configuration Manager for Advanced DSP Denoising, Multi-Notch Filtering,
 * and Noise Gating. Manages persistence, active filters, and listener notifications.
 */
public class DenoisingConfigurationManager {

    private static final DenoisingConfigurationManager INSTANCE = new DenoisingConfigurationManager();

    public static DenoisingConfigurationManager getInstance() {
        return INSTANCE;
    }

    // Config keys
    public static final String CFG_DENOISING_ENABLED = "PRPDMonitor.denoising.global_enabled";
    public static final String CFG_NOTCH_ENABLED = "PRPDMonitor.denoising.notch_enabled";
    public static final String CFG_NOISE_GATE_ENABLED = "PRPDMonitor.denoising.noise_gate_enabled";
    public static final String CFG_NOISE_GATE_THRESH = "PRPDMonitor.denoising.noise_gate_threshold_mv";
    public static final String CFG_NOISE_GATE_ATTEN = "PRPDMonitor.denoising.noise_gate_attenuation_db";

    private boolean globalEnabled = false;
    private final AdaptiveNotchFilter notchFilter = new AdaptiveNotchFilter();
    private final NoiseGateFilter noiseGate = new NoiseGateFilter();

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private DenoisingConfigurationManager() {
    }

    public synchronized boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public synchronized void setGlobalEnabled(boolean enabled) {
        if (this.globalEnabled != enabled) {
            this.globalEnabled = enabled;
            notifyListeners();
        }
    }

    public AdaptiveNotchFilter getNotchFilter() {
        return notchFilter;
    }

    public NoiseGateFilter getNoiseGate() {
        return noiseGate;
    }

    public void addChangeListener(Runnable r) {
        if (r != null && !changeListeners.contains(r)) {
            changeListeners.add(r);
        }
    }

    public void removeChangeListener(Runnable r) {
        changeListeners.remove(r);
    }

    public void notifyListeners() {
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    public void loadFromConfig(Configuration config) {
        if (config == null) return;
        try {
            String gen = config.getValue(CFG_DENOISING_ENABLED);
            if (gen != null) globalEnabled = Boolean.parseBoolean(gen);

            String ngEn = config.getValue(CFG_NOISE_GATE_ENABLED);
            if (ngEn != null) noiseGate.setEnabled(Boolean.parseBoolean(ngEn));

            String ngTh = config.getValue(CFG_NOISE_GATE_THRESH);
            if (ngTh != null) noiseGate.setThresholdMv(Double.parseDouble(ngTh));

            String ngAtt = config.getValue(CFG_NOISE_GATE_ATTEN);
            if (ngAtt != null) {
                double db = Double.parseDouble(ngAtt);
                double factor = Math.pow(10.0, db / 20.0);
                noiseGate.setAttenuationFactor(factor);
            }

            // Load notch slots
            List<AdaptiveNotchFilter.NotchSlot> slots = notchFilter.getSlots();
            for (int i = 0; i < slots.size(); i++) {
                String enKey = "PRPDMonitor.denoising.notch." + i + ".enabled";
                String freqKey = "PRPDMonitor.denoising.notch." + i + ".freq";
                String qKey = "PRPDMonitor.denoising.notch." + i + ".q";

                String enStr = config.getValue(enKey);
                String freqStr = config.getValue(freqKey);
                String qStr = config.getValue(qKey);

                if (enStr != null && freqStr != null && qStr != null) {
                    slots.get(i).enabled = Boolean.parseBoolean(enStr);
                    slots.get(i).freqHz = Double.parseDouble(freqStr);
                    slots.get(i).q = Double.parseDouble(qStr);
                }
            }
        } catch (Exception ex) {
            System.err.println("Błąd odczytu konfiguracji odszumiania DSP: " + ex.getMessage());
        }
    }

    public void saveToConfig(Configuration config) {
        if (config == null) return;
        try {
            config.saveValue(CFG_DENOISING_ENABLED, Boolean.toString(globalEnabled));
            config.saveValue(CFG_NOISE_GATE_ENABLED, Boolean.toString(noiseGate.isEnabled()));
            config.saveValue(CFG_NOISE_GATE_THRESH, String.format(Locale.US, "%.2f", noiseGate.getThresholdMv()));

            double db = 20.0 * Math.log10(Math.max(1e-4, noiseGate.getAttenuationFactor()));
            config.saveValue(CFG_NOISE_GATE_ATTEN, String.format(Locale.US, "%.1f", db));

            List<AdaptiveNotchFilter.NotchSlot> slots = notchFilter.getSlots();
            for (int i = 0; i < slots.size(); i++) {
                AdaptiveNotchFilter.NotchSlot s = slots.get(i);
                config.saveValue("PRPDMonitor.denoising.notch." + i + ".enabled", Boolean.toString(s.enabled));
                config.saveValue("PRPDMonitor.denoising.notch." + i + ".freq", String.format(Locale.US, "%.1f", s.freqHz));
                config.saveValue("PRPDMonitor.denoising.notch." + i + ".q", String.format(Locale.US, "%.1f", s.q));
            }
        } catch (Exception ex) {
            System.err.println("Błąd zapisu konfiguracji odszumiania DSP: " + ex.getMessage());
        }
    }
}
