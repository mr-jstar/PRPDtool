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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
 * - Integrated Comprehensive Guide and Documentation Dialog [?]
 */
public class AdvancedDenoisingDialog extends JDialog {

    private final PRPDTool parent;
    private final Configuration configuration;
    private final boolean isDarkMode;
    private final DenoisingConfigurationManager denoisingMgr = DenoisingConfigurationManager.getInstance();
    private final AdvancedDenoisingHelpDialog helpDialog;

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
    private JButton autoRmsBtn;
    private double lastMeasuredRms = 2.5;

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
        this.helpDialog = new AdvancedDenoisingHelpDialog(this, isDarkMode);

        setSize(1320, 840);
        setMinimumSize(new Dimension(1080, 700));
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

        // 1. TOP BANNER & GLOBAL SWITCH & HELP BUTTON
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

        JPanel rightBanner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBanner.setOpaque(false);

        JButton autoScanBtn = new JButton("Skanuj NBI (Auto-Scan)");
        autoScanBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        autoScanBtn.setBackground(new Color(245, 158, 11));
        autoScanBtn.setForeground(Color.BLACK);
        autoScanBtn.setToolTipText("Skanuje widmo FFT surowego sygnału i automatycznie wstawia filtry Notch na wykryte szpilki NBI");
        autoScanBtn.addActionListener(e -> runAutoNotchDetection());
        rightBanner.add(autoScanBtn);

        JButton helpBtn = new JButton("Przewodnik i pomoc [?]");
        helpBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        helpBtn.setForeground(new Color(0, 190, 245));
        helpBtn.setToolTipText("Otwórz przewodnik po filtracji DSP, doborze parametrów i ich wpływie na PRPD");
        helpBtn.addActionListener(e -> helpDialog.showTopic("OVERVIEW"));
        rightBanner.add(helpBtn);

        topBanner.add(rightBanner, BorderLayout.EAST);

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

