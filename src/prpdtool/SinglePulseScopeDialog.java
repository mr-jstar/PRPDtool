package prpdtool;

import dsp.FastFourierTransform;
import dsp.FastFourierTransform.SpectrumResult;
import dsp.FastFourierTransform.WindowType;
import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Interactive Diagnostic Tool: Single Pulse Waveform & FFT Scope.
 * Displays high-resolution time domain waveform (ns/us) and frequency domain
 * FFT spectrum (MHz/dB) with IEC 60270 & CIGRE pulse metrics.
 * Integrated with interactive documentation and diagnostic benchmarks.
 */
public class SinglePulseScopeDialog extends JDialog {

    private final boolean isDarkMode;
    private final PulseWaveformRepository repository = PulseWaveformRepository.getInstance();
    private final SinglePulseScopeHelpDialog helpDialog;

    private PulseWaveform currentPulse = null;
    private WindowType currentWindow = WindowType.HANNING;
    private int signalDisplayMode = 0; // 0 = Filtered, 1 = Raw, 2 = Both
    private boolean fftDbScale = true; // true = dB, false = Linear
    private PulseWaveformRepository.SortOrder currentSortOrder = PulseWaveformRepository.SortOrder.AMPLITUDE_DESC;

    // UI Controls
    private JComboBox<PulseWaveform> pulseSelectorCombo;
    private JComboBox<PulseWaveformRepository.SortOrder> listSortCombo;
    private JComboBox<String> signalModeCombo;
    private JComboBox<String> fftScaleCombo;
    private JComboBox<WindowType> windowCombo;
    private JLabel pulseCountLbl;

    // Charts
    private final WaveformPanel waveformPanel;
    private final SpectrumPanel spectrumPanel;

    // Metric Labels
    private JLabel qPeakValLbl;
    private JLabel phaseValLbl;
    private JLabel timeValLbl;
    private JLabel polarityValLbl;
    private JLabel riseTimeValLbl;
    private JLabel pulseWidthValLbl;
    private JLabel fallTimeValLbl;
    private JLabel energyValLbl;
    private JLabel teqValLbl;

    private JLabel fPeakValLbl;
    private JLabel fCentValLbl;
    private JLabel eqBwValLbl;
    private JLabel feqValLbl;
    private JLabel fsValLbl;

    private JTextArea cigreHintArea;

    private boolean updatingCombo = false;

    public SinglePulseScopeDialog(Frame owner, boolean isDarkMode) {
        super(owner, "Single Pulse Waveform & FFT Scope (IEC 60270 / CIGRE)", false);
        this.isDarkMode = isDarkMode;

        setSize(1320, 840);
        setMinimumSize(new Dimension(1180, 680));
        setLocationRelativeTo(owner);

        helpDialog = new SinglePulseScopeHelpDialog(this, isDarkMode);

        waveformPanel = new WaveformPanel();
        spectrumPanel = new SpectrumPanel();

        initUI();

        // Listen for new pulses from repository
        repository.addListener(pw -> {
            if (isVisible()) {
                SwingUtilities.invokeLater(() -> {
                    updatePulseList(pw);
                });
            }
        });

        ChargeCalibrationManager.getInstance().addChangeListener(() -> {
            if (isVisible()) {
                refreshDisplays();
                waveformPanel.repaint();
            }
        });
    }

    private void initUI() {
        Color bg = isDarkMode ? new Color(24, 26, 30) : new Color(245, 247, 250);
        Color panelBg = isDarkMode ? new Color(30, 33, 38) : Color.WHITE;
        Color borderCol = isDarkMode ? new Color(55, 60, 68) : new Color(210, 215, 225);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout());

