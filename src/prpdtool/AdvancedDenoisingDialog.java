package prpdtool;

import dsp.*;
import dsp.FastFourierTransform.SpectrumResult;
import dsp.FastFourierTransform.WindowType;
import pipeline.Buffer;
import pipeline.DynamicPRPDHistogram;
import pipeline.PRPDHistogram;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Interactive DSP Denoising Control Center & Harmonic Notch Filter Bank
 * compliant with IEC 62478 and CIGRE WG D1.33.
 * Features:
 * - Live Dual FFT Spectrum Scope (Raw vs Denoised) with Notch Markers
 * - Automatic NBI Harmonic Spike Scanner (Auto-Detect Peaks)
 * - 5-Slot Multi-Notch IIR Filter Bank with Q-factor adjustment
 * - Soft-Knee Adaptive Noise Gate with RMS background noise meter
 * - Time-Domain Waveform Before/After Scope with delta-SNR readout
 */
public class AdvancedDenoisingDialog extends JDialog {

    private final PRPDTool parent;
    private final Configuration configuration;
    private final boolean isDarkMode;
    private final DenoisingConfigurationManager denoisingMgr = DenoisingConfigurationManager.getInstance();

    private JCheckBox globalEnableCb;
    private JLabel statusBannerLbl;

    // Notch slots UI components
    private JCheckBox[] notchEnCbs;
    private JTextField[] notchFreqFields;
    private JSlider[] notchQSliders;
    private JLabel[] notchQLabels;
    private JTextField[] notchDescFields;

    // Noise gate components
    private JCheckBox gateEnCb;
    private JSlider gateThreshSlider;
    private JLabel gateThreshLbl;
    private JComboBox<String> gateAttenCombo;
    private JLabel measuredNoiseRmsLbl;

    // Charts
    private DenoisingSpectrumPanel spectrumPanel;
    private DenoisingWaveformPanel waveformPanel;
    private JLabel snrImprovementLbl;
    private JLabel autoDetectStatusLbl;

    public AdvancedDenoisingDialog(PRPDTool owner, Configuration config, boolean isDarkMode) {
        super(owner, "Zaawansowane Odszumianie DSP i Filtry Notch (IEC 62478 / CIGRE)", true);
        this.parent = owner;
        this.configuration = config;
        this.isDarkMode = isDarkMode;

        setSize(1100, 780);
        setMinimumSize(new Dimension(960, 680));
        setLocationRelativeTo(owner);

        initUI();
        updateFromManager();
        refreshDisplaysFromParentData();
    }

    private void initUI() {
        Color bg = isDarkMode ? new Color(24, 26, 30) : new Color(245, 247, 250);
        Color panelBg = isDarkMode ? new Color(30, 33, 38) : Color.WHITE;
        Color borderCol = isDarkMode ? new Color(55, 60, 68) : new Color(210, 215, 225);
        Color accentCol = isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout());