        // --- NOTCH BANK PANEL ---
        JPanel notchPanel = new JPanel(new BorderLayout(0, 6));
        notchPanel.setBackground(panelBg);
        notchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderCol),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel notchHeaderBar = new JPanel(new BorderLayout());
        notchHeaderBar.setOpaque(false);
        JLabel notchTitleLbl = new JLabel("Bank Filtrów Wycinających Notch (NBI Suppression Bank)");
        notchTitleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        notchTitleLbl.setForeground(accentCol);

        JButton helpNotchBtn = new JButton("Objaśnienia Notch [?]");
        helpNotchBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        helpNotchBtn.setForeground(new Color(0, 190, 245));
        helpNotchBtn.setMargin(new Insets(2, 6, 2, 6));
        helpNotchBtn.setToolTipText("Szczegółowe wyjaśnienie działania filtrów wycinających, dobroci Q i NBI");
        helpNotchBtn.addActionListener(e -> helpDialog.showTopic("NOTCH"));

        notchHeaderBar.add(notchTitleLbl, BorderLayout.WEST);
        notchHeaderBar.add(helpNotchBtn, BorderLayout.EAST);
        notchPanel.add(notchHeaderBar, BorderLayout.NORTH);

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
            notchEnCbs[i].setPreferredSize(new Dimension(84, 22));
            final int slotIdx = i;
            notchEnCbs[i].addActionListener(e -> applyNotchSlotChange(slotIdx));

            double fKhz = slot.freqHz / 1e3;
            notchFreqFields[i] = new JTextField(String.format(Locale.US, "%.1f", fKhz), 6);
            notchFreqFields[i].setPreferredSize(new Dimension(58, 22));
            notchFreqFields[i].setToolTipText("Częstotliwość środkowa wycięcia [kHz]");
            notchFreqFields[i].addActionListener(e -> applyNotchSlotChange(slotIdx));

            notchQSliders[i] = new JSlider(5, 50, (int) Math.round(slot.q));
            notchQSliders[i].setPreferredSize(new Dimension(75, 20));
            notchQSliders[i].setBackground(panelBg);
            notchQLabels[i] = new JLabel("Q=" + (int) slot.q);
            notchQLabels[i].setPreferredSize(new Dimension(38, 18));
            notchQSliders[i].addChangeListener(e -> {
                notchQLabels[slotIdx].setText("Q=" + notchQSliders[slotIdx].getValue());
                applyNotchSlotChange(slotIdx);
            });

            notchDescFields[i] = new JTextField(slot.description, 14);
            notchDescFields[i].setPreferredSize(new Dimension(145, 22));
            notchDescFields[i].setToolTipText("Opis źródła zakłócenia (np. Falownik, Radio AM)");
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

        // Presets & Status
        JPanel notchSouth = new JPanel(new BorderLayout(0, 4));
        notchSouth.setOpaque(false);

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
        notchSouth.add(presetBar, BorderLayout.NORTH);

        autoDetectStatusLbl = new JLabel("Gotowy do analizy zakłóceń NBI.");
        autoDetectStatusLbl.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        autoDetectStatusLbl.setForeground(isDarkMode ? new Color(140, 150, 165) : new Color(110, 120, 130));
        autoDetectStatusLbl.setBorder(new EmptyBorder(3, 4, 0, 0));
        notchSouth.add(autoDetectStatusLbl, BorderLayout.SOUTH);

        notchPanel.add(notchSouth, BorderLayout.SOUTH);
        controlsPanel.add(notchPanel);

        // --- NOISE GATE PANEL (CLEAN STRICT ALIGNMENT) ---
        JPanel gatePanel = new JPanel(new BorderLayout(0, 8));
        gatePanel.setBackground(panelBg);
        gatePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderCol),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Header bar with checkbox and help button
        JPanel gateHeaderBar = new JPanel(new BorderLayout());
        gateHeaderBar.setOpaque(false);

        gateEnCb = new JCheckBox("Aktywuj Bramkę Szumową (Noise Gating)", denoisingMgr.getNoiseGate().isEnabled());
        gateEnCb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        gateEnCb.setForeground(accentCol);
        gateEnCb.addActionListener(e -> {
            denoisingMgr.getNoiseGate().setEnabled(gateEnCb.isSelected());
            refreshDisplaysFromParentData();
        });

        JButton helpGateBtn = new JButton("Objaśnienia Bramki [?]");
        helpGateBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        helpGateBtn.setForeground(new Color(0, 190, 245));
        helpGateBtn.setMargin(new Insets(2, 6, 2, 6));
        helpGateBtn.setToolTipText("Wyjaśnienie zasady działania bramki szumowej, progu T_gate i tłumienia w dB");
        helpGateBtn.addActionListener(e -> helpDialog.showTopic("GATE"));

        gateHeaderBar.add(gateEnCb, BorderLayout.WEST);
        gateHeaderBar.add(helpGateBtn, BorderLayout.EAST);
        gatePanel.add(gateHeaderBar, BorderLayout.NORTH);

        // Strict 2-column GridBagLayout for aligned controls
        JPanel formGrid = new JPanel(new GridBagLayout());
        formGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 0, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // Row 0: Threshold
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblThresh = new JLabel("Próg odcięcia szumu (T_gate):");
        lblThresh.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblThresh.setPreferredSize(new Dimension(175, 24));
        formGrid.add(lblThresh, gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        JPanel threshCtrlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        threshCtrlPanel.setOpaque(false);

        gateThreshSlider = new JSlider(5, 500, (int) Math.round(denoisingMgr.getNoiseGate().getThresholdMv() * 10.0));
        gateThreshSlider.setPreferredSize(new Dimension(135, 22));
        gateThreshSlider.setBackground(panelBg);

        gateThreshLbl = new JLabel(String.format(Locale.US, "%.1f mV", denoisingMgr.getNoiseGate().getThresholdMv()));
        gateThreshLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gateThreshLbl.setForeground(accentCol);
        gateThreshLbl.setPreferredSize(new Dimension(52, 20));

        gateThreshSlider.addChangeListener(e -> {
            double mv = gateThreshSlider.getValue() / 10.0;
            gateThreshLbl.setText(String.format(Locale.US, "%.1f mV", mv));
            denoisingMgr.getNoiseGate().setThresholdMv(mv);
            refreshDisplaysFromParentData();
        });

        autoRmsBtn = new JButton("Ustaw z RMS (x2.0)");
        autoRmsBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        autoRmsBtn.setMargin(new Insets(2, 6, 2, 6));
        autoRmsBtn.setToolTipText("Automatycznie ustawia próg bramki na dwukrotność zmierzonego szumu tła RMS");
        autoRmsBtn.addActionListener(e -> {
            double target = Math.max(0.5, Math.min(50.0, lastMeasuredRms * 2.0));
            gateThreshSlider.setValue((int) Math.round(target * 10.0));
            gateThreshLbl.setText(String.format(Locale.US, "%.1f mV", target));
            denoisingMgr.getNoiseGate().setThresholdMv(target);
            refreshDisplaysFromParentData();
        });

        threshCtrlPanel.add(gateThreshSlider);
        threshCtrlPanel.add(gateThreshLbl);
        threshCtrlPanel.add(autoRmsBtn);
        formGrid.add(threshCtrlPanel, gbc);

        // Row 1: Attenuation
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lblAtten = new JLabel("Poziom tłumienia tła:");
        lblAtten.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblAtten.setPreferredSize(new Dimension(175, 24));
        formGrid.add(lblAtten, gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        JPanel attenCtrlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        attenCtrlPanel.setOpaque(false);

        gateAttenCombo = new JComboBox<>(new String[]{
                "-12 dB (Umiarkowane tłumienie tła x0.25)",
                "-18 dB (Zalecane standardowe x0.125)",
                "-24 dB (Głębokie tłumienie szumu x0.06)",
                "Pełne wyciszenie tła (0 mV - Hard Gating)"
        });
        gateAttenCombo.setPreferredSize(new Dimension(280, 24));
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
        attenCtrlPanel.add(gateAttenCombo);
        formGrid.add(attenCtrlPanel, gbc);

        // Row 2: Presets
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblPresets = new JLabel("Szybkie presety progu:");
        lblPresets.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPresets.setPreferredSize(new Dimension(175, 24));
        formGrid.add(lblPresets, gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        JPanel presetBtnsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        presetBtnsPanel.setOpaque(false);

        JButton labPresetBtn = new JButton("Lab (1.5 mV)");
        labPresetBtn.setMargin(new Insets(2, 6, 2, 6));
        labPresetBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        labPresetBtn.addActionListener(e -> {
            gateThreshSlider.setValue(15);
            gateThreshLbl.setText("1.5 mV");
            denoisingMgr.getNoiseGate().setThresholdMv(1.5);
            refreshDisplaysFromParentData();
        });

        JButton stdPresetBtn = new JButton("Standard (5.0 mV)");
        stdPresetBtn.setMargin(new Insets(2, 6, 2, 6));
        stdPresetBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        stdPresetBtn.addActionListener(e -> {
            gateThreshSlider.setValue(50);
            gateThreshLbl.setText("5.0 mV");
            denoisingMgr.getNoiseGate().setThresholdMv(5.0);
            refreshDisplaysFromParentData();
        });

        JButton fieldPresetBtn = new JButton("Obiekt SN (15.0 mV)");
        fieldPresetBtn.setMargin(new Insets(2, 6, 2, 6));
        fieldPresetBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        fieldPresetBtn.addActionListener(e -> {
            gateThreshSlider.setValue(150);
            gateThreshLbl.setText("15.0 mV");
            denoisingMgr.getNoiseGate().setThresholdMv(15.0);
            refreshDisplaysFromParentData();
        });

        presetBtnsPanel.add(labPresetBtn);
        presetBtnsPanel.add(stdPresetBtn);
        presetBtnsPanel.add(fieldPresetBtn);
        formGrid.add(presetBtnsPanel, gbc);

        // Row 3: Measured RMS
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel lblRms = new JLabel("Szacowany szum tła RMS:");
        lblRms.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblRms.setPreferredSize(new Dimension(175, 24));
        formGrid.add(lblRms, gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        JPanel rmsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rmsPanel.setOpaque(false);
        measuredNoiseRmsLbl = new JLabel("Szacowany szum tła RMS: -- mV");
        measuredNoiseRmsLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        measuredNoiseRmsLbl.setForeground(isDarkMode ? new Color(160, 175, 195) : new Color(60, 75, 90));
        rmsPanel.add(measuredNoiseRmsLbl);
        formGrid.add(rmsPanel, gbc);

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.setOpaque(false);
        formWrapper.add(formGrid, BorderLayout.WEST);
        gatePanel.add(formWrapper, BorderLayout.CENTER);

        // Bottom informational tip box
        JPanel tipBox = new JPanel(new BorderLayout());
        tipBox.setBackground(isDarkMode ? new Color(24, 27, 32) : new Color(240, 244, 250));
        tipBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isDarkMode ? new Color(48, 54, 64) : new Color(210, 220, 235)),
                new EmptyBorder(6, 10, 6, 10)
        ));
        JLabel tipLbl = new JLabel("<html><b style='color:#00d4ff;'>Wskazówka:</b> Bramka dynamicznie tłumi szum tła w przerwach między impulsami o wybrany poziom dB, zachowując 100% kształtu i energii właściwych impulsów WNZ.</html>");
        tipLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        tipLbl.setForeground(isDarkMode ? new Color(180, 190, 205) : new Color(70, 80, 95));
        tipBox.add(tipLbl, BorderLayout.CENTER);
        gatePanel.add(tipBox, BorderLayout.SOUTH);

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
            statusBannerLbl.setText("● AKTYWNY (Notch + Bramka)");
            statusBannerLbl.setForeground(new Color(16, 185, 129));
            statusBannerLbl.setToolTipText("Wycinanie NBI oraz Bramka Szumowa są aktywne w torze pomiarowym");
        } else {
            statusBannerLbl.setText("○ WYŁĄCZONY (Standardowy tor HPF)");
            statusBannerLbl.setForeground(new Color(156, 163, 175));
            statusBannerLbl.setToolTipText("Odszumianie wyłączone (aktywny standardowy filtr górnoprzepustowy HPF)");
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
        pipeline.PulseWaveform pw = pipeline.PulseWaveformRepository.getInstance().getMaxPulse();
        if (pw != null && pw.filtered != null) {
            return pw.filtered;
        }
        return null;
    }

    public void refreshDisplaysFromParentData() {
        double[] sig = getSampleSignalFromParent();
        if (sig == null || sig.length < 128) {
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
        lastMeasuredRms = (denRms > 0 && denoisingMgr.isGlobalEnabled()) ? denRms : rawRms;

        measuredNoiseRmsLbl.setText(String.format(Locale.US,
                "<html><b>%.2f mV</b> &nbsp;<span style='color:#8892b0; font-size:10px;'>(zalecany próg: %.1f &ndash; %.1f mV)</span></html>",
                lastMeasuredRms, lastMeasuredRms * 1.5, lastMeasuredRms * 2.5));

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
            setToolTipText("Kliknij wykres widma lub [?], aby otworzyć objaśnienia filtrów Notch i NBI");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    helpDialog.showTopic("NOTCH");
                }
            });
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

            // Title with clickable [?]
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(0, 210, 255));
            String title = "WIDMO CZĘSTOTLIWOŚCIOWE FFT (SUROWE vs ODSZUMIONE)";
            g2.drawString(title, padL, padT - 8);

            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(title);
            g2.setColor(new Color(245, 158, 11));
            g2.drawString("[?]", padL + tw + 8, padT - 8);

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
                int labelYOffset = 0;
                int prevNx = -999;
                for (AdaptiveNotchFilter.NotchSlot slot : notchSlots) {
                    if (slot.enabled && slot.freqHz > 0 && slot.freqHz < maxFreq) {
                        int nx = padL + (int) Math.round((slot.freqHz / maxFreq) * plotW);
                        g2.drawLine(nx, padT, nx, padT + plotH);
                        if (Math.abs(nx - prevNx) < 42) {
                            labelYOffset = (labelYOffset + 11) % 33;
                        } else {
                            labelYOffset = 0;
                        }
                        prevNx = nx;
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                        String notchLbl = String.format(Locale.US, "%.1fk", slot.freqHz / 1e3);
                        g2.drawString(notchLbl, Math.max(padL, nx - 12), padT + 12 + labelYOffset);
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
            setToolTipText("Kliknij wykres czasowy lub [?], aby otworzyć objaśnienia Bramki Szumowej");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    helpDialog.showTopic("GATE");
                }
            });
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
            String title = "PRZEBIEG CZASOWY (POMARAŃCZOWY=SUROWY, BŁĘKITNY=ODSZUMIONY)";
            g2.drawString(title, padL, padT - 8);

            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(title);
            g2.setColor(new Color(0, 210, 255));
            g2.drawString("[?]", padL + tw + 8, padT - 8);

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
