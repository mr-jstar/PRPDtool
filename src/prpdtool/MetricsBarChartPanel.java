package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Grouped Bar Chart for Comprehensive AI Model and Defect Class Benchmark Comparison.
 * Compares Accuracy, Precision, Recall, and F1-score with publication-ready Light/Dark styling.
 */
public class MetricsBarChartPanel extends JPanel {

    private final Window parentWindow;
    private final boolean isDarkMode;

    private boolean isLightMode = false;
    private boolean isEnglishMode = false;
    private boolean compareByModels = true; // true = compare 12 models, false = compare 6 classes
    private String selectedModelForClasses = "RESNET_18";
    private boolean showValuesOnBars = true;
    private boolean zoomYAxis = true; // start at 70% for high-resolution comparison

    private final JLabel viewLbl;
    private final JComboBox<String> viewCombo;
    private final JComboBox<String> modelSelectCombo;
    private final JCheckBox showValuesCb;
    private final JCheckBox zoomAxisCb;
    private final JToggleButton lightModeToggle;
    private final JToggleButton langToggle;
    private final JButton exportBtn;
    private final BarChartCanvas canvas;

    public MetricsBarChartPanel(Window parentWindow, boolean isDarkMode) {
        this.parentWindow = parentWindow;
        this.isDarkMode = isDarkMode;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        // Bar chart canvas initialized first
        this.canvas = new BarChartCanvas();

        // --- TOP TOOLBAR ---
        JPanel toolBar = new JPanel(new BorderLayout(10, 0));
        toolBar.setOpaque(false);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        viewLbl = new JLabel("Widok Porównania:");
        viewLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        viewLbl.setForeground(isDarkMode ? new Color(200, 215, 230) : new Color(40, 50, 60));
        leftControls.add(viewLbl);

        viewCombo = new JComboBox<>(new String[]{
                "Porównanie Modeli AI (11 Architektur + Komitet)",
                "Porównanie Klas WNZ (6 Defektów dla Wybranego Modelu)"
        });
        viewCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        leftControls.add(viewCombo);

        modelSelectCombo = new JComboBox<>();
        modelSelectCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        for (String key : ModelValidationData.getAllModelKeys()) {
            modelSelectCombo.addItem(ModelValidationData.getMetrics(key).displayName);
        }
        modelSelectCombo.setSelectedIndex(1); // default ResNet-18
        modelSelectCombo.setVisible(false);
        leftControls.add(modelSelectCombo);

        viewCombo.addActionListener(e -> {
            compareByModels = (viewCombo.getSelectedIndex() == 0);
            modelSelectCombo.setVisible(!compareByModels);
            canvas.repaint();
        });

        modelSelectCombo.addActionListener(e -> {
            int idx = modelSelectCombo.getSelectedIndex();
            if (idx >= 0 && idx < ModelValidationData.getAllModelKeys().size()) {
                selectedModelForClasses = ModelValidationData.getAllModelKeys().get(idx);
                canvas.repaint();
            }
        });

        showValuesCb = new JCheckBox("Pokaż Wartości (%)", showValuesOnBars);
        showValuesCb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        showValuesCb.setOpaque(false);
        showValuesCb.addActionListener(e -> {
            showValuesOnBars = showValuesCb.isSelected();
            canvas.repaint();
        });
        leftControls.add(showValuesCb);

        zoomAxisCb = new JCheckBox("Skala 70%-100%", zoomYAxis);
        zoomAxisCb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        zoomAxisCb.setOpaque(false);
        zoomAxisCb.addActionListener(e -> {
            zoomYAxis = zoomAxisCb.isSelected();
            canvas.repaint();
        });
        leftControls.add(zoomAxisCb);

        lightModeToggle = new JToggleButton("Tryb do artykułu (Jasny)");
        lightModeToggle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lightModeToggle.setFocusPainted(false);
        lightModeToggle.addActionListener(e -> {
            setLightMode(lightModeToggle.isSelected());
        });
        leftControls.add(lightModeToggle);

        langToggle = new JToggleButton("Język: EN (Artykuł)");
        langToggle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        langToggle.setFocusPainted(false);
        langToggle.addActionListener(e -> {
            setEnglishMode(langToggle.isSelected());
        });
        leftControls.add(langToggle);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightControls.setOpaque(false);

        exportBtn = new JButton("Eksportuj wykres (PNG)");
        exportBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        exportBtn.setBackground(isDarkMode ? new Color(38, 52, 70) : new Color(225, 235, 245));
        exportBtn.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));
        exportBtn.setFocusPainted(false);
        exportBtn.addActionListener(e -> ChartExportUtil.exportComponentToPng(
                canvas,
                parentWindow,
                (isEnglishMode
                        ? (compareByModels ? "AI_Models_Comparison_BarChart" : ("PD_Classes_" + selectedModelForClasses + "_BarChart"))
                        : (compareByModels ? "Porownanie_Modeli_BarChart" : ("Porownanie_Klas_" + selectedModelForClasses + "_BarChart")))
                        + (isLightMode ? "_Light.png" : ".png")
        ));
        rightControls.add(exportBtn);

        toolBar.add(leftControls, BorderLayout.WEST);
        toolBar.add(rightControls, BorderLayout.EAST);
        add(toolBar, BorderLayout.NORTH);

        // --- CANVAS ---
        add(canvas, BorderLayout.CENTER);
    }

    public void setLightMode(boolean light) {
        this.isLightMode = light;
        if (lightModeToggle.isSelected() != light) {
            lightModeToggle.setSelected(light);
        }
        lightModeToggle.setText(light
                ? (isEnglishMode ? "Dark Mode" : "Tryb ciemny (Dark)")
                : (isEnglishMode ? "Light Mode (Paper)" : "Tryb do artykułu (Jasny)"));
        canvas.setBackground(light ? Color.WHITE : (isDarkMode ? new Color(20, 24, 32) : Color.WHITE));
        canvas.repaint();
    }

    public boolean isLightMode() {
        return isLightMode;
    }

    public void setEnglishMode(boolean en) {
        this.isEnglishMode = en;
        if (langToggle.isSelected() != en) {
            langToggle.setSelected(en);
        }
        langToggle.setText(en ? "Język: EN (Artykuł)" : "Podpisy: PL / EN");
        viewLbl.setText(en ? "Comparison View:" : "Widok Porównania:");
        showValuesCb.setText(en ? "Show Values (%)" : "Pokaż Wartości (%)");
        zoomAxisCb.setText(en ? "Zoom Axis (70%-100%)" : "Skala 70%-100%");
        exportBtn.setText(en ? "Export Chart (PNG)" : "Eksportuj wykres (PNG)");
        lightModeToggle.setText(isLightMode
                ? (en ? "Dark Mode" : "Tryb ciemny (Dark)")
                : (en ? "Light Mode (Paper)" : "Tryb do artykułu (Jasny)"));

        int sel = viewCombo.getSelectedIndex();
        viewCombo.setModel(new DefaultComboBoxModel<>(en ? new String[]{
                "Compare AI Models (11 Architectures + Ensemble)",
                "Compare PD Classes (6 Defect Types for Selected Model)"
        } : new String[]{
                "Porównanie Modeli AI (11 Architektur + Komitet)",
                "Porównanie Klas WNZ (6 Defektów dla Wybranego Modelu)"
        }));
        viewCombo.setSelectedIndex(sel);

        canvas.repaint();
    }

    public boolean isEnglishMode() {
        return isEnglishMode;
    }

    private class BarChartCanvas extends JPanel {

        BarChartCanvas() {
            setOpaque(true);
            setBackground(isDarkMode ? new Color(20, 24, 32) : Color.WHITE);
            setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(45, 55, 70) : new Color(210, 220, 230), 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean dark = !isLightMode && isDarkMode;

            Color colAcc = dark ? new Color(0, 210, 255) : new Color(2, 132, 199);
            Color colPrec = dark ? new Color(16, 185, 129) : new Color(5, 150, 105);
            Color colRec = dark ? new Color(245, 158, 11) : new Color(217, 119, 6);
            Color colF1 = dark ? new Color(139, 92, 246) : new Color(124, 58, 237);

            int w = getWidth();
            int h = getHeight();

            if (!dark) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, w, h);
            }

            int leftMargin = 60;
            int rightMargin = 25;
            int topMargin = 50;
            int bottomMargin = compareByModels ? 95 : 65;

            int plotW = w - leftMargin - rightMargin;
            int plotH = h - topMargin - bottomMargin;
            if (plotW <= 0 || plotH <= 0) {
                g2.dispose();
                return;
            }

            double yMin = zoomYAxis ? 70.0 : 0.0;
            double yMax = 100.0;

            // Draw Legend at top
            drawLegend(g2, leftMargin, 16, colAcc, colPrec, colRec, colF1, dark);

            // Draw horizontal Grid and Y-axis labels
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            int gridSteps = zoomYAxis ? 6 : 5;
            for (int i = 0; i <= gridSteps; i++) {
                double val = yMin + i * (yMax - yMin) / gridSteps;
                int y = topMargin + (int) Math.round(plotH - (val - yMin) / (yMax - yMin) * plotH);

                g2.setColor(dark ? new Color(42, 50, 65) : new Color(226, 232, 240));
                g2.drawLine(leftMargin, y, leftMargin + plotW, y);

                g2.setColor(dark ? new Color(150, 165, 180) : new Color(71, 85, 105));
                String yLabel = String.format("%.0f%%", val);
                int ylw = g2.getFontMetrics().stringWidth(yLabel);
                g2.drawString(yLabel, leftMargin - ylw - 8, y + 4);
            }

            // Draw groups
            List<GroupData> groups = buildGroups();
            int numGroups = groups.size();
            double groupWidth = (double) plotW / numGroups;
            double barWidth = Math.max(3.0, (groupWidth * 0.76) / 4.0);

            for (int gIdx = 0; gIdx < numGroups; gIdx++) {
                GroupData grp = groups.get(gIdx);
                double groupCenterX = leftMargin + (gIdx + 0.5) * groupWidth;
                double startX = groupCenterX - 2.0 * barWidth;

                // 4 Bars
                drawSingleBar(g2, startX, grp.accuracy * 100.0, barWidth, plotH, topMargin, yMin, yMax, colAcc, dark);
                drawSingleBar(g2, startX + barWidth, grp.precision * 100.0, barWidth, plotH, topMargin, yMin, yMax, colPrec, dark);
                drawSingleBar(g2, startX + 2 * barWidth, grp.recall * 100.0, barWidth, plotH, topMargin, yMin, yMax, colRec, dark);
                drawSingleBar(g2, startX + 3 * barWidth, grp.f1Score * 100.0, barWidth, plotH, topMargin, yMin, yMax, colF1, dark);

                // Group Label below
                drawGroupLabel(g2, grp.label, groupCenterX, topMargin + plotH + 16, compareByModels, dark);
            }

            // Axis line
            g2.setColor(dark ? new Color(100, 115, 135) : new Color(100, 116, 139));
            g2.drawLine(leftMargin, topMargin + plotH, leftMargin + plotW, topMargin + plotH);
            g2.drawLine(leftMargin, topMargin, leftMargin, topMargin + plotH);

            g2.dispose();
        }

        private void drawSingleBar(Graphics2D g2, double x, double val, double width, int plotH, int topMargin, double yMin, double yMax, Color c, boolean dark) {
            val = Math.max(yMin, Math.min(yMax, val));
            double barH = (val - yMin) / (yMax - yMin) * plotH;
            int by = topMargin + (int) Math.round(plotH - barH);
            int bx = (int) Math.round(x);
            int bw = (int) Math.max(1, Math.round(width - 2));

            // Bar fill
            if (dark) {
                GradientPaint gp = new GradientPaint(bx, by, c.brighter(), bx, topMargin + plotH, c.darker());
                g2.setPaint(gp);
            } else {
                g2.setColor(c);
            }
            g2.fillRect(bx, by, bw, (int) Math.round(barH));

            // Top outline
            g2.setColor(dark ? c.brighter() : c.darker());
            g2.drawRect(bx, by, bw, (int) Math.round(barH));

            // Value text on top
            if (showValuesOnBars && bw >= 12) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, bw >= 20 ? 9 : 8));
                g2.setColor(dark ? Color.WHITE : new Color(15, 23, 42));
                String valStr = String.format("%.1f", val);
                int vw = g2.getFontMetrics().stringWidth(valStr);
                g2.drawString(valStr, (int) Math.round(bx + (bw - vw) / 2.0), by - 3);
            }
        }

        private void drawGroupLabel(Graphics2D g2, String label, double cx, int y, boolean rotate, boolean dark) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(dark ? new Color(200, 215, 230) : new Color(15, 23, 42));

            if (rotate) {
                Graphics2D gRot = (Graphics2D) g2.create();
                gRot.translate(cx, y + 4);
                gRot.rotate(Math.toRadians(38));
                gRot.drawString(label, 0, 0);
                gRot.dispose();
            } else {
                int lw = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, (int) Math.round(cx - lw / 2.0), y);
            }
        }

        private void drawLegend(Graphics2D g2, int startX, int y, Color cAcc, Color cPrec, Color cRec, Color cF1, boolean dark) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            int curX = startX;

            curX = drawLegendItem(g2, curX, y, isEnglishMode ? "Accuracy" : "Dokładność (Accuracy)", cAcc, dark);
            curX = drawLegendItem(g2, curX, y, isEnglishMode ? "Precision" : "Precyzja (Precision)", cPrec, dark);
            curX = drawLegendItem(g2, curX, y, isEnglishMode ? "Recall (TPR)" : "Czułość (Recall / TPR)", cRec, dark);
            curX = drawLegendItem(g2, curX, y, isEnglishMode ? "F1-Score" : "F1-Score (Śr. Harmoniczna)", cF1, dark);
        }

        private int drawLegendItem(Graphics2D g2, int x, int y, String label, Color c, boolean dark) {
            g2.setColor(c);
            g2.fillRect(x, y - 9, 12, 12);
            g2.setColor(c.darker());
            g2.drawRect(x, y - 9, 12, 12);

            g2.setColor(dark ? new Color(210, 225, 240) : new Color(15, 23, 42));
            g2.drawString(label, x + 16, y + 1);
            return x + 16 + g2.getFontMetrics().stringWidth(label) + 24;
        }

        private List<GroupData> buildGroups() {
            List<GroupData> list = new ArrayList<>();
            if (compareByModels) {
                for (String key : ModelValidationData.getAllModelKeys()) {
                    ModelValidationData.ModelMetrics m = ModelValidationData.getMetrics(key);
                    String shortLabel = getShortModelLabel(key);
                    list.add(new GroupData(shortLabel, m.accuracy, m.precision, m.recall, m.f1Score));
                }
            } else {
                ModelValidationData.ModelMetrics m = ModelValidationData.getMetrics(selectedModelForClasses);
                String[] classShortNames = ModelValidationData.getClassShortNames(isEnglishMode);
                for (int c = 0; c < 6; c++) {
                    String cName = classShortNames[c];
                    list.add(new GroupData(cName, m.accuracy, m.classPrecision[c], m.classRecall[c], m.classF1[c]));
                }
            }
            return list;
        }

        private String getShortModelLabel(String key) {
            switch (key) {
                case "ENSEMBLE": return isEnglishMode ? "Ensemble Committee" : "Komitet (Ensemble)";
                case "RESNET_18": return "ResNet-18";
                case "YOLO_V8": return "YOLOv8-PRPD";
                case "CNN_LSTM_2D": return "2D-CNN-LSTM";
                case "CNN_LSTM_1D": return "1D-CNN-LSTM";
                case "SQUEEZENET": return "SqueezeNet";
                case "CNN_1D": return "1D-CNN";
                case "RANDOM_FOREST": return "Random Forest";
                case "SVM_QUADRATIC": return "SVM Quadratic";
                case "ANN_MLP": return "ANN / MLP";
                case "KNN": return "k-NN (k=5)";
                case "CIGRE_RULES": return "CIGRE 226";
                default: return key;
            }
        }
    }

    private static class GroupData {
        final String label;
        final double accuracy;
        final double precision;
        final double recall;
        final double f1Score;

        GroupData(String label, double accuracy, double precision, double recall, double f1Score) {
            this.label = label;
            this.accuracy = accuracy;
            this.precision = precision;
            this.recall = recall;
            this.f1Score = f1Score;
        }
    }
}