        // 1. TOP BANNER & GLOBAL SWITCH
        JPanel topBanner = new JPanel(new BorderLayout(12, 0));
        topBanner.setBackground(panelBg);
        topBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
                new EmptyBorder(10, 16, 10, 16)
        ));

        JPanel leftBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftBanner.setOpaque(false);

        globalEnableCb = new JCheckBox("AKTYWUJ ZAAWANSOWANE ODSZUMIANIE DSP (DENOISING PIPELINE)", denoisingMgr.isGlobalEnabled());
        globalEnableCb.setFont(new Font("Segoe UI", Font.BOLD, 13));
        globalEnableCb.setForeground(accentCol);
        globalEnableCb.addActionListener(e -> {
            denoisingMgr.setGlobalEnabled(globalEnableCb.isSelected());
            updateStatusBanner();
            refreshDisplaysFromParentData();
        });
        leftBanner.add(globalEnableCb);

        statusBannerLbl = new JLabel();
        statusBannerLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        leftBanner.add(statusBannerLbl);
        updateStatusBanner();

        topBanner.add(leftBanner, BorderLayout.WEST);

        JButton autoScanBtn = new JButton("🔍 Automatycznie skanuj i wykryj zakłócenia NBI");
        autoScanBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        autoScanBtn.setBackground(new Color(245, 158, 11));
        autoScanBtn.setForeground(Color.BLACK);
        autoScanBtn.addActionListener(e -> runAutoNotchDetection());
        topBanner.add(autoScanBtn, BorderLayout.EAST);

        add(topBanner, BorderLayout.NORTH);

        // 2. MAIN CENTER CONTENT (Split into Charts at Top and Controls at Bottom)
        JPanel mainContent = new JPanel(new GridLayout(2, 1, 0, 8));
        mainContent.setBackground(bg);
        mainContent.setBorder(new EmptyBorder(8, 12, 8, 12));

        // TOP HALF: CHARTS (Dual FFT Spectrum + Waveform Preview)
        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        chartsPanel.setOpaque(false);

        spectrumPanel = new DenoisingSpectrumPanel();
        waveformPanel = new DenoisingWaveformPanel();

        chartsPanel.add(spectrumPanel);
        chartsPanel.add(waveformPanel);
        mainContent.add(chartsPanel);

        // BOTTOM HALF: CONTROLS (Notch Bank Left, Noise Gate Right)
        JPanel controlsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        controlsPanel.setOpaque(false);

        // NOTCH BANK PANEL
        JPanel notchPanel = new JPanel(new BorderLayout(0, 6));
        notchPanel.setBackground(panelBg);
        notchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "Bank Filtrów Wycinających Notch (NBI Suppression Bank)",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), accentCol),
                new EmptyBorder(6, 10, 8, 10)
        ));

        JPanel slotsGrid = new JPanel(new GridLayout(5, 1, 0, 4));
        slotsGrid.setOpaque(false);

        List<AdaptiveNotchFilter.NotchSlot> slots = denoisingMgr.getNotchFilter().getSlots();
        int nSlots = Math.min(5, slots.size());
        notchEnCbs = new JCheckBox[nSlots];
        notchFreqFields = new JTextField[nSlots];
        notchQSliders = new JSlider[nSlots];
        notchQLabels = new JLabel[nSlots];
        notchDescFields = new JTextField[nSlots];

        for (int i = 0; i < nSlots; i++) {
            AdaptiveNotchFilter.NotchSlot slot = slots.get(i);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setOpaque(false);

            notchEnCbs[i] = new JCheckBox("Notch " + (i + 1), slot.enabled);
            notchEnCbs[i].setFont(new Font("Segoe UI", Font.BOLD, 11));
            final int slotIdx = i;
            notchEnCbs[i].addActionListener(e -> applyNotchSlotChange(slotIdx));

            double fKhz = slot.freqHz / 1e3;
            notchFreqFields[i] = new JTextField(String.format(Locale.US, "%.1f", fKhz), 6);
            notchFreqFields[i].setToolTipText("Częstotliwość środkowa wycięcia [kHz]");
            notchFreqFields[i].addActionListener(e -> applyNotchSlotChange(slotIdx));

            notchQSliders[i] = new JSlider(5, 50, (int) Math.round(slot.q));
            notchQSliders[i].setPreferredSize(new Dimension(80, 20));
            notchQSliders[i].setBackground(panelBg);
            notchQLabels[i] = new JLabel("Q=" + (int) slot.q);
            notchQLabels[i].setPreferredSize(new Dimension(36, 18));
            notchQSliders[i].addChangeListener(e -> {
                notchQLabels[slotIdx].setText("Q=" + notchQSliders[slotIdx].getValue());
                applyNotchSlotChange(slotIdx);
            });

            notchDescFields[i] = new JTextField(slot.description, 14);
            notchDescFields[i].setToolTipText("Opis źródła zakłócenia");
            notchDescFields[i].addActionListener(e -> applyNotchSlotChange(slotIdx));

            row.add(notchEnCbs[i]);
            row.add(new JLabel("f:"));
            row.add(notchFreqFields[i]);
            row.add(new JLabel("kHz"));
            row.add(notchQSliders[i]);
            row.add(notchQLabels[i]);
            row.add(notchDescFields[i]);

            slotsGrid.add(row);
        }
        notchPanel.add(slotsGrid, BorderLayout.CENTER);

        // Presets buttons
        JPanel presetBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        presetBar.setOpaque(false);
        JButton p1Btn = new JButton("Preset: Falowniki (100k, 500k)");
        p1Btn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        p1Btn.addActionListener(e -> applyPresetFalowniki());

        JButton p2Btn = new JButton("Preset: Radio/NBI (1.25M, 2.5M, 10.7M)");
        p2Btn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        p2Btn.addActionListener(e -> applyPresetRadio());

        JButton clearNotchBtn = new JButton("Wyłącz wszystkie");
        clearNotchBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        clearNotchBtn.addActionListener(e -> disableAllNotches());

        presetBar.add(p1Btn);
        presetBar.add(p2Btn);
        presetBar.add(clearNotchBtn);
        notchPanel.add(presetBar, BorderLayout.SOUTH);

        controlsPanel.add(notchPanel);

        // NOISE GATE PANEL
        JPanel gatePanel = new JPanel(new BorderLayout(0, 6));
        gatePanel.setBackground(panelBg);
        gatePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "Bramka Szumowa i Tłumienie Tła (Noise Gate)",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), accentCol),
                new EmptyBorder(6, 12, 8, 12)
        ));

        JPanel gateControls = new JPanel();
        gateControls.setLayout(new BoxLayout(gateControls, BoxLayout.Y_AXIS));
        gateControls.setOpaque(false);

        gateEnCb = new JCheckBox("Aktywuj Bramkę Szumową (Noise Gating)", denoisingMgr.getNoiseGate().isEnabled());
        gateEnCb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gateEnCb.setForeground(accentCol);
        gateEnCb.addActionListener(e -> {
            denoisingMgr.getNoiseGate().setEnabled(gateEnCb.isSelected());
            refreshDisplaysFromParentData();
        });
        gateControls.add(gateEnCb);
        gateControls.add(Box.createVerticalStrut(8));

        JPanel threshRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        threshRow.setOpaque(false);
        threshRow.add(new JLabel("Próg odcięcia szumu T_gate:"));

        gateThreshSlider = new JSlider(5, 500, (int) Math.round(denoisingMgr.getNoiseGate().getThresholdMv() * 10.0));
        gateThreshSlider.setPreferredSize(new Dimension(140, 22));
        gateThreshSlider.setBackground(panelBg);
        gateThreshLbl = new JLabel(String.format(Locale.US, "%.1f mV", denoisingMgr.getNoiseGate().getThresholdMv()));
        gateThreshSlider.addChangeListener(e -> {
            double mv = gateThreshSlider.getValue() / 10.0;
            gateThreshLbl.setText(String.format(Locale.US, "%.1f mV", mv));
            denoisingMgr.getNoiseGate().setThresholdMv(mv);
            refreshDisplaysFromParentData();
        });
        threshRow.add(gateThreshSlider);
        threshRow.add(gateThreshLbl);
        gateControls.add(threshRow);
        gateControls.add(Box.createVerticalStrut(8));

        JPanel attenRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        attenRow.setOpaque(false);
        attenRow.add(new JLabel("Poziom tłumienia tła:"));
        gateAttenCombo = new JComboBox<>(new String[]{
                "-12 dB (Umiarkowane tłumienie tła x0.25)",
                "-18 dB (Zalecane standardowe x0.125)",
                "-24 dB (Głębokie tłumienie szumu x0.06)",
                "Pełne wyciszenie tła (0 mV - Hard Gating)"
        });
        gateAttenCombo.setSelectedIndex(1);
        gateAttenCombo.addActionListener(e -> {
            int idx = gateAttenCombo.getSelectedIndex();
            double factor = switch (idx) {
                case 0 -> 0.25;
                case 2 -> 0.0625;
                case 3 -> 0.0;
                default -> 0.125;
            };
            denoisingMgr.getNoiseGate().setAttenuationFactor(factor);
            refreshDisplaysFromParentData();
        });
        attenRow.add(gateAttenCombo);
        gateControls.add(attenRow);
        gateControls.add(Box.createVerticalStrut(10));

        measuredNoiseRmsLbl = new JLabel("Szacowany szum tła RMS: -- mV");
        measuredNoiseRmsLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        measuredNoiseRmsLbl.setForeground(isDarkMode ? new Color(160, 175, 195) : new Color(80, 95, 110));
        gateControls.add(measuredNoiseRmsLbl);

        gatePanel.add(gateControls, BorderLayout.CENTER);

        autoDetectStatusLbl = new JLabel("Gotowy do analizy zakłóceń NBI.");
        autoDetectStatusLbl.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        autoDetectStatusLbl.setForeground(isDarkMode ? new Color(140, 150, 165) : new Color(110, 120, 130));
        gatePanel.add(autoDetectStatusLbl, BorderLayout.SOUTH);

        controlsPanel.add(gatePanel);
        mainContent.add(controlsPanel);

        add(mainContent, BorderLayout.CENTER);

        // 3. BOTTOM ACTION BAR
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(panelBg);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol),
                new EmptyBorder(10, 16, 10, 16)
        ));

        snrImprovementLbl = new JLabel("Szacowana poprawa SNR: -- dB");
        snrImprovementLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        snrImprovementLbl.setForeground(new Color(16, 185, 129));
        bottomBar.add(snrImprovementLbl, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);

        JButton saveCfgBtn = new JButton("Zapisz jako domyślne w konfiguracji");
        saveCfgBtn.addActionListener(e -> {
            denoisingMgr.saveToConfig(configuration);
            JOptionPane.showMessageDialog(this, "Parametry odszumiania DSP zostały zapisane w konfiguracji!", "Zapisano", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton applyBtn = new JButton("Zastosuj do Toru Pomiarowego");
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.setBackground(new Color(2, 132, 199));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.addActionListener(e -> {
            denoisingMgr.notifyListeners();
            refreshDisplaysFromParentData();
            JOptionPane.showMessageDialog(this, "Parametry odszumiania zostały zaktualizowane w aktywnym torze akwizycji!", "Zastosowano", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.addActionListener(e -> setVisible(false));

        rightBtns.add(saveCfgBtn);
        rightBtns.add(applyBtn);
        rightBtns.add(closeBtn);
        bottomBar.add(rightBtns, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void updateStatusBanner() {
        if (denoisingMgr.isGlobalEnabled()) {
            statusBannerLbl.setText("STATUS: AKTYWNY (Wycinanie NBI + Bramka Szumowa WŁĄCZONE)");
            statusBannerLbl.setForeground(new Color(16, 185, 129));
        } else {
            statusBannerLbl.setText("STATUS: WYŁĄCZONY (Standardowy tor HPF)");
            statusBannerLbl.setForeground(new Color(156, 163, 175));
        }
    }

    private void updateFromManager() {
        globalEnableCb.setSelected(denoisingMgr.isGlobalEnabled());
        gateEnCb.setSelected(denoisingMgr.getNoiseGate().isEnabled());
        gateThreshSlider.setValue((int) Math.round(denoisingMgr.getNoiseGate().getThresholdMv() * 10.0));
        gateThreshLbl.setText(String.format(Locale.US, "%.1f mV", denoisingMgr.getNoiseGate().getThresholdMv()));

        List<AdaptiveNotchFilter.NotchSlot> slots = denoisingMgr.getNotchFilter().getSlots();
        for (int i = 0; i < Math.min(slots.size(), notchEnCbs.length); i++) {
            AdaptiveNotchFilter.NotchSlot s = slots.get(i);
            notchEnCbs[i].setSelected(s.enabled);
            notchFreqFields[i].setText(String.format(Locale.US, "%.1f", s.freqHz / 1e3));
            notchQSliders[i].setValue((int) Math.round(s.q));
            notchQLabels[i].setText("Q=" + (int) s.q);
            notchDescFields[i].setText(s.description);
        }
    }

    private void applyNotchSlotChange(int idx) {
        try {
            boolean en = notchEnCbs[idx].isSelected();
            double fKhz = Double.parseDouble(notchFreqFields[idx].getText().trim().replace(',', '.'));
            double fHz = fKhz * 1e3;
            double q = notchQSliders[idx].getValue();
            String desc = notchDescFields[idx].getText().trim();

            denoisingMgr.getNotchFilter().setSlot(idx, en, fHz, q, desc);
            refreshDisplaysFromParentData();
        } catch (Exception ex) {
            System.err.println("Błąd zapisu slotu notch: " + ex.getMessage());
        }
    }

    private void applyPresetFalowniki() {
        denoisingMgr.getNotchFilter().setSlot(0, true, 100_000.0, 25.0, "Falownik PWM 100 kHz");
        denoisingMgr.getNotchFilter().setSlot(1, true, 300_000.0, 25.0, "Przetwornica 300 kHz");
        denoisingMgr.getNotchFilter().setSlot(2, true, 500_000.0, 30.0, "Przetwornica DC/DC 500 kHz");
        updateFromManager();
        refreshDisplaysFromParentData();
    }

    private void applyPresetRadio() {
        denoisingMgr.getNotchFilter().setSlot(0, true, 1_250_000.0, 30.0, "Nadajnik AM 1.25 MHz");
        denoisingMgr.getNotchFilter().setSlot(1, true, 2_500_000.0, 35.0, "Radiolinia NBI 2.5 MHz");
        denoisingMgr.getNotchFilter().setSlot(2, true, 10_700_000.0, 40.0, "P.cz. odbiornika 10.7 MHz");
        updateFromManager();
        refreshDisplaysFromParentData();
    }

    private void disableAllNotches() {
        List<AdaptiveNotchFilter.NotchSlot> slots = denoisingMgr.getNotchFilter().getSlots();
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).enabled = false;
        }
        updateFromManager();
        refreshDisplaysFromParentData();
    }

    private void runAutoNotchDetection() {
        double[] rawSignal = getSampleSignalFromParent();
        if (rawSignal == null || rawSignal.length < 512) {
            JOptionPane.showMessageDialog(this, "Brak dostępnego sygnału do analizy NBI. Załaduj plik pomiarowy lub włącz Red Pitaya.", "Brak danych", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double fs = parent.getF0() > 0 ? 125e6 : 125e6;
        List<Double> spikes = AdaptiveNotchFilter.detectHarmonicSpikes(rawSignal, fs, 14.0, notchEnCbs.length);

        if (spikes.isEmpty()) {
            autoDetectStatusLbl.setText("Nie wykryto wyraźnych szpilek harmonicznych NBI (> 14 dB nad tłem).");
            JOptionPane.showMessageDialog(this, "Skanowanie FFT nie wykryło silnych, wąskopasmowych zakłóceń ciągłych (NBI) przekraczających tło o 14 dB.", "Wynik skanowania", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Apply detected spikes to notch slots
        disableAllNotches();
        for (int i = 0; i < spikes.size() && i < notchEnCbs.length; i++) {
            double freqHz = spikes.get(i);
            String desc = String.format(Locale.US, "Wykryte NBI %.2f MHz", freqHz / 1e6);
            denoisingMgr.getNotchFilter().setSlot(i, true, freqHz, 30.0, desc);
        }

        denoisingMgr.setGlobalEnabled(true);
        updateFromManager();
        refreshDisplaysFromParentData();

        autoDetectStatusLbl.setText("Wykryto i włączono " + spikes.size() + " filtrów notch na częstotliwościach zakłóceń.");
        JOptionPane.showMessageDialog(this, "Automatycznie wykryto i skonfigurowano " + spikes.size() + " filtrów Notch dla dominujących zakłóceń NBI!", "Wykryto zakłócenia", JOptionPane.INFORMATION_MESSAGE);
    }

    private double[] getSampleSignalFromParent() {
        // Look for representative buffer or pulse from repository
        pipeline.PulseWaveform pw = pipeline.PulseWaveformRepository.getInstance().getMaxPulse();
        if (pw != null && pw.filtered != null) {
            return pw.filtered;
        }
        return null;
    }

    public void refreshDisplaysFromParentData() {
        double[] sig = getSampleSignalFromParent();
        if (sig == null || sig.length < 128) {
            // Synthesize noisy demonstration pulse if no live data loaded yet
            sig = generateDemoSignal();
        }

        double fs = 125e6;
        denoisingMgr.getNotchFilter().setFs(fs);

        // Filter signal through active pipeline
        double[] denoised = sig.clone();
        if (denoisingMgr.isGlobalEnabled()) {
            denoised = denoisingMgr.getNotchFilter().filter(denoised, denoised.length);
            denoised = denoisingMgr.getNoiseGate().filter(denoised, denoised.length);
        }

        // Compute FFT spectra
        SpectrumResult rawSpec = FastFourierTransform.computeSpectrum(sig, fs, WindowType.BLACKMAN_HARRIS);
        SpectrumResult denSpec = FastFourierTransform.computeSpectrum(denoised, fs, WindowType.BLACKMAN_HARRIS);

        // Estimate SNR improvement
        double rawRms = calcRms(sig);
        double denRms = calcRms(denoised);
        measuredNoiseRmsLbl.setText(String.format(Locale.US, "Szacowany szum tła RMS: %.2f mV", denRms));

        double deltaSnr = 20.0 * Math.log10(Math.max(1e-3, rawRms / Math.max(1e-3, denRms)));
        if (denoisingMgr.isGlobalEnabled()) {
            snrImprovementLbl.setText(String.format(Locale.US, "Poprawa SNR: +%.1f dB (Odszumianie aktywne)", Math.max(0.0, deltaSnr)));
        } else {
            snrImprovementLbl.setText("Poprawa SNR: 0.0 dB (Odszumianie wyłączone)");
        }

        spectrumPanel.setData(rawSpec, denSpec, denoisingMgr.getNotchFilter().getSlots());
        waveformPanel.setData(sig, denoised);
    }

    private double calcRms(double[] arr) {
        double sum = 0.0;
        for (double v : arr) sum += v * v;
        return Math.sqrt(sum / Math.max(1, arr.length));
    }

    private double[] generateDemoSignal() {
        int n = 1024;
        double[] s = new double[n];
        double fs = 125e6;
        for (int i = 0; i < n; i++) {
            double t = (double) i / fs;
            // 500 kHz interference + 2.5 MHz radio harmonic
            double nbi = 8.0 * Math.sin(2.0 * Math.PI * 500_000.0 * t) + 6.0 * Math.sin(2.0 * Math.PI * 2_500_000.0 * t);
            // Random thermal noise
            double whiteNoise = (Math.random() - 0.5) * 3.0;
            // PD pulse transient at index 256
            double pd = 0.0;
            if (i >= 256 && i < 256 + 120) {
                double dt = (i - 256) / fs;
                pd = 45.0 * (Math.exp(-dt / 150e-9) - Math.exp(-dt / 20e-9));
            }
            s[i] = nbi + whiteNoise + pd;
        }
        return s;
    }

    // SPECTRUM CHART PANEL
    private class DenoisingSpectrumPanel extends JPanel {
        private SpectrumResult rawSpec;
        private SpectrumResult denSpec;
        private List<AdaptiveNotchFilter.NotchSlot> notchSlots;

        public DenoisingSpectrumPanel() {
            setBackground(isDarkMode ? new Color(18, 20, 24) : new Color(250, 252, 255));
        }

        public void setData(SpectrumResult raw, SpectrumResult den, List<AdaptiveNotchFilter.NotchSlot> notches) {
            this.rawSpec = raw;
            this.denSpec = den;
            this.notchSlots = notches;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(isDarkMode ? new Color(18, 20, 24) : Color.WHITE);
            g2.fillRect(0, 0, w, h);

            int padL = 50, padR = 20, padT = 28, padB = 32;
            int plotW = w - padL - padR;
            int plotH = h - padT - padB;

            // Title
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(0, 210, 255));
            g2.drawString("WIDMO CZĘSTOTLIWOŚCIOWE FFT (SUROWE vs ODSZUMIONE)", padL, padT - 8);

            if (plotW <= 20 || plotH <= 20 || rawSpec == null) {
                g2.dispose();
                return;
            }

            // Grid & Box
            g2.setColor(isDarkMode ? new Color(16, 18, 22) : new Color(248, 250, 252));
            g2.fillRect(padL, padT, plotW, plotH);
            g2.setColor(isDarkMode ? new Color(45, 50, 60) : new Color(220, 225, 235));
            g2.drawRect(padL, padT, plotW, plotH);

            double minDb = -40.0;
            double maxDb = 40.0;
            double maxFreq = rawSpec.frequencies[rawSpec.frequencies.length - 1];

            // Draw Raw Spectrum (Orange)
            drawSpectrumPath(g2, rawSpec, padL, padT, plotW, plotH, minDb, maxDb, maxFreq, new Color(245, 158, 11, 160));

            // Draw Denoised Spectrum (Cyan)
            if (denSpec != null) {
                drawSpectrumPath(g2, denSpec, padL, padT, plotW, plotH, minDb, maxDb, maxFreq, new Color(0, 215, 255, 230));
            }

            // Draw Notch Markers (Red dashed vertical lines)
            if (notchSlots != null) {
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
                for (AdaptiveNotchFilter.NotchSlot slot : notchSlots) {
                    if (slot.enabled && slot.freqHz > 0 && slot.freqHz < maxFreq) {
                        int nx = padL + (int) Math.round((slot.freqHz / maxFreq) * plotW);
                        g2.drawLine(nx, padT, nx, padT + plotH);
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                        g2.drawString(String.format(Locale.US, "%.1fk", slot.freqHz / 1e3), nx - 12, padT + 12);
                    }
                }
            }

            // Axis labels
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(isDarkMode ? Color.GRAY : Color.DARK_GRAY);
            g2.drawString("+40 dB", padL - 42, padT + 10);
            g2.drawString("0 dB", padL - 30, padT + plotH / 2);
            g2.drawString("-40 dB", padL - 42, padT + plotH);
            g2.drawString("0 MHz", padL, padT + plotH + 16);
            g2.drawString(String.format(Locale.US, "%.1f MHz", maxFreq / 1e6), padL + plotW - 45, padT + plotH + 16);

            g2.dispose();
        }

        private void drawSpectrumPath(Graphics2D g2, SpectrumResult spec, int padL, int padT, int plotW, int plotH,
                                      double minDb, double maxDb, double maxFreq, Color col) {
            g2.setColor(col);
            g2.setStroke(new BasicStroke(1.4f));
            Path2D.Double path = new Path2D.Double();

            for (int i = 0; i < spec.frequencies.length; i++) {
                double f = spec.frequencies[i];
                double dbVal = Math.max(minDb, Math.min(maxDb, spec.magnitudesDb[i]));
                double px = padL + (f / maxFreq) * plotW;
                double py = padT + plotH - ((dbVal - minDb) / (maxDb - minDb)) * plotH;
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            g2.draw(path);
        }
    }

    // TIME DOMAIN WAVEFORM PANEL
    private class DenoisingWaveformPanel extends JPanel {
        private double[] rawSignal;
        private double[] denoisedSignal;

        public DenoisingWaveformPanel() {
            setBackground(isDarkMode ? new Color(18, 20, 24) : new Color(250, 252, 255));
        }

        public void setData(double[] raw, double[] den) {
            this.rawSignal = raw;
            this.denoisedSignal = den;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(isDarkMode ? new Color(18, 20, 24) : Color.WHITE);
            g2.fillRect(0, 0, w, h);

            int padL = 50, padR = 20, padT = 28, padB = 32;
            int plotW = w - padL - padR;
            int plotH = h - padT - padB;

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(16, 185, 129));
            g2.drawString("PRZEBIEG CZASOWY (POMARAŃCZOWY=SUROWY, BŁĘKITNY=ODSZUMIONY)", padL, padT - 8);

            if (plotW <= 20 || plotH <= 20 || rawSignal == null) {
                g2.dispose();
                return;
            }

            g2.setColor(isDarkMode ? new Color(16, 18, 22) : new Color(248, 250, 252));
            g2.fillRect(padL, padT, plotW, plotH);
            g2.setColor(isDarkMode ? new Color(45, 50, 60) : new Color(220, 225, 235));
            g2.drawRect(padL, padT, plotW, plotH);

            double maxAmp = 1.0;
            for (double v : rawSignal) if (Math.abs(v) > maxAmp) maxAmp = Math.abs(v);
            maxAmp *= 1.25;

            int midY = padT + plotH / 2;
            g2.setColor(isDarkMode ? new Color(40, 44, 52) : new Color(230, 235, 242));
            g2.drawLine(padL, midY, padL + plotW, midY);

            // Raw signal
            drawSignal(g2, rawSignal, padL, plotW, midY, plotH, maxAmp, new Color(245, 158, 11, 140), 1.0f);

            // Denoised signal
            if (denoisedSignal != null) {
                drawSignal(g2, denoisedSignal, padL, plotW, midY, plotH, maxAmp, new Color(0, 215, 255), 1.8f);
            }

            // Axis labels
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(isDarkMode ? Color.GRAY : Color.DARK_GRAY);
            g2.drawString("+" + String.format(Locale.US, "%.0f mV", maxAmp), padL - 45, padT + 10);
            g2.drawString("0", padL - 16, midY + 4);
            g2.drawString("-" + String.format(Locale.US, "%.0f mV", maxAmp), padL - 45, padT + plotH);
            g2.drawString("0 ns", padL, padT + plotH + 16);
            g2.drawString("Czas t [próbki]", padL + plotW / 2 - 35, padT + plotH + 20);

            g2.dispose();
        }

        private void drawSignal(Graphics2D g2, double[] sig, int padL, int plotW, int midY, int plotH, double maxAmp, Color col, float stroke) {
            g2.setColor(col);
            g2.setStroke(new BasicStroke(stroke));
            Path2D.Double path = new Path2D.Double();

            for (int i = 0; i < sig.length; i++) {
                double px = padL + ((double) i / sig.length) * plotW;
                double py = midY - (sig[i] / maxAmp) * (plotH / 2.0);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            g2.draw(path);
        }
    }
}
