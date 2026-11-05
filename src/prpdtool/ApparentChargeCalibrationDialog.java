package prpdtool;

import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interactive Calibration Wizard for IEC 60270 Apparent Charge Calibration (pC / nC).
 * Allows automatic extraction of calibration pulses from real-time stream or file,
 * manual scale factor entry, and unit selection.
 */
public class ApparentChargeCalibrationDialog extends JDialog {

    private final boolean isDarkMode;
    private final Configuration configuration;
    private final ChargeCalibrationManager calManager = ChargeCalibrationManager.getInstance();

    private JTextField targetChargeField;
    private JTextField measuredVoltageField;
    private JTextField calibratorNameField;
    private JLabel calculatedFactorLbl;
    private JLabel detectionStatusLbl;
    private JCheckBox activatePcCheckBox;
    private JLabel currentStatusLbl;

    public ApparentChargeCalibrationDialog(Frame owner, Configuration config, boolean isDarkMode) {
        super(owner, "Kalibracja ładunku pozornego (IEC 60270 / CIGRE)", true);
        this.configuration = config;
        this.isDarkMode = isDarkMode;

        setSize(940, 640);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(owner);

        initUI();
        updateCalculatedFactor();
    }

    private void initUI() {
        Color bg = isDarkMode ? new Color(24, 26, 30) : new Color(245, 247, 250);
        Color panelBg = isDarkMode ? new Color(30, 33, 38) : Color.WHITE;
        Color borderCol = isDarkMode ? new Color(55, 60, 68) : new Color(210, 215, 225);
        Color accentCol = isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout());

        // HEADER
        JPanel headerPanel = new JPanel(new BorderLayout(8, 6));
        headerPanel.setBackground(panelBg);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLbl = new JLabel("Procedura kalibracji ładunku pozornego (IEC 60270)");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(accentCol);

        currentStatusLbl = new JLabel(calManager.getStatusSummary());
        currentStatusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        currentStatusLbl.setForeground(isDarkMode ? new Color(180, 195, 210) : new Color(80, 90, 100));

        headerPanel.add(titleLbl, BorderLayout.NORTH);
        headerPanel.add(currentStatusLbl, BorderLayout.SOUTH);
        getContentPane().add(headerPanel, BorderLayout.NORTH);

        // MAIN CONTENT
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(bg);
        contentPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // STEP 1: CALIBRATOR CHARGE (q_cal)
        JPanel step1Panel = createStepPanel("1. Wstrzyknięty ładunek wzorcowy (q_cal wg kalibratora)", panelBg, borderCol, accentCol);
        step1Panel.setLayout(new BorderLayout(8, 8));

        JPanel qPresetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        qPresetsPanel.setOpaque(false);
        JLabel presetsLbl = new JLabel("Standardowe wzorce:");
        presetsLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        qPresetsPanel.add(presetsLbl);