        // TOP TOOLBAR
        JPanel topToolbar = new JPanel(new BorderLayout());
        topToolbar.setBackground(panelBg);
        topToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol));

        JPanel leftNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 6));
        leftNav.setOpaque(false);

        JButton prevBtn = new JButton("◀ Poprzedni");
        JButton nextBtn = new JButton("Następny ▶");
        JButton maxBtn = new JButton("★ Najwyższy (Qmax)");
        JButton randBtn = new JButton("⚄ Losowy");

        maxBtn.setFont(maxBtn.getFont().deriveFont(Font.BOLD));
        maxBtn.setForeground(isDarkMode ? new Color(255, 204, 0) : new Color(180, 130, 0));

        pulseSelectorCombo = new JComboBox<>();
        pulseSelectorCombo.setPreferredSize(new Dimension(320, 26));
        pulseSelectorCombo.setMinimumSize(new Dimension(280, 26));

        pulseSelectorCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PulseWaveform) {
                    PulseWaveform pw = (PulseWaveform) value;
                    PulseWaveform maxP = repository.getMaxPulse();
                    boolean isMax = (maxP != null && (pw == maxP || pw.id == maxP.id));
                    ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();

                    String qStr = cal.isCalibrated()
                            ? String.format(Locale.US, "Q = %.1f %s (%.1f mV)", cal.toDisplay(pw.peakMagnitude), cal.getUnitLabel(), pw.peakMagnitude)
                            : String.format(Locale.US, "Q = %.1f mV", pw.peakMagnitude);

                    String star = isMax ? "<b style='color:#ffd700;'>★ [Qmax]</b> " : "";
                    lbl.setText("<html>" + star + "<b>" + qStr + "</b> &nbsp;|&nbsp; φ = "
                            + String.format(Locale.US, "%.1f°", pw.phase) + " &nbsp;|&nbsp; t = "
                            + String.format(Locale.US, "%.4f s", pw.timestamp) + "</html>");
                    lbl.setBorder(new EmptyBorder(3, 4, 3, 4));
                }
                return lbl;
            }
        });

        listSortCombo = new JComboBox<>(PulseWaveformRepository.SortOrder.values());
        listSortCombo.setSelectedItem(currentSortOrder);
        listSortCombo.setPreferredSize(new Dimension(185, 26));
        listSortCombo.setToolTipText("Wybierz sposób sortowania/filtrowania impulsów w buforze (np. od najwyższej amplitudy |Q|)");
        listSortCombo.addActionListener(e -> {
            PulseWaveformRepository.SortOrder selected = (PulseWaveformRepository.SortOrder) listSortCombo.getSelectedItem();
            if (selected != null && selected != currentSortOrder) {
                currentSortOrder = selected;
                updateFromCurrentData();
            }
        });

        pulseCountLbl = new JLabel("Bufor: 0 WNZ");
        pulseCountLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pulseCountLbl.setForeground(isDarkMode ? new Color(160, 175, 190) : new Color(100, 110, 120));
        pulseCountLbl.setToolTipText("<html><div style='width:260px; font-family: Segoe UI, sans-serif; font-size: 11px;'>"
                + "<b>Pamięć podręczna oscyloskopu (bufor WNZ)</b><br>"
                + "Liczba wyładowań WNZ aktualnie dostępnych do przeglądania na liście. "
                + "Aplikacja automatycznie chroni i zachowuje do 800 najbardziej energetycznych i charakterystycznych impulsów."
                + "</div></html>");

        leftNav.add(new JLabel("Wybór impulsu:"));
        leftNav.add(prevBtn);
        leftNav.add(nextBtn);
        leftNav.add(pulseSelectorCombo);
        leftNav.add(new JLabel("Widok:"));
        leftNav.add(listSortCombo);
        leftNav.add(maxBtn);
        leftNav.add(randBtn);
        leftNav.add(new JSeparator(JSeparator.VERTICAL));
        leftNav.add(pulseCountLbl);

        // Top-right help button
        JPanel rightHelpBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        rightHelpBar.setOpaque(false);
        JButton mainHelpBtn = new JButton("<html><b style='color:" + (isDarkMode ? "#00e1ff" : "#0284c7") + ";'>[?]</b> <b>Przewodnik i pomoc</b></html>");
        mainHelpBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        mainHelpBtn.setToolTipText("Otwórz pełny przewodnik: objaśnienia wykresów, osi X/Y, parametrów czasowych, widmowych FFT i norm diagnostycznych");
        mainHelpBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        mainHelpBtn.addActionListener(e -> helpDialog.showTopic("OVERVIEW"));
        rightHelpBar.add(mainHelpBtn);

        topToolbar.add(leftNav, BorderLayout.CENTER);
        topToolbar.add(rightHelpBar, BorderLayout.EAST);

        // SECOND TOOLBAR (VIEW MODES)
        JPanel settingsToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        settingsToolbar.setBackground(panelBg);
        settingsToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol));

        signalModeCombo = new JComboBox<>(new String[]{"Filtrowany (Filtered)", "Surowy (Raw)", "Oba sygnały (Overlay)"});
        signalModeCombo.setSelectedIndex(0);

        fftScaleCombo = new JComboBox<>(new String[]{"Decybelowa [dBmV]", "Liniowa [mV]"});
        fftScaleCombo.setSelectedIndex(0);

        windowCombo = new JComboBox<>(WindowType.values());
        windowCombo.setSelectedItem(WindowType.HANNING);

        settingsToolbar.add(new JLabel("Sygnał czasowy:"));
        settingsToolbar.add(signalModeCombo);
        settingsToolbar.add(Box.createHorizontalStrut(10));
        settingsToolbar.add(new JLabel("Skala FFT:"));
        settingsToolbar.add(fftScaleCombo);
        settingsToolbar.add(Box.createHorizontalStrut(10));
        settingsToolbar.add(new JLabel("Okno FFT:"));
        settingsToolbar.add(windowCombo);
        settingsToolbar.add(Box.createHorizontalStrut(6));

        JButton dspHelpBtn = createMiniHelpButton("Objaśnienie opcji: sygnał czasowy, skale FFT i funkcje okna czasowego",
                () -> helpDialog.showTopic("DSP_SETTINGS"));
        settingsToolbar.add(dspHelpBtn);

        JPanel topContainer = new JPanel(new GridLayout(2, 1));
        topContainer.add(topToolbar);
        topContainer.add(settingsToolbar);
        getContentPane().add(topContainer, BorderLayout.NORTH);

        // CENTER CHARTS
        waveformPanel.setMinimumSize(new Dimension(250, 120));
        spectrumPanel.setMinimumSize(new Dimension(250, 120));

        JSplitPane chartSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, waveformPanel, spectrumPanel);
        chartSplit.setResizeWeight(0.5);
        chartSplit.setDividerSize(6);
        chartSplit.setDividerLocation(320);
        chartSplit.setMinimumSize(new Dimension(320, 200));
        chartSplit.setBorder(new EmptyBorder(6, 6, 6, 6));

        // RIGHT METRICS SIDEBAR
        JPanel rightPanel = createMetricsPanel(panelBg, borderCol);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartSplit, rightPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setDividerSize(6);
        mainSplit.setDividerLocation(950);
        mainSplit.setBorder(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    int w = mainSplit.getWidth();
                    if (w > 600) {
                        mainSplit.setDividerLocation(w - 335);
                    }
                });
            }
        });

        getContentPane().add(mainSplit, BorderLayout.CENTER);

        // BOTTOM BAR
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        bottomBar.setBackground(panelBg);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol));

        JButton exportPngBtn = new JButton("Eksportuj wykres (PNG)");
        JButton exportCsvBtn = new JButton("Eksportuj próbki (CSV)");
        JButton refreshBtn = new JButton("Odśwież listę");
        JButton closeBtn = new JButton("Zamknij");

        exportPngBtn.addActionListener(e -> exportChartImage());
        exportCsvBtn.addActionListener(e -> exportPulseDataCsv());
        refreshBtn.addActionListener(e -> updateFromCurrentData());
        closeBtn.addActionListener(e -> setVisible(false));

        bottomBar.add(exportPngBtn);
        bottomBar.add(exportCsvBtn);
        bottomBar.add(refreshBtn);
        bottomBar.add(Box.createHorizontalStrut(15));
        bottomBar.add(closeBtn);

        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        // ACTIONS
        prevBtn.addActionListener(e -> selectRelativePulse(-1));
        nextBtn.addActionListener(e -> selectRelativePulse(1));
        maxBtn.addActionListener(e -> {
            PulseWaveform maxP = repository.getMaxPulse();
            if (maxP != null) selectPulse(maxP);
        });
        randBtn.addActionListener(e -> selectRandomPulse());

        pulseSelectorCombo.addActionListener(e -> {
            if (!updatingCombo) {
                PulseWaveform selected = (PulseWaveform) pulseSelectorCombo.getSelectedItem();
                if (selected != null) {
                    currentPulse = selected;
                    refreshDisplays();
                }
            }
        });

        signalModeCombo.addActionListener(e -> {
            signalDisplayMode = signalModeCombo.getSelectedIndex();
            waveformPanel.repaint();
        });

        fftScaleCombo.addActionListener(e -> {
            fftDbScale = (fftScaleCombo.getSelectedIndex() == 0);
            spectrumPanel.repaint();
        });

        windowCombo.addActionListener(e -> {
            WindowType wt = (WindowType) windowCombo.getSelectedItem();
            if (wt != null) {
                currentWindow = wt;
                spectrumPanel.repaint();
                updateSpectralMetrics();
            }
        });
    }

    private JPanel createMetricsPanel(Color panelBg, Color borderCol) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(panelBg);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, borderCol),
                new EmptyBorder(8, 8, 8, 8)
        ));
        p.setPreferredSize(new Dimension(325, 600));
        p.setMinimumSize(new Dimension(270, 200));
        p.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        // Group 1: General & Time Domain
        JPanel timeGrid = new JPanel(new GridLayout(9, 2, 4, 3));
        timeGrid.setOpaque(false);
        timeGrid.setMinimumSize(new Dimension(220, 150));

        qPeakValLbl = addMetricRow(timeGrid, "Amplituda szczytowa Q:", "Q_PEAK",
                "Maksymalna bezwzględna wartość napięcia sygnału chwilowego lub ładunek pozorny [pC]. Podstawowa miara niszczącej siły wyładowania.");
        phaseValLbl = addMetricRow(timeGrid, "Kąt fazowy φ:", "TIME_METRICS",
                "Kąt fazowy napięcia probierczego 50 Hz w chwili wystąpienia impulsu (0°-360°). Wskazuje położenie na sinusoidzie.");
        polarityValLbl = addMetricRow(timeGrid, "Polaryzacja:", "TIME_METRICS",
                "Biegunowość sygnału w punkcie szczytowym (+ dodatnia / - ujemna). Informuje o kierunku pola w defekcie.");
        timeValLbl = addMetricRow(timeGrid, "Czas wystąpienia t:", "TIME_METRICS",
                "Bezwzględny czas zarejestrowania impulsu od początku pomiaru [s].");
        riseTimeValLbl = addMetricRow(timeGrid, "Czas narastania tr (10-90%):", "RISE_TIME",
                "Czas wzrostu czoła fali od 10% do 90% piku. Mierzy natychmiastową stromość wyładowania (krótki czas = szybka lawina plazmowa).");
        pulseWidthValLbl = addMetricRow(timeGrid, "Czas trwania tw (FWHM):", "PULSE_WIDTH",
                "Szerokość połówkowa na poziomie 50% piku. Efektywny czas trwania głównego transferu ładunku plazmowego.");
        fallTimeValLbl = addMetricRow(timeGrid, "Czas opadania tf:", "FALL_TIME",
                "Czas spadku od 90% do 10% na grzbiecie fali. Rekombinacja nośników i tłumienie w obwodzie RLC czujnika.");
        energyValLbl = addMetricRow(timeGrid, "Energia całkowa E:", "ENERGY",
                "Całka kwadratu napięcia po czasie (∫ u² dt). Różnicuje drobny szum od silnych niszczących WNZ oraz fal z generatora.");
        teqValLbl = addMetricRow(timeGrid, "Czas równoważny CIGRE Teq:", "TEQ",
                "Efektywna szerokość czasowa uwzględniająca całą energię wraz z ogonem i oscylacjami fali (oś X na mapie CIGRE; im mniejsze Teq, tym szybszy impuls).");

        JPanel timeGroup = createSectionGroup(
                "Parametry czasowe impulsu",
                isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204),
                "TIME_METRICS",
                timeGrid,
                borderCol
        );

        // Group 2: Frequency Domain (FFT)
        JPanel freqGrid = new JPanel(new GridLayout(5, 2, 4, 3));
        freqGrid.setOpaque(false);
        freqGrid.setMinimumSize(new Dimension(220, 90));

        fPeakValLbl = addMetricRow(freqGrid, "Częstotliwość piku f_peak:", "F_PEAK",
                "Częstotliwość składowej o największej gęstości energii widmowej FFT (główny rezonans układu lub dominujący prążek fali).");
        fCentValLbl = addMetricRow(freqGrid, "Środek ciężkości f_cent:", "F_CENT",
                "Środek masy rozkładu częstotliwości (tzw. jasność widma). Wyższa wartość świadczy o dominacji szybkich zjawisk nanosekundowych.");
        eqBwValLbl = addMetricRow(freqGrid, "Pasmo równoważne BW:", "BW",
                "Szerokość pasma wokół środka ciężkości. Wąskie pasmo (< 2 MHz) wskazuje na zakłócenie radiowe/NBI, a szerokie na WNZ.");
        feqValLbl = addMetricRow(freqGrid, "Częstotliwość CIGRE Feq:", "FEQ",
                "Średnia ważona zawartość wysokich częstotliwości (RMS) w widmie mocy (oś Y na mapie CIGRE; wysokie Feq > 15 MHz to wyładowania w gazie/wtrętowe, niskie < 3 MHz to zakłócenia).");
        fsValLbl = addMetricRow(freqGrid, "Częstotliwość próbk. fs:", "FREQ_METRICS",
                "Częstotliwość próbkowania przetwornika ADC Red Pitaya STEMlab (125.0 MS/s). Pasmo Nyquista: 62.5 MHz.");

        JPanel freqGroup = createSectionGroup(
                "Parametry widmowe (FFT)",
                isDarkMode ? new Color(102, 255, 153) : new Color(0, 136, 51),
                "FREQ_METRICS",
                freqGrid,
                borderCol
        );

        // Group 3: CIGRE Diagnostic Classification
        JPanel cigreContent = new JPanel(new BorderLayout(4, 4));
        cigreContent.setOpaque(false);

        cigreHintArea = new JTextArea("Brak danych");
        cigreHintArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cigreHintArea.setLineWrap(true);
        cigreHintArea.setWrapStyleWord(true);
        cigreHintArea.setEditable(false);
        cigreHintArea.setOpaque(false);
        cigreHintArea.setMinimumSize(new Dimension(150, 32));
        cigreHintArea.setForeground(isDarkMode ? new Color(220, 230, 240) : new Color(30, 40, 50));
        cigreHintArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cigreHintArea.setToolTipText("Kliknij, aby otworzyć szczegółowy opis klasyfikacji CIGRE T-F");
        cigreHintArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                helpDialog.showTopic("CIGRE_TF");
            }
        });
        cigreContent.add(cigreHintArea, BorderLayout.CENTER);

        JPanel cigreGroup = createSectionGroup(
                "Interpretacja CIGRE T-F",
                isDarkMode ? new Color(255, 204, 102) : new Color(180, 110, 0),
                "CIGRE_TF",
                cigreContent,
                borderCol
        );

        p.add(timeGroup);
        p.add(Box.createVerticalStrut(8));
        p.add(freqGroup);
        p.add(Box.createVerticalStrut(8));
        p.add(cigreGroup);
        p.add(Box.createVerticalGlue());

        return p;
    }

    private JPanel createSectionGroup(String title, Color titleColor, String helpTopicKey, JComponent content, Color borderCol) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderCol, 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(titleColor);

        JButton miniHelp = createMiniHelpButton("Kliknij, aby otworzyć szczegółowy opis i normy: " + title,
                () -> helpDialog.showTopic(helpTopicKey));

        header.add(titleLbl, BorderLayout.WEST);
        header.add(miniHelp, BorderLayout.EAST);

        box.add(header, BorderLayout.NORTH);
        box.add(content, BorderLayout.CENTER);
        return box;
    }

    private JButton createMiniHelpButton(String tooltip, Runnable onClick) {
        JButton btn = new JButton("[?]");
        btn.setToolTipText(tooltip);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(70, 80, 95) : new Color(180, 190, 205), 1, true));
        btn.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> onClick.run());
        return btn;
    }

    private JLabel addMetricRow(JPanel parent, String labelText, String topicKey, String tooltip) {
        JLabel l = new JLabel(labelText);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(isDarkMode ? new Color(160, 175, 190) : new Color(90, 100, 110));
        if (tooltip != null) {
            l.setToolTipText("<html><div style='width:240px; font-family: Segoe UI, sans-serif; font-size: 11px;'>"
                    + tooltip + "<br><i style='color:#38bdf8;'>Kliknij, aby otworzyć pełny opis i wzory.</i></div></html>");
        }
        if (topicKey != null) {
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    helpDialog.showTopic(topicKey);
                }
            });
        }

        JLabel v = new JLabel("-");
        v.setFont(new Font("Segoe UI", Font.BOLD, 11));
        v.setForeground(isDarkMode ? Color.WHITE : Color.BLACK);
        if (topicKey != null) {
            v.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            v.setToolTipText("Kliknij, aby otworzyć opis parametru " + labelText);
            v.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    helpDialog.showTopic(topicKey);
                }
            });
        }

        parent.add(l);
        parent.add(v);
        return v;
    }

    public void updateFromCurrentData() {
        List<PulseWaveform> snapshot = repository.getSnapshot(currentSortOrder);
        updatePulseCombo(snapshot, (currentPulse != null) ? currentPulse : repository.getMaxPulse());
    }

    private void updatePulseList(PulseWaveform newPulse) {
        List<PulseWaveform> snapshot = repository.getSnapshot(currentSortOrder);
        updatePulseCombo(snapshot, newPulse);
    }

    private void updatePulseCombo(List<PulseWaveform> snapshot, PulseWaveform selectedTarget) {
        updatingCombo = true;
        pulseSelectorCombo.removeAllItems();

        for (PulseWaveform p : snapshot) {
            pulseSelectorCombo.addItem(p);
        }

        pulseCountLbl.setText("Bufor: " + snapshot.size() + " WNZ");

        if (selectedTarget != null && snapshot.contains(selectedTarget)) {
            pulseSelectorCombo.setSelectedItem(selectedTarget);
            currentPulse = selectedTarget;
        } else if (!snapshot.isEmpty()) {
            PulseWaveform maxP = repository.getMaxPulse();
            if (maxP != null && snapshot.contains(maxP)) {
                pulseSelectorCombo.setSelectedItem(maxP);
                currentPulse = maxP;
            } else {
                pulseSelectorCombo.setSelectedIndex(0);
                currentPulse = snapshot.get(0);
            }
        } else {
            currentPulse = null;
        }

        updatingCombo = false;
        refreshDisplays();
    }

    private void selectRelativePulse(int delta) {
        int count = pulseSelectorCombo.getItemCount();
        if (count <= 0) return;
        int idx = pulseSelectorCombo.getSelectedIndex();
        int target = idx + delta;
        if (target >= 0 && target < count) {
            pulseSelectorCombo.setSelectedIndex(target);
        }
    }

    private void selectRandomPulse() {
        int count = pulseSelectorCombo.getItemCount();
        if (count <= 0) return;
        int target = (int) (Math.random() * count);
        pulseSelectorCombo.setSelectedIndex(target);
    }

    public void selectPulse(PulseWaveform p) {
        if (p != null) {
            pulseSelectorCombo.setSelectedItem(p);
            currentPulse = p;
            refreshDisplays();
            waveformPanel.repaint();
            spectrumPanel.repaint();
        }
    }

    private void refreshDisplays() {
        if (currentPulse != null) {
            ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
            if (cal.isCalibrated()) {
                qPeakValLbl.setText(String.format(Locale.US, "%.2f %s (%.1f mV)",
                        cal.toDisplay(currentPulse.peakMagnitude), cal.getUnitLabel(), currentPulse.peakMagnitude));
            } else {
                qPeakValLbl.setText(String.format(Locale.US, "%.1f mV", currentPulse.peakMagnitude));
            }
            phaseValLbl.setText(String.format(Locale.US, "%.1f°", currentPulse.phase));
            polarityValLbl.setText(currentPulse.polarity + " (" + (currentPulse.polarity.equals("+") ? "Dodatnia" : "Ujemna") + ")");
            timeValLbl.setText(String.format(Locale.US, "%.4f s", currentPulse.timestamp));

            riseTimeValLbl.setText(String.format(Locale.US, "%.1f ns", currentPulse.riseTimeNs));
            pulseWidthValLbl.setText(String.format(Locale.US, "%.1f ns", currentPulse.pulseWidthNs));
            fallTimeValLbl.setText(String.format(Locale.US, "%.1f ns", currentPulse.fallTimeNs));
            energyValLbl.setText(String.format(Locale.US, "%.2f mV²·μs", currentPulse.energy));
            teqValLbl.setText(String.format(Locale.US, "%.1f ns", currentPulse.equivalentTimeNs));

            updateSpectralMetrics();
        } else {
            qPeakValLbl.setText("-");
            phaseValLbl.setText("-");
            polarityValLbl.setText("-");
            timeValLbl.setText("-");
            riseTimeValLbl.setText("-");
            pulseWidthValLbl.setText("-");
            fallTimeValLbl.setText("-");
            energyValLbl.setText("-");
            teqValLbl.setText("-");

            fPeakValLbl.setText("-");
            fCentValLbl.setText("-");
            eqBwValLbl.setText("-");
            feqValLbl.setText("-");
            fsValLbl.setText("-");
            cigreHintArea.setText("Brak zarejestrowanych impulsów. Rozpocznij akwizycję lub wczytaj plik z sygnałem.");
        }

        waveformPanel.repaint();
        spectrumPanel.repaint();
    }

    private void updateSpectralMetrics() {
        if (currentPulse == null) return;
        SpectrumResult spec = currentPulse.getSpectrum(currentWindow, signalDisplayMode != 1);

        fPeakValLbl.setText(String.format(Locale.US, "%.2f MHz", spec.peakFrequency / 1e6));
        fCentValLbl.setText(String.format(Locale.US, "%.2f MHz", spec.spectralCentroid / 1e6));
        eqBwValLbl.setText(String.format(Locale.US, "%.2f MHz", spec.equivalentBandwidth / 1e6));
        feqValLbl.setText(String.format(Locale.US, "%.2f MHz", spec.equivalentFrequency / 1e6));
        fsValLbl.setText(String.format(Locale.US, "%.1f MS/s", currentPulse.fs / 1e6));

        cigreHintArea.setText(currentPulse.getCigreClassification(spec));
    }

    private void exportChartImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Zapisz widok oscyloskopu i FFT (PNG)");
        fc.setSelectedFile(new File(String.format("WNZ_Pulse_Scope_%d.png", currentPulse != null ? currentPulse.id : 1)));
        fc.setFileFilter(new FileNameExtensionFilter("Pliki obrazów (*.png)", "png"));

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".png")) {
                f = new File(f.getAbsolutePath() + ".png");
            }
            try {
                int w = getWidth();
                int h = getHeight();
                BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = bi.createGraphics();
                paint(g2);
                g2.dispose();
                ImageIO.write(bi, "PNG", f);
                JOptionPane.showMessageDialog(this, "Pomyślnie zapisano wykres:\n" + f.getAbsolutePath(), "Eksport zakończony", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Błąd podczas zapisu obrazu: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportPulseDataCsv() {
        if (currentPulse == null) {
            JOptionPane.showMessageDialog(this, "Brak wybranego impulsu do eksportu.", "Informacja", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Eksportuj próbki czasu i FFT do CSV");
        fc.setSelectedFile(new File(String.format("WNZ_Pulse_%d_Data.csv", currentPulse.id)));
        fc.setFileFilter(new FileNameExtensionFilter("Pliki CSV (*.csv)", "csv"));

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".csv")) {
                f = new File(f.getAbsolutePath() + ".csv");
            }
            try (PrintWriter pw = new PrintWriter(f)) {
                pw.println("# IEC 60270 / CIGRE Single Pulse Waveform & FFT Data");
                pw.printf(Locale.US, "# Pulse ID: %d, Time: %.5f s, Phase: %.2f deg, Qpeak: %.2f mV%n",
                        currentPulse.id, currentPulse.timestamp, currentPulse.phase, currentPulse.peakMagnitude);
                pw.printf(Locale.US, "# RiseTime: %.2f ns, FWHM: %.2f ns, FallTime: %.2f ns, Energy: %.3f mV^2*us%n",
                        currentPulse.riseTimeNs, currentPulse.pulseWidthNs, currentPulse.fallTimeNs, currentPulse.energy);
                pw.println("Sample_Index,Time_ns,Raw_mV,Filtered_mV,FFT_Bin,Freq_MHz,Magnitude_mV,Magnitude_dB");

                SpectrumResult spec = currentPulse.getSpectrum(currentWindow, signalDisplayMode != 1);
                int timeLen = currentPulse.timeRelativeNs.length;
                int fftLen = spec.frequencies.length;
                int maxRows = Math.max(timeLen, fftLen);

                for (int i = 0; i < maxRows; i++) {
                    String timeNsStr = (i < timeLen) ? String.format(Locale.US, "%.2f", currentPulse.timeRelativeNs[i]) : "";
                    String rawStr = (i < timeLen && currentPulse.raw != null && i < currentPulse.raw.length)
                            ? String.format(Locale.US, "%.3f", currentPulse.raw[i]) : "";
                    String filtStr = (i < timeLen && currentPulse.filtered != null && i < currentPulse.filtered.length)
                            ? String.format(Locale.US, "%.3f", currentPulse.filtered[i]) : "";

                    String freqStr = (i < fftLen) ? String.format(Locale.US, "%.4f", spec.frequencies[i] / 1e6) : "";
                    String magStr = (i < fftLen) ? String.format(Locale.US, "%.4f", spec.magnitudes[i]) : "";
                    String dbStr = (i < fftLen) ? String.format(Locale.US, "%.2f", spec.magnitudesDb[i]) : "";

                    pw.printf("%d,%s,%s,%s,%d,%s,%s,%s%n",
                            i, timeNsStr, rawStr, filtStr, (i < fftLen ? i : -1), freqStr, magStr, dbStr);
                }
                JOptionPane.showMessageDialog(this, "Wyeksportowano próbki do pliku CSV:\n" + f.getAbsolutePath(), "Eksport zakończony", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Błąd eksportu do CSV: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================================
    // INNER CLASSES: WAVEFORM & SPECTRUM CHART PANELS
    // =========================================================================

    private class WaveformPanel extends JPanel {
        private final int PAD_L = 60, PAD_R = 25, PAD_T = 28, PAD_B = 32;
        private final Rectangle helpPillBounds = new Rectangle(0, 0, 0, 0);
        private boolean helpPillHover = false;

        public WaveformPanel() {
            setBackground(isDarkMode ? new Color(18, 20, 24) : Color.WHITE);
            setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(45, 50, 58) : new Color(220, 225, 235)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (helpPillBounds.contains(e.getPoint())) {
                        helpDialog.showTopic("CHART_TIME");
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean hover = helpPillBounds.contains(e.getPoint());
                    if (hover != helpPillHover) {
                        helpPillHover = hover;
                        repaint();
                    }
                    setCursor(hover ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = getWidth();
            int h = getHeight();
            int plotW = w - PAD_L - PAD_R;
            int plotH = h - PAD_T - PAD_B;

            // Background & Border
            g2.setColor(isDarkMode ? new Color(18, 20, 24) : Color.WHITE);
            g2.fillRect(0, 0, w, h);

            // Title
            g2.setColor(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            String timeTitle = "PRZEBIEG CZASOWY IMPULSU WNZ (TIME DOMAIN SCOPE)";
            g2.drawString(timeTitle, PAD_L, PAD_T - 8);

            // Help Button [?]
            int pillX = PAD_L + g2.getFontMetrics().stringWidth(timeTitle) + 10;
            int pillY = PAD_T - 22;
            int pillW = 22;
            int pillH = 18;
            helpPillBounds.setBounds(pillX, pillY, pillW, pillH);

            Color pillBg = helpPillHover ?
                    (isDarkMode ? new Color(0, 180, 255, 60) : new Color(0, 120, 220, 40)) :
                    (isDarkMode ? new Color(0, 180, 255, 25) : new Color(0, 120, 220, 20));
            Color pillBorder = isDarkMode ? new Color(0, 210, 255, 140) : new Color(0, 102, 204, 120);
            Color pillText = isDarkMode ? new Color(0, 220, 255) : new Color(0, 102, 204);

            g2.setColor(pillBg);
            g2.fillRoundRect(pillX, pillY, pillW, pillH, 6, 6);
            g2.setColor(pillBorder);
            g2.drawRoundRect(pillX, pillY, pillW, pillH, 6, 6);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(pillText);
            int qW = g2.getFontMetrics().stringWidth("?");
            g2.drawString("?", pillX + (pillW - qW) / 2, pillY + 13);

            if (currentPulse == null || currentPulse.timeRelativeNs.length == 0) {
                g2.setColor(isDarkMode ? Color.GRAY : Color.DARK_GRAY);
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                g2.drawString("Brak impulsu do wyświetlenia", w / 2 - 80, h / 2);
                g2.dispose();
                return;
            }

            double[] time = currentPulse.timeRelativeNs;
            double[] filt = currentPulse.filtered;
            double[] raw = currentPulse.raw;

            double tMin = time[0];
            double tMax = time[time.length - 1];

            // Determine Y bounds dynamically from all displayed signal samples
            double minSample = 0.0;
            double maxSample = 0.0;
            boolean scanFilt = (signalDisplayMode == 0 || signalDisplayMode == 2) && filt != null;
            boolean scanRaw = (signalDisplayMode == 1 || signalDisplayMode == 2) && raw != null;

            if (scanFilt) {
                for (double v : filt) {
                    if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                        if (v < minSample) minSample = v;
                        if (v > maxSample) maxSample = v;
                    }
                }
            }
            if (scanRaw) {
                for (double v : raw) {
                    if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                        if (v < minSample) minSample = v;
                        if (v > maxSample) maxSample = v;
                    }
                }
            }

            double maxAbs = Math.max(Math.abs(minSample), Math.abs(maxSample));
            maxAbs = Math.max(maxAbs, Math.abs(currentPulse.peakMagnitude));
            if (maxAbs < 0.5) {
                maxAbs = 1.0;
            }

            // Provide 18% headroom so peak amplitudes and ringing oscillations never clip or touch border
            double yLimit = maxAbs * 1.18;
            double yMin = -yLimit;
            double yMax = yLimit;

            // Draw Grid
            drawGrid(g2, PAD_L, PAD_T, plotW, plotH, tMin, tMax, yMin, yMax, "ns", "mV");

            Shape oldClip = g2.getClip();
            g2.clipRect(PAD_L, PAD_T, plotW, plotH);

            // Plot Raw Signal (if selected)
            if ((signalDisplayMode == 1 || signalDisplayMode == 2) && raw != null && raw.length == time.length) {
                g2.setColor(isDarkMode ? new Color(245, 158, 11, 180) : new Color(217, 119, 6, 200));
                g2.setStroke(new BasicStroke(1.2f));
                drawSignalPath(g2, PAD_L, PAD_T, plotW, plotH, time, raw, tMin, tMax, yMin, yMax);
            }

            // Plot Filtered Signal (if selected)
            if ((signalDisplayMode == 0 || signalDisplayMode == 2) && filt != null && filt.length == time.length) {
                g2.setColor(isDarkMode ? new Color(0, 225, 255) : new Color(2, 132, 199));
                g2.setStroke(new BasicStroke(2.0f));
                drawSignalPath(g2, PAD_L, PAD_T, plotW, plotH, time, filt, tMin, tMax, yMin, yMax);
            }

            // Draw Peak Marker Dot & Line
            if (currentPulse.peakIndex >= 0 && currentPulse.peakIndex < time.length) {
                double pkT = time[currentPulse.peakIndex];
                double pkY = (filt != null) ? filt[currentPulse.peakIndex] : currentPulse.peakMagnitude;

                int px = PAD_L + (int) Math.round(((pkT - tMin) / (tMax - tMin)) * plotW);
                int py = PAD_T + (int) Math.round(((yMax - pkY) / (yMax - yMin)) * plotH);

                g2.setColor(Color.RED);
                g2.fillOval(px - 4, py - 4, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawOval(px - 4, py - 4, 8, 8);

                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.setColor(isDarkMode ? new Color(255, 100, 100) : new Color(200, 0, 0));
                ChargeCalibrationManager calMgr = ChargeCalibrationManager.getInstance();
                String pkTxt = calMgr.isCalibrated() ?
                        String.format(Locale.US, "Qp=%.2f %s", calMgr.toDisplay(currentPulse.peakMagnitude), calMgr.getUnitLabel()) :
                        String.format(Locale.US, "Qp=%.1f mV", currentPulse.peakMagnitude);
                g2.drawString(pkTxt, px + 6, py - 4);
            }

            g2.setClip(oldClip);

            // Legend
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            int legX = w - PAD_R - 160;
            int legY = PAD_T - 8;
            if (signalDisplayMode == 0 || signalDisplayMode == 2) {
                g2.setColor(isDarkMode ? new Color(0, 225, 255) : new Color(2, 132, 199));
                g2.fillRect(legX, legY - 7, 10, 10);
                g2.setColor(isDarkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                g2.drawString("Filtrowany", legX + 14, legY + 2);
            }
            if (signalDisplayMode == 1 || signalDisplayMode == 2) {
                legX += 75;
                g2.setColor(isDarkMode ? new Color(245, 158, 11) : new Color(217, 119, 6));
                g2.fillRect(legX, legY - 7, 10, 10);
                g2.setColor(isDarkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY);
                g2.drawString("Surowy", legX + 14, legY + 2);
            }

            g2.dispose();
        }

        private void drawSignalPath(Graphics2D g2, int ox, int oy, int pw, int ph,
                                    double[] xs, double[] ys, double xMin, double xMax, double yMin, double yMax) {
            Path2D.Double path = new Path2D.Double();
            double xRange = xMax - xMin;
            double yRange = yMax - yMin;

            for (int i = 0; i < xs.length; i++) {
                double sx = ox + ((xs[i] - xMin) / xRange) * pw;
                double sy = oy + ((yMax - ys[i]) / yRange) * ph;
                if (i == 0) {
                    path.moveTo(sx, sy);
                } else {
                    path.lineTo(sx, sy);
                }
            }
            g2.draw(path);
        }
    }

    private class SpectrumPanel extends JPanel {
        private final int PAD_L = 60, PAD_R = 25, PAD_T = 28, PAD_B = 32;
        private final Rectangle helpPillBounds = new Rectangle(0, 0, 0, 0);
        private boolean helpPillHover = false;

        public SpectrumPanel() {
            setBackground(isDarkMode ? new Color(18, 20, 24) : Color.WHITE);
            setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(45, 50, 58) : new Color(220, 225, 235)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (helpPillBounds.contains(e.getPoint())) {
                        helpDialog.showTopic("CHART_FREQ");
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean hover = helpPillBounds.contains(e.getPoint());
                    if (hover != helpPillHover) {
                        helpPillHover = hover;
                        repaint();
                    }
                    setCursor(hover ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = getWidth();
            int h = getHeight();
            int plotW = w - PAD_L - PAD_R;
            int plotH = h - PAD_T - PAD_B;

            // Background
            g2.setColor(isDarkMode ? new Color(18, 20, 24) : Color.WHITE);
            g2.fillRect(0, 0, w, h);

            // Title
            g2.setColor(isDarkMode ? new Color(102, 255, 153) : new Color(0, 136, 51));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            String freqTitle = "WIDMO CZĘSTOTLIWOŚCIOWE FFT (FREQUENCY DOMAIN SPECTRUM)";
            g2.drawString(freqTitle, PAD_L, PAD_T - 8);

            // Help Button [?]
            int pillX = PAD_L + g2.getFontMetrics().stringWidth(freqTitle) + 10;
            int pillY = PAD_T - 22;
            int pillW = 22;
            int pillH = 18;
            helpPillBounds.setBounds(pillX, pillY, pillW, pillH);

            Color pillBg = helpPillHover ?
                    (isDarkMode ? new Color(16, 185, 129, 60) : new Color(5, 150, 105, 40)) :
                    (isDarkMode ? new Color(16, 185, 129, 25) : new Color(5, 150, 105, 20));
            Color pillBorder = isDarkMode ? new Color(16, 185, 129, 140) : new Color(5, 150, 105, 120);
            Color pillText = isDarkMode ? new Color(16, 185, 129) : new Color(5, 150, 105);

            g2.setColor(pillBg);
            g2.fillRoundRect(pillX, pillY, pillW, pillH, 6, 6);
            g2.setColor(pillBorder);
            g2.drawRoundRect(pillX, pillY, pillW, pillH, 6, 6);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(pillText);
            int qW = g2.getFontMetrics().stringWidth("?");
            g2.drawString("?", pillX + (pillW - qW) / 2, pillY + 13);

            if (currentPulse == null) {
                g2.setColor(isDarkMode ? Color.GRAY : Color.DARK_GRAY);
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                g2.drawString("Brak widma do wyświetlenia", w / 2 - 80, h / 2);
                g2.dispose();
                return;
            }

            SpectrumResult spec = currentPulse.getSpectrum(currentWindow, signalDisplayMode != 1);
            if (spec == null || spec.frequencies.length == 0) {
                g2.dispose();
                return;
            }

            double fMin = 0.0;
            double fMax = (spec.fs > 0) ? (spec.fs / 2e6) : 62.5; // in MHz

            double[] freqsMhz = new double[spec.frequencies.length];
            for (int i = 0; i < spec.frequencies.length; i++) {
                freqsMhz[i] = spec.frequencies[i] / 1e6;
            }

            double[] amps = fftDbScale ? spec.magnitudesDb : spec.magnitudes;
            double yMin, yMax;

            if (fftDbScale) {
                yMin = -80.0; // -80 dBmV
                double maxDb = -80.0;
                for (double d : spec.magnitudesDb) {
                    if (!Double.isNaN(d) && !Double.isInfinite(d) && d > maxDb) {
                        maxDb = d;
                    }
                }
                // Provide 10 dB headroom so the highest harmonic peak never touches the top border
                yMax = Math.max(20.0, Math.ceil((maxDb + 10.0) / 10.0) * 10.0);
            } else {
                yMin = 0.0;
                double maxMag = 0.0;
                for (double m : spec.magnitudes) {
                    if (!Double.isNaN(m) && !Double.isInfinite(m) && m > maxMag) {
                        maxMag = m;
                    }
                }
                yMax = Math.max(1.0, maxMag * 1.25);
            }

            // Draw Grid
            drawGrid(g2, PAD_L, PAD_T, plotW, plotH, fMin, fMax, yMin, yMax, "MHz", fftDbScale ? "dB" : "mV");

            Shape oldClip = g2.getClip();
            g2.clipRect(PAD_L, PAD_T, plotW, plotH);

            // Draw Area Fill & Line
            double xRange = fMax - fMin;
            double yRange = yMax - yMin;

            Path2D.Double area = new Path2D.Double();
            Path2D.Double line = new Path2D.Double();

            double baseSy = PAD_T + ((yMax - yMin) / yRange) * plotH;
            area.moveTo(PAD_L, baseSy);

            for (int i = 0; i < freqsMhz.length; i++) {
                double sx = PAD_L + ((freqsMhz[i] - fMin) / xRange) * plotW;
                double val = Math.max(yMin, Math.min(yMax, amps[i]));
                double sy = PAD_T + ((yMax - val) / yRange) * plotH;

                if (i == 0) {
                    area.lineTo(sx, sy);
                    line.moveTo(sx, sy);
                } else {
                    area.lineTo(sx, sy);
                    line.lineTo(sx, sy);
                }
            }
            area.lineTo(PAD_L + plotW, baseSy);
            area.closePath();

            // Gradient Fill
            Color gradStart = isDarkMode ? new Color(16, 185, 129, 90) : new Color(16, 185, 129, 60);
            Color gradEnd = isDarkMode ? new Color(16, 185, 129, 5) : new Color(16, 185, 129, 5);
            g2.setPaint(new GradientPaint(0, PAD_T, gradStart, 0, PAD_T + plotH, gradEnd));
            g2.fill(area);

            // Spectrum Stroke
            g2.setColor(isDarkMode ? new Color(16, 185, 129) : new Color(5, 150, 105));
            g2.setStroke(new BasicStroke(1.8f));
            g2.draw(line);

            // Dominant Peak Marker
            if (spec.peakFrequency > 0) {
                double fPkMhz = spec.peakFrequency / 1e6;
                int px = PAD_L + (int) Math.round(((fPkMhz - fMin) / xRange) * plotW);
                if (px >= PAD_L && px <= PAD_L + plotW) {
                    g2.setColor(isDarkMode ? new Color(255, 204, 0, 160) : new Color(200, 140, 0, 180));
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3, 3}, 0.0f));
                    g2.drawLine(px, PAD_T, px, PAD_T + plotH);

                    g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    g2.setColor(isDarkMode ? new Color(255, 220, 50) : new Color(160, 100, 0));
                    g2.drawString(String.format(Locale.US, "fpk=%.2f MHz", fPkMhz), px + 4, PAD_T + 16);
                }
            }

            // Spectral Centroid Marker
            if (spec.spectralCentroid > 0) {
                double fCentMhz = spec.spectralCentroid / 1e6;
                int cx = PAD_L + (int) Math.round(((fCentMhz - fMin) / xRange) * plotW);
                if (cx >= PAD_L && cx <= PAD_L + plotW) {
                    g2.setColor(isDarkMode ? new Color(0, 210, 255, 140) : new Color(0, 102, 204, 160));
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2, 2}, 0.0f));
                    g2.drawLine(cx, PAD_T + 22, cx, PAD_T + plotH);

                    g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                    g2.setColor(isDarkMode ? new Color(100, 220, 255) : new Color(0, 80, 180));
                    g2.drawString(String.format(Locale.US, "f_cent=%.1f MHz", fCentMhz), cx + 4, PAD_T + 30);
                }
            }

            g2.setClip(oldClip);

            g2.dispose();
        }
    }

    private void drawGrid(Graphics2D g2, int ox, int oy, int pw, int ph,
                          double xMin, double xMax, double yMin, double yMax,
                          String xUnit, String yUnit) {
        Color gridColor = isDarkMode ? new Color(40, 44, 52) : new Color(230, 235, 242);
        Color textColor = isDarkMode ? new Color(140, 150, 165) : new Color(100, 110, 120);

        // Plot Border
        g2.setColor(gridColor);
        g2.drawRect(ox, oy, pw, ph);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        // Horizontal Grid (Y-axis) - 5 divisions
        int yDivs = 5;
        for (int i = 0; i <= yDivs; i++) {
            double frac = (double) i / yDivs;
            int sy = oy + (int) Math.round(frac * ph);
            double val = yMax - frac * (yMax - yMin);

            g2.setColor(gridColor);
            g2.drawLine(ox, sy, ox + pw, sy);

            g2.setColor(textColor);
            String label = String.format(Locale.US, "%.1f", val);
            int strW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, ox - strW - 5, sy + 4);
        }

        // Vertical Grid (X-axis) - 6 divisions
        int xDivs = 6;
        for (int i = 0; i <= xDivs; i++) {
            double frac = (double) i / xDivs;
            int sx = ox + (int) Math.round(frac * pw);
            double val = xMin + frac * (xMax - xMin);

            g2.setColor(gridColor);
            g2.drawLine(sx, oy, sx, oy + ph);

            g2.setColor(textColor);
            String label = String.format(Locale.US, "%.1f", val);
            int strW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, sx - strW / 2, oy + ph + 14);
        }

        // Units
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.setColor(isDarkMode ? new Color(180, 195, 210) : new Color(70, 80, 90));
        g2.drawString("[" + yUnit + "]", ox - 45, oy - 6);
        g2.drawString("[" + xUnit + "]", ox + pw + 4, oy + ph + 14);
    }
}
