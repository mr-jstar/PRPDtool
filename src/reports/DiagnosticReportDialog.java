package reports;

import pipeline.DynamicPRPDHistogram;
import pipeline.PRPDHistogram;
import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;
import pipeline.StatisticalAnalyzer;
import prpdtool.ChargeCalibrationManager;
import prpdtool.Configuration;
import prpdtool.PRPDTool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Interactive Wizard and Dialog for compiling comprehensive Partial Discharge (WNZ)
 * diagnostic reports according to IEC 60270, IEC 62478, and CIGRE WG 21.03.
 * Supports HTML (Print-to-PDF ready) and Composite Graphic PNG output formats.
 */
public class DiagnosticReportDialog extends JDialog {

    private final PRPDTool parent;
    private final Configuration config;
    private final boolean isDarkMode;

    // Metadata fields
    private JTextField objectNameField;
    private JTextField bayPhaseField;
    private JTextField locationField;
    private JTextField voltageField;
    private JTextField frequencyField;
    private JTextArea notesArea;

    // Section checkboxes
    private JCheckBox incPrpdCb;
    private JCheckBox incProfilesCb;
    private JCheckBox incPulseScopeCb;
    private JCheckBox incStatsTableCb;
    private JCheckBox incAiDiagCb;

    // Format checkboxes
    private JCheckBox formatHtmlCb;
    private JCheckBox formatPngCb;
    private JCheckBox autoOpenCb;
    private JCheckBox runAiFirstCb;

    private JTextField outputDirField;
    private JLabel statusLabel;
    private JButton generateBtn;

    public DiagnosticReportDialog(PRPDTool owner, Configuration config, boolean isDarkMode) {
        super(owner, "Generator Raportu Diagnostycznego WNZ (IEC 60270 / CIGRE)", true);
        this.parent = owner;
        this.config = config;
        this.isDarkMode = isDarkMode;

        setSize(780, 680);
        setMinimumSize(new Dimension(680, 580));
        setLocationRelativeTo(owner);

        initUI();
    }

    private void initUI() {
        Color bg = isDarkMode ? new Color(24, 26, 30) : new Color(245, 247, 250);
        Color panelBg = isDarkMode ? new Color(30, 33, 38) : Color.WHITE;
        Color borderCol = isDarkMode ? new Color(55, 60, 68) : new Color(210, 215, 225);
        Color accentCol = isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout());