        double[] presets = {5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0};
        for (double p : presets) {
            JButton b = new JButton(String.format(Locale.US, "%.0f pC", p));
            b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            b.setMargin(new Insets(2, 6, 2, 6));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> {
                targetChargeField.setText(String.format(Locale.US, "%.1f", p));
                updateCalculatedFactor();
            });
            qPresetsPanel.add(b);
        }

        JPanel qInputsPanel = new JPanel(new GridBagLayout());
        qInputsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        qInputsPanel.add(new JLabel("Ładunek probierczy q_cal [pC]:"), gbc);
        gbc.gridx = 1;
        targetChargeField = new JTextField(String.format(Locale.US, "%.1f", calManager.getTargetCharge_pC()), 10);
        qInputsPanel.add(targetChargeField, gbc);

        gbc.gridx = 2;
        qInputsPanel.add(new JLabel("Oznaczenie / model kalibratora:"), gbc);
        gbc.gridx = 3;
        String initialCalName = calManager.getCalibratorName();
        if (initialCalName == null || initialCalName.isBlank() || initialCalName.contains("Wzorzec IEC 60270")) {
            initialCalName = "Omicron CAL 542";
        }
        calibratorNameField = new JTextField(initialCalName, 14);
        qInputsPanel.add(calibratorNameField, gbc);

        step1Panel.add(qPresetsPanel, BorderLayout.NORTH);
        step1Panel.add(qInputsPanel, BorderLayout.SOUTH);
        contentPanel.add(step1Panel);
        contentPanel.add(Box.createVerticalStrut(12));

        // STEP 2: MEASURED VOLTAGE RESPONSE (U_cal)
        JPanel step2Panel = createStepPanel("2. Odpowiedź napięciowa toru pomiarowego (U_cal w mV)", panelBg, borderCol, accentCol);
        step2Panel.setLayout(new BorderLayout(8, 8));

        JPanel autoDetectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        autoDetectPanel.setOpaque(false);
        JButton autoDetectBtn = new JButton("⚡ Wykryj impulsy kalibratora z bieżącego sygnału");
        autoDetectBtn.setFont(autoDetectBtn.getFont().deriveFont(Font.BOLD));
        autoDetectBtn.setForeground(isDarkMode ? new Color(0, 220, 150) : new Color(0, 140, 70));
        autoDetectBtn.addActionListener(e -> detectCalibrationPulses());
        autoDetectPanel.add(autoDetectBtn);

        detectionStatusLbl = new JLabel("Kliknij powyższy przycisk, aby automatycznie obliczyć średnią amplitudę impulsów kalibratora.");
        detectionStatusLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        detectionStatusLbl.setForeground(isDarkMode ? new Color(160, 175, 190) : new Color(100, 110, 120));

        JPanel measInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        measInputPanel.setOpaque(false);
        measInputPanel.add(new JLabel("Średnia amplituda odpowiedzi U_cal [mV]:"));
        measuredVoltageField = new JTextField(String.format(Locale.US, "%.3f", calManager.getMeasuredMean_mV()), 10);
        measInputPanel.add(measuredVoltageField);

        JPanel step2Content = new JPanel(new GridLayout(3, 1, 4, 4));
        step2Content.setOpaque(false);
        step2Content.add(autoDetectPanel);
        step2Content.add(detectionStatusLbl);
        step2Content.add(measInputPanel);
        step2Panel.add(step2Content, BorderLayout.CENTER);

        contentPanel.add(step2Panel);
        contentPanel.add(Box.createVerticalStrut(12));

        // STEP 3: RESULT AND SCALE FACTOR
        JPanel step3Panel = createStepPanel("3. Wyliczony współczynnik kalibracji i jednostka", panelBg, borderCol, accentCol);
        step3Panel.setLayout(new BorderLayout(8, 8));

        calculatedFactorLbl = new JLabel("-");
        calculatedFactorLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        calculatedFactorLbl.setForeground(accentCol);

        activatePcCheckBox = new JCheckBox("Aktywuj jednostkę ładunku [pC] w całym programie (wykresy PRPD, tabele, profile)", true);
        activatePcCheckBox.setFont(new Font("Segoe UI", Font.BOLD, 11));
        activatePcCheckBox.setOpaque(false);

        JPanel step3Content = new JPanel(new GridLayout(2, 1, 6, 6));
        step3Content.setOpaque(false);
        step3Content.add(calculatedFactorLbl);
        step3Content.add(activatePcCheckBox);
        step3Panel.add(step3Content, BorderLayout.CENTER);

        contentPanel.add(step3Panel);
        contentPanel.add(Box.createVerticalGlue());

        getContentPane().add(new JScrollPane(contentPanel), BorderLayout.CENTER);

        // BOTTOM ACTION BAR
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomBar.setBackground(panelBg);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol));

        JButton resetBtn = new JButton("Resetuj kalibrację (przywróć mV)");
        resetBtn.setForeground(new Color(220, 80, 80));
        JButton applyBtn = new JButton("Zastosuj i zapisz kalibrację");
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.setForeground(accentCol);
        JButton cancelBtn = new JButton("Anuluj");

        resetBtn.addActionListener(e -> resetCalibration());
        applyBtn.addActionListener(e -> applyCalibration());
        cancelBtn.addActionListener(e -> setVisible(false));

        bottomBar.add(resetBtn);
        bottomBar.add(Box.createHorizontalStrut(20));
        bottomBar.add(cancelBtn);
        bottomBar.add(applyBtn);

        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        // LISTENERS FOR REAL-TIME FORMULA PREVIEW
        targetChargeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedFactor(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedFactor(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedFactor(); }
        });

        measuredVoltageField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedFactor(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedFactor(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedFactor(); }
        });
    }

    private JPanel createStepPanel(String title, Color panelBg, Color borderCol, Color titleCol) {
        JPanel p = new JPanel();
        p.setBackground(panelBg);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(borderCol),
                        title,
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 12),
                        titleCol
                ),
                new EmptyBorder(6, 10, 8, 10)
        ));
        return p;
    }

    private void updateCalculatedFactor() {
        try {
            double targetPc = Double.parseDouble(targetChargeField.getText().trim().replace(',', '.'));
            double measMv = Double.parseDouble(measuredVoltageField.getText().trim().replace(',', '.'));

            if (targetPc > 0 && measMv > 0) {
                double factor = targetPc / measMv;
                calculatedFactorLbl.setText(String.format(Locale.US,
                        "<html>Współczynnik skali: <b>k_cal = %.4f pC/mV</b> &nbsp;&nbsp;(1 mV = %.4f pC | 1 pC = %.4f mV)</html>",
                        factor, factor, 1.0 / factor));
            } else {
                calculatedFactorLbl.setText("<html><span style='color:orange;'>Wprowadź wartości większe od zera.</span></html>");
            }
        } catch (Exception ex) {
            calculatedFactorLbl.setText("<html><span style='color:gray;'>Wprowadź poprawne liczby zmiennoprzecinkowe.</span></html>");
        }
    }

    /**
     * Analyzes captured pulses to identify repetitive calibrator pulses and compute their mean amplitude.
     */
    private void detectCalibrationPulses() {
        List<PulseWaveform> pulses = PulseWaveformRepository.getInstance().getSnapshot();

        if (pulses.isEmpty()) {
            detectionStatusLbl.setText("<html><span style='color:red;'>Brak zarejestrowanych impulsów. Włącz strumieniowanie lub wczytaj plik z impulsami kalibratora.</span></html>");
            return;
        }

        // Calibrator pulses have a steady, highly consistent amplitude.
        // We find the median or dominant amplitude cluster among non-zero pulses.
        List<Double> amps = new ArrayList<>();
        for (PulseWaveform p : pulses) {
            double a = Math.abs(p.peakMagnitude);
            if (a > 0.5) { // ignore noise floor
                amps.add(a);
            }
        }

        if (amps.size() < 3) {
            detectionStatusLbl.setText("<html><span style='color:orange;'>Zbyt mało impulsów powyżej progu (" + amps.size() + "). Potrzeba co najmniej kilku impulsów.</span></html>");
            return;
        }

        amps.sort(Double::compareTo);
        double median = amps.get(amps.size() / 2);

        // Filter pulses within +/- 30% of median (calibrator cluster)
        double sum = 0.0;
        int count = 0;
        for (double a : amps) {
            if (Math.abs(a - median) <= 0.35 * median) {
                sum += a;
                count++;
            }
        }

        if (count >= 3) {
            double mean = sum / count;
            double sumSq = 0.0;
            for (double a : amps) {
                if (Math.abs(a - median) <= 0.35 * median) {
                    sumSq += (a - mean) * (a - mean);
                }
            }
            double std = Math.sqrt(sumSq / count);

            measuredVoltageField.setText(String.format(Locale.US, "%.3f", mean));
            detectionStatusLbl.setText(String.format(Locale.US,
                    "<html><span style='color:#00e1ff;'>Pomyślnie wykryto wzorzec!</span> Znaleziono <b>%d</b> impulsów kalibracyjnych: <b>U_cal = %.3f mV</b> (odchylenie σ = ±%.3f mV).</html>",
                    count, mean, std));
            updateCalculatedFactor();
        } else {
            detectionStatusLbl.setText("<html><span style='color:orange;'>Nie udało się jednoznacznie zidentyfikować klastra kalibratora. Wpisz U_cal ręcznie.</span></html>");
        }
    }

    private void applyCalibration() {
        try {
            double targetPc = Double.parseDouble(targetChargeField.getText().trim().replace(',', '.'));
            double measMv = Double.parseDouble(measuredVoltageField.getText().trim().replace(',', '.'));
            String name = calibratorNameField.getText().trim();
            boolean switchToPc = activatePcCheckBox.isSelected();

            if (targetPc <= 0 || measMv <= 0) {
                JOptionPane.showMessageDialog(this, "Wartości muszą być większe od zera.", "Błąd danych", JOptionPane.ERROR_MESSAGE);
                return;
            }

            calManager.calibrate(targetPc, measMv, name, switchToPc);
            if (configuration != null) {
                calManager.saveToConfig(configuration);
            }

            JOptionPane.showMessageDialog(this,
                    String.format(Locale.US,
                            "Kalibracja zakończona pomyślnie!\n\n"
                                    + "Współczynnik skali: k_cal = %.4f pC/mV\n"
                                    + "Aktywna jednostka: %s\n"
                                    + "Kalibrator: %s\n\n"
                                    + "Wszystkie wykresy, tabele statystyczne i oscyloskop impulsów zostały zaktualizowane.",
                            calManager.getScaleFactor(), calManager.getUnitLabel(), calManager.getCalibratorName()),
                    "Kalibracja IEC 60270 aktywna",
                    JOptionPane.INFORMATION_MESSAGE);

            setVisible(false);
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Wprowadź poprawne liczby zmiennoprzecinkowe.", "Błąd formatu", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Błąd podczas zapisywania kalibracji: " + ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetCalibration() {
        int opt = JOptionPane.showConfirmDialog(this,
                "Czy na pewno chcesz zresetować kalibrację i powrócić do surowych jednostek miliwoltów [mV]?",
                "Potwierdzenie resetu",
                JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            calManager.resetCalibration();
            if (configuration != null) {
                calManager.saveToConfig(configuration);
            }
            currentStatusLbl.setText(calManager.getStatusSummary());
            updateCalculatedFactor();
            JOptionPane.showMessageDialog(this, "Przywrócono domyślny tryb nieskalibrowany (mV).", "Reset zakończony", JOptionPane.INFORMATION_MESSAGE);
            setVisible(false);
        }
    }
}