        // HEADER
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setBackground(panelBg);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
                new EmptyBorder(12, 18, 12, 18)
        ));

        JLabel titleLbl = new JLabel("Generowanie Raportu Diagnostycznego WNZ");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(accentCol);

        JLabel subLbl = new JLabel("Tworzenie dokumentacji badań stanu izolacji zgodnej z IEC 60270, IEC 62478 oraz wytycznymi CIGRE");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(isDarkMode ? new Color(160, 170, 185) : new Color(100, 110, 120));

        headerPanel.add(titleLbl, BorderLayout.NORTH);
        headerPanel.add(subLbl, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // MAIN CONTENT
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(bg);
        centerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // 1. DANE OBIEKTU I POMIARU
        JPanel metaPanel = new JPanel(new GridBagLayout());
        metaPanel.setBackground(panelBg);
        metaPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "1. Dane Obiektu i Parametry Badania",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), accentCol),
                new EmptyBorder(8, 12, 8, 12)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 4, 3, 4);

        objectNameField = new JTextField("Rozdzielnica SN w izolacji powietrznej (AIS) 15 kV");
        bayPhaseField = new JTextField("Pole probiercze / Faza L1");
        locationField = new JTextField("Hala Wysokich Napięć WE PW, Warszawa");
        voltageField = new JTextField("12.0");
        frequencyField = new JTextField("50.0");
        notesArea = new JTextArea("Stanowisko badawcze WNZ na Hali Wysokich Napięć WE PW. Transformator probierczy 100 kV z regulatorem indukcyjnym i rezystorem wodnym Ro = 40 kΩ. Akwizycja sygnału kartą Red Pitaya STEMlab 125-14.", 2, 20);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        addMetaRow(metaPanel, gbc, 0, "Obiekt badany:", objectNameField, "Pole / Faza:", bayPhaseField);
        addMetaRow(metaPanel, gbc, 1, "Lokalizacja:", locationField, "Napięcie U_test [kV]:", voltageField);
        JLabel sensorInfoLbl = new JLabel("TEV / HFCT (Red Pitaya)");
        sensorInfoLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        addMetaRow(metaPanel, gbc, 2, "Częstotliwość [Hz]:", frequencyField, "Sensory WNZ:", sensorInfoLbl);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        metaPanel.add(new JLabel("Uwagi / Notatki:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3;
        metaPanel.add(new JScrollPane(notesArea), gbc);

        centerPanel.add(metaPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // 2. ZAKRES SEKCJI RAPORTU
        JPanel sectionsPanel = new JPanel(new GridLayout(2, 3, 8, 4));
        sectionsPanel.setBackground(panelBg);
        sectionsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "2. Zakres Sekcji do Uwzględnienia w Raporcie",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), accentCol),
                new EmptyBorder(6, 12, 8, 12)
        ));

        incPrpdCb = new JCheckBox("Wykres PRPD (φ-q-n)", true);
        incProfilesCb = new JCheckBox("Profile Hn, Hqn, Hqmax", true);
        incPulseScopeCb = new JCheckBox("Impuls Qmax i widmo FFT", true);
        incStatsTableCb = new JCheckBox("Tabela CIGRE Fingerprint", true);
        incAiDiagCb = new JCheckBox("Diagnoza AI i Rekomendacje", true);
        runAiFirstCb = new JCheckBox("Wykonaj AI przed generowaniem", true);

        sectionsPanel.add(incPrpdCb);
        sectionsPanel.add(incProfilesCb);
        sectionsPanel.add(incPulseScopeCb);
        sectionsPanel.add(incStatsTableCb);
        sectionsPanel.add(incAiDiagCb);
        sectionsPanel.add(runAiFirstCb);

        centerPanel.add(sectionsPanel);
        centerPanel.add(Box.createVerticalStrut(10));

        // 3. FORMATY WYJŚCIOWE I KATALOG
        JPanel formatPanel = new JPanel(new BorderLayout(8, 8));
        formatPanel.setBackground(panelBg);
        formatPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "3. Format Wyjściowy i Zapis Pliku",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), accentCol),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel fmtToggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        fmtToggles.setBackground(panelBg);
        formatHtmlCb = new JCheckBox("Raport HTML (gotowy do druku i PDF)", true);
        formatPngCb = new JCheckBox("Zbiorczy obraz graficzny PNG", true);
        autoOpenCb = new JCheckBox("Otwórz automatycznie po wygenerowaniu", true);
        fmtToggles.add(formatHtmlCb);
        fmtToggles.add(formatPngCb);
        fmtToggles.add(autoOpenCb);

        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.setBackground(panelBg);
        pathRow.add(new JLabel("Katalog zapisu: "), BorderLayout.WEST);

        File defaultDir = new File("reports");
        if (!defaultDir.exists()) defaultDir.mkdirs();
        outputDirField = new JTextField(defaultDir.getAbsolutePath());
        JButton browseBtn = new JButton("Przeglądaj");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(outputDirField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(outputDirField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);

        formatPanel.add(fmtToggles, BorderLayout.NORTH);
        formatPanel.add(pathRow, BorderLayout.SOUTH);

        centerPanel.add(formatPanel);
        add(centerPanel, BorderLayout.CENTER);

        // BOTTOM ACTION BAR
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(panelBg);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol),
                new EmptyBorder(10, 16, 10, 16)
        ));

        statusLabel = new JLabel("Gotowy do wygenerowania raportu");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(isDarkMode ? new Color(180, 190, 205) : new Color(80, 90, 100));

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnGroup.setBackground(panelBg);

        JButton cancelBtn = new JButton("Zamknij");
        cancelBtn.addActionListener(e -> setVisible(false));

        generateBtn = new JButton("Generuj Raport Diagnostyczny");
        generateBtn.setFont(generateBtn.getFont().deriveFont(Font.BOLD));
        generateBtn.setBackground(new Color(2, 132, 199));
        generateBtn.setForeground(Color.WHITE);
        generateBtn.addActionListener(e -> generateReportAsync());

        btnGroup.add(cancelBtn);
        btnGroup.add(generateBtn);

        bottomBar.add(statusLabel, BorderLayout.WEST);
        bottomBar.add(btnGroup, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void addMetaRow(JPanel p, GridBagConstraints gbc, int row, String lbl1, JComponent c1, String lbl2, JComponent c2) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0.0;
        p.add(new JLabel(lbl1), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        p.add(c1, gbc);
        gbc.gridx = 2; gbc.weightx = 0.0;
        p.add(new JLabel(lbl2), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        p.add(c2, gbc);
    }

    private void generateReportAsync() {
        if (!formatHtmlCb.isSelected() && !formatPngCb.isSelected()) {
            JOptionPane.showMessageDialog(this, "Wybierz przynajmniej jeden format wyjściowy (HTML lub PNG)!", "Brak formatu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        generateBtn.setEnabled(false);
        statusLabel.setText("Generowanie wykresów i kompilacja danych raportu...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<File, Void> worker = new SwingWorker<>() {
            private File generatedHtml;
            private File generatedPng;

            @Override
            protected File doInBackground() throws Exception {
                // 1. Optionally run AI Classification
                if (runAiFirstCb.isSelected()) {
                    parent.runAiClassificationSync();
                }

                // 2. Compile Report Data Container
                DiagnosticReportData d = new DiagnosticReportData();
                String timestampStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                d.reportId = "WNZ-" + timestampStr;
                d.timestamp = new Date();
                d.objectName = objectNameField.getText().trim();
                d.bayOrPhase = bayPhaseField.getText().trim();
                d.location = locationField.getText().trim();
                d.testVoltageKv = voltageField.getText().trim();
                d.lineFrequencyHz = frequencyField.getText().trim();
                d.operatorName = "";
                d.notes = notesArea.getText().trim();

                // Calibration details
                ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
                d.isCalibrated = cal.isCalibrated();
                d.calibrationFactor = cal.getScaleFactor();
                d.chargeUnit = cal.getUnitLabel();
                d.calibratorName = cal.getCalibratorName();
                d.calibrationDate = cal.getCalibrationDate();
                d.targetCharge = cal.getTargetCharge_pC();
                d.measuredVoltage = cal.getMeasuredMean_mV();

                // Section flags
                d.includePrpd = incPrpdCb.isSelected();
                d.includeProfiles = incProfilesCb.isSelected();
                d.includePulseScope = incPulseScopeCb.isSelected();
                d.includeStatisticalTable = incStatsTableCb.isSelected();
                d.includeAiDiagnosis = incAiDiagCb.isSelected();

                // Statistical calculation from live data
                PRPDHistogram hist = parent.getHistogram();
                if (hist instanceof DynamicPRPDHistogram) {
                    DynamicPRPDHistogram dh = (DynamicPRPDHistogram) hist;
                    d.statisticalResult = StatisticalAnalyzer.compute(
                            dh.getPhases(), dh.getAmps(), dh.getTimes(),
                            dh.getSize(), 360, parent.getF0());
                }

                // Representative pulse
                d.qmaxPulse = PulseWaveformRepository.getInstance().getMaxPulse();

                // AI Predictions
                d.aiClassifications = parent.getClassificationResults();

                // Render Chart Images
                // Compute natural proportional dimensions for PRPD based on live display
                int prpdW = 1112;
                int prpdH = 620; // Default generous tall height
                if (hist != null) {
                    BufferedImage baseImg = hist.getImage();
                    if (baseImg != null && baseImg.getWidth() > 0 && baseImg.getHeight() > 0) {
                        double aspect = (double) baseImg.getHeight() / baseImg.getWidth();
                        prpdH = (int) Math.round(prpdW * aspect);
                        prpdH = Math.max(540, Math.min(780, prpdH));
                    }
                }

                if (d.includePrpd) {
                    d.prpdImage = ReportChartRenderer.renderPRPD(hist, prpdW, prpdH);
                }
                if (d.includeProfiles && d.statisticalResult != null) {
                    d.profilesImage = ReportChartRenderer.renderProfiles(d.statisticalResult, 1112, 680, isDarkMode);
                }
                if (d.includePulseScope && d.qmaxPulse != null) {
                    d.pulseScopeImage = ReportChartRenderer.renderPulseAndSpectrum(d.qmaxPulse, 1112, 440, isDarkMode);
                }

                File outDir = new File(outputDirField.getText().trim());
                if (!outDir.exists()) outDir.mkdirs();

                String baseName = "Raport_WNZ_" + d.objectName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + timestampStr;

                // 3. Generate HTML
                if (formatHtmlCb.isSelected()) {
                    File htmlFile = new File(outDir, baseName + ".html");
                    HtmlReportGenerator.generateReport(d, htmlFile);
                    generatedHtml = htmlFile;
                }

                // 4. Generate PNG
                if (formatPngCb.isSelected()) {
                    File pngFile = new File(outDir, baseName + ".png");
                    PngReportGenerator.generateReport(d, pngFile);
                    generatedPng = pngFile;
                }

                return generatedHtml != null ? generatedHtml : generatedPng;
            }

            @Override
            protected void done() {
                generateBtn.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());
                try {
                    File mainFile = get();
                    statusLabel.setText("Raport wygenerowany pomyślnie!");

                    if (autoOpenCb.isSelected() && mainFile != null && mainFile.exists()) {
                        try {
                            Desktop.getDesktop().open(mainFile);
                        } catch (Exception ex) {
                            Desktop.getDesktop().browse(mainFile.toURI());
                        }
                    }

                    StringBuilder msg = new StringBuilder("Raport diagnostyczny został pomyślnie wygenerowany:\n\n");
                    if (generatedHtml != null) msg.append("• HTML: ").append(generatedHtml.getAbsolutePath()).append("\n");
                    if (generatedPng != null) msg.append("• PNG:  ").append(generatedPng.getAbsolutePath()).append("\n");

                    Object[] options = {"Otwórz plik", "Otwórz katalog", "Zamknij"};
                    int choice = JOptionPane.showOptionDialog(DiagnosticReportDialog.this,
                            msg.toString(), "Sukces — Raport Gotowy",
                            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
                            null, options, options[0]);

                    if (choice == 0 && mainFile != null) {
                        Desktop.getDesktop().open(mainFile);
                    } else if (choice == 1 && mainFile != null) {
                        Desktop.getDesktop().open(mainFile.getParentFile());
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Błąd generowania raportu: " + ex.getMessage());
                    JOptionPane.showMessageDialog(DiagnosticReportDialog.this,
                            "Wystąpił błąd podczas generowania raportu:\n" + ex.getMessage(),
                            "Błąd", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }
}
