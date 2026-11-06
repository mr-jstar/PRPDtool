package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Multi-Class ROC (Receiver Operating Characteristic) & Precision-Recall (PR) Curve Plotter.
 * Supports publication-grade Light/Dark modes, trapezoidal AUC/AP integration, and high-res PNG export.
 */
public class RocPrCurvePanel extends JPanel {

    private final Window parentWindow;
    private final boolean isDarkMode;

    private boolean isLightMode = false;
    private boolean isEnglishMode = false;
    private boolean isRocMode = true; // true = ROC, false = PR
    private String currentModelKey = "RESNET_18";

    private final JLabel modelLbl;
    private final JComboBox<String> modelCombo;
    private final JRadioButton rocRadio;
    private final JRadioButton prRadio;
    private final JToggleButton lightModeToggle;
    private final JToggleButton langToggle;
    private final JButton exportBtn;
    private final CurveCanvas canvas;
    private final JLabel formulaFooter;

    public RocPrCurvePanel(Window parentWindow, boolean isDarkMode) {
        this.parentWindow = parentWindow;
        this.isDarkMode = isDarkMode;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        // Curve canvas initialized first
        this.canvas = new CurveCanvas();

        // --- TOP TOOLBAR ---
        JPanel toolBar = new JPanel(new BorderLayout(10, 0));
        toolBar.setOpaque(false);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        modelLbl = new JLabel("Model AI:");
        modelLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        modelLbl.setForeground(isDarkMode ? new Color(200, 215, 230) : new Color(40, 50, 60));
        leftControls.add(modelLbl);

        modelCombo = new JComboBox<>();
        modelCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        for (String key : ModelValidationData.getAllModelKeys()) {
            modelCombo.addItem(ModelValidationData.getMetrics(key).displayName);
        }
        modelCombo.setSelectedIndex(1); // default ResNet-18
        modelCombo.addActionListener(e -> {
            int idx = modelCombo.getSelectedIndex();
            if (idx >= 0 && idx < ModelValidationData.getAllModelKeys().size()) {
                currentModelKey = ModelValidationData.getAllModelKeys().get(idx);
                canvas.repaint();
                updateFooter();
            }
        });
        leftControls.add(modelCombo);

        ButtonGroup curveTypeGroup = new ButtonGroup();
        rocRadio = new JRadioButton("Krzywa ROC (AUC)", true);
        rocRadio.setFont(new Font("Segoe UI", Font.BOLD, 11));
        rocRadio.setOpaque(false);
        rocRadio.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));
        curveTypeGroup.add(rocRadio);
        leftControls.add(rocRadio);

        prRadio = new JRadioButton("Krzywa Precision-Recall (AP)", false);
        prRadio.setFont(new Font("Segoe UI", Font.BOLD, 11));
        prRadio.setOpaque(false);
        prRadio.setForeground(isDarkMode ? new Color(16, 185, 129) : new Color(10, 140, 90));
        curveTypeGroup.add(prRadio);
        leftControls.add(prRadio);

        rocRadio.addActionListener(e -> {
            isRocMode = true;
            canvas.repaint();
            updateFooter();
        });

        prRadio.addActionListener(e -> {
            isRocMode = false;
            canvas.repaint();
            updateFooter();
        });

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
                (isEnglishMode ? (isRocMode ? "ROC_Curve_" : "PR_Curve_") : (isRocMode ? "Krzywa_ROC_" : "Krzywa_PR_"))
                        + currentModelKey + (isLightMode ? "_Light.png" : ".png")
        ));
        rightControls.add(exportBtn);

        toolBar.add(leftControls, BorderLayout.WEST);
        toolBar.add(rightControls, BorderLayout.EAST);
        add(toolBar, BorderLayout.NORTH);

        // --- CANVAS ---
        add(canvas, BorderLayout.CENTER);

        // --- BOTTOM EXPLANATION ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(6, 6, 4, 6));

        formulaFooter = new JLabel();
        formulaFooter.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        formulaFooter.setForeground(isDarkMode ? new Color(210, 225, 240) : new Color(30, 40, 50));
        bottomPanel.add(formulaFooter, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        updateFooter();
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
        updateFooter();
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
        modelLbl.setText(en ? "AI Model:" : "Model AI:");
        rocRadio.setText(en ? "ROC Curve (AUC)" : "Krzywa ROC (AUC)");
        prRadio.setText(en ? "Precision-Recall Curve (AP)" : "Krzywa Precision-Recall (AP)");
        exportBtn.setText(en ? "Export Chart (PNG)" : "Eksportuj wykres (PNG)");
        lightModeToggle.setText(isLightMode
                ? (en ? "Dark Mode" : "Tryb ciemny (Dark)")
                : (en ? "Light Mode (Paper)" : "Tryb do artykułu (Jasny)"));
        updateFooter();
        canvas.repaint();
    }

    public boolean isEnglishMode() {
        return isEnglishMode;
    }

    private void updateFooter() {
        ModelValidationData.ModelMetrics m = ModelValidationData.getMetrics(currentModelKey);
        if (m == null) return;
        String accColor = isLightMode ? "#0284c7" : "#00e1ff";
        if (isRocMode) {
            if (isEnglishMode) {
                formulaFooter.setText(String.format(
                        "<html><b>Receiver Operating Characteristic (ROC) Curve:</b> X-axis: False Positive Rate (FPR = 1 - Specificity) | Y-axis: True Positive Rate (TPR = Sensitivity). "
                                + "Macro-Average: <b style='color:%s;'>AUC = %.4f</b> (Higher AUC denotes superior defect discrimination without false alarms).</html>",
                        accColor, m.macroAuc
                ));
            } else {
                formulaFooter.setText(String.format(
                        "<html><b>Krzywa ROC (Receiver Operating Characteristic):</b> Oś X: False Positive Rate (FPR = 1 - Swoistość) | Oś Y: True Positive Rate (TPR = Czułość). "
                                + "Średnia Makro: <b style='color:%s;'>AUC = %.4f</b> (Im bliżej 1.000, tym wyższa zdolność separacji bez fałszywych alarmów).</html>",
                        accColor, m.macroAuc
                ));
            }
        } else {
            if (isEnglishMode) {
                formulaFooter.setText(String.format(
                        "<html><b>Precision-Recall (PR) Curve:</b> X-axis: Recall (TPR) | Y-axis: Precision (Positive Predictive Value). "
                                + "Essential for imbalanced PD diagnostics (e.g. rare internal void discharges against dense substation noise). Mean Average Precision: <b>mAP = %.4f</b></html>",
                        m.macroAuc * 0.985
                ));
            } else {
                formulaFooter.setText(String.format(
                        "<html><b>Krzywa Precision-Recall (PR Curve):</b> Oś X: Czułość (Recall) | Oś Y: Precyzja (Precision). "
                                + "Szczególnie krytyczna dla rzadkich defektów WNZ (np. pojedynczy pęcherz gazowy na tle 10 000 zakłóceń stacyjnych). Średnia Precyzja: <b>mAP = %.4f</b></html>",
                        m.macroAuc * 0.985
                ));
            }
        }
    }

    private class CurveCanvas extends JPanel {

        private Point mousePos = null;

        CurveCanvas() {
            setOpaque(true);
            setBackground(isDarkMode ? new Color(20, 24, 32) : Color.WHITE);
            setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(45, 55, 70) : new Color(210, 220, 230), 1));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePos = e.getPoint();
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mousePos = null;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean dark = !isLightMode && isDarkMode;

            ModelValidationData.ModelMetrics m = ModelValidationData.getMetrics(currentModelKey);
            if (m == null) {
                g2.dispose();
                return;
            }

            int w = getWidth();
            int h = getHeight();

            if (!dark) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, w, h);
            }

            int leftMargin = 70;
            int rightMargin = Math.max(190, Math.min(250, (int) (w * 0.22))); // adaptive legend width
            int topMargin = 35;
            int bottomMargin = 55;

            int plotW = w - leftMargin - rightMargin;
            int plotH = h - topMargin - bottomMargin;
            if (plotW <= 0 || plotH <= 0) {
                g2.dispose();
                return;
            }

            Color[] classColors = dark ? new Color[]{
                    new Color(255, 75, 75),   // Corona +
                    new Color(0, 190, 255),   // Corona -
                    new Color(0, 220, 120),   // Surface
                    new Color(190, 80, 255),  // Void
                    new Color(255, 200, 0),   // Floating
                    new Color(160, 175, 190)  // Noise
            } : new Color[]{
                    new Color(220, 38, 38),   // Corona + (Deep Red)
                    new Color(2, 132, 199),   // Corona - (Sky Blue)
                    new Color(5, 150, 105),   // Surface (Emerald)
                    new Color(124, 58, 237),  // Void (Purple)
                    new Color(217, 119, 6),   // Floating (Amber)
                    new Color(71, 85, 105)    // Noise (Slate)
            };

            // Draw Grid & Axes
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            int steps = 10;
            for (int i = 0; i <= steps; i++) {
                double val = i * 0.1;
                int x = leftMargin + (int) Math.round(val * plotW);
                int y = topMargin + (int) Math.round((1.0 - val) * plotH);

                // Horizontal line
                g2.setColor(dark ? new Color(38, 46, 60) : new Color(226, 232, 240));
                g2.drawLine(leftMargin, y, leftMargin + plotW, y);

                // Vertical line
                g2.drawLine(x, topMargin, x, topMargin + plotH);

                // Labels
                g2.setColor(dark ? new Color(150, 165, 180) : new Color(71, 85, 105));
                String valStr = String.format("%.1f", val);
                int vlw = g2.getFontMetrics().stringWidth(valStr);
                g2.drawString(valStr, leftMargin - vlw - 8, y + 4);
                g2.drawString(valStr, x - vlw / 2, topMargin + plotH + 18);
            }

            // Axis titles
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(dark ? new Color(200, 215, 230) : new Color(15, 23, 42));
            String xTitle;
            if (isEnglishMode) {
                xTitle = isRocMode
                        ? "False Positive Rate (FPR = 1 - Specificity)"
                        : "Recall (True Positive Rate)";
            } else {
                xTitle = isRocMode
                        ? "False Positive Rate (FPR = 1 - Swoistość)"
                        : "Czułość (Recall / True Positive Rate)";
            }
            int xtw = g2.getFontMetrics().stringWidth(xTitle);
            g2.drawString(xTitle, leftMargin + (plotW - xtw) / 2, topMargin + plotH + 38);

            // Rotate Y Title
            Graphics2D gRot = (Graphics2D) g2.create();
            gRot.translate(20, topMargin + plotH / 2);
            gRot.rotate(-Math.PI / 2);
            String yTitle;
            if (isEnglishMode) {
                yTitle = isRocMode
                        ? "True Positive Rate (TPR / Sensitivity)"
                        : "Precision (Positive Predictive Value)";
            } else {
                yTitle = isRocMode
                        ? "True Positive Rate (TPR / Czułość)"
                        : "Precyzja (Precision / Positive Predictive Value)";
            }
            int ytw = gRot.getFontMetrics().stringWidth(yTitle);
            gRot.drawString(yTitle, -ytw / 2, 0);
            gRot.dispose();

            // Baseline diagonal or chance line
            Stroke oldStroke = g2.getStroke();
            float[] dash = {6f, 6f};
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.setColor(dark ? new Color(100, 115, 130) : new Color(148, 163, 184));

            if (isRocMode) {
                // Diagonal y = x (Random classifier AUC = 0.500)
                g2.drawLine(leftMargin, topMargin + plotH, leftMargin + plotW, topMargin);
            } else {
                // Horizontal chance line y = 1/6
                int chanceY = topMargin + (int) Math.round((1.0 - 1.0 / 6.0) * plotH);
                g2.drawLine(leftMargin, chanceY, leftMargin + plotW, chanceY);
            }
            g2.setStroke(oldStroke);

            // Draw Class Curves
            for (int c = 0; c < 6; c++) {
                List<Point2D.Double> pts = isRocMode ? m.rocCurves.get(c) : m.prCurves.get(c);
                if (pts == null || pts.size() < 2) continue;

                Path2D.Double path = new Path2D.Double();
                for (int i = 0; i < pts.size(); i++) {
                    Point2D.Double p = pts.get(i);
                    double px = leftMargin + p.x * plotW;
                    double py = topMargin + (1.0 - p.y) * plotH;
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }

                g2.setColor(classColors[c]);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(path);
            }

            // Draw Macro-Average Curve (Bold White in Dark, Solid Black in Light Mode)
            if (isRocMode && m.macroRocCurve.size() > 1) {
                Path2D.Double macroPath = new Path2D.Double();
                for (int i = 0; i < m.macroRocCurve.size(); i++) {
                    Point2D.Double p = m.macroRocCurve.get(i);
                    double px = leftMargin + p.x * plotW;
                    double py = topMargin + (1.0 - p.y) * plotH;
                    if (i == 0) macroPath.moveTo(px, py);
                    else macroPath.lineTo(px, py);
                }
                g2.setColor(dark ? Color.WHITE : Color.BLACK);
                g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(macroPath);
            }

            // Border around plot area
            g2.setColor(dark ? new Color(75, 90, 110) : new Color(148, 163, 184));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(leftMargin, topMargin, plotW, plotH);

            // Draw Legend Box on the right
            drawLegendBox(g2, m, leftMargin + plotW + 15, topMargin, rightMargin - 20, classColors, dark);

            // Draw interactive hover crosshair
            if (mousePos != null && mousePos.x >= leftMargin && mousePos.x <= leftMargin + plotW
                    && mousePos.y >= topMargin && mousePos.y <= topMargin + plotH) {
                drawCrosshair(g2, mousePos.x, mousePos.y, leftMargin, topMargin, plotW, plotH, dark);
            }

            g2.dispose();
        }

        private void drawLegendBox(Graphics2D g2, ModelValidationData.ModelMetrics m, int lx, int ly, int lw, Color[] classColors, boolean dark) {
            int lh = isRocMode ? 255 : 225;

            // Box background
            g2.setColor(dark ? new Color(26, 32, 42) : new Color(248, 250, 252));
            g2.fillRoundRect(lx, ly, lw, lh, 8, 8);
            g2.setColor(dark ? new Color(55, 65, 80) : new Color(203, 213, 225));
            g2.drawRoundRect(lx, ly, lw, lh, 8, 8);

            // Title
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(dark ? new Color(0, 210, 255) : new Color(2, 132, 199));
            if (isEnglishMode) {
                g2.drawString(isRocMode ? "AUC METRIC:" : "AVERAGE PRECISION (AP):", lx + 10, ly + 20);
            } else {
                g2.drawString(isRocMode ? "METRYKA AUC:" : "ŚREDNIA PRECYZJA:", lx + 10, ly + 20);
            }

            int itemY = ly + 42;
            String[] classShortNames = ModelValidationData.getClassShortNames(isEnglishMode);
            for (int c = 0; c < 6; c++) {
                g2.setColor(classColors[c]);
                g2.fillRect(lx + 10, itemY - 9, 12, 12);
                g2.setColor(dark ? Color.WHITE : Color.BLACK);
                g2.drawRect(lx + 10, itemY - 9, 12, 12);

                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.setColor(dark ? new Color(220, 230, 240) : new Color(15, 23, 42));
                String name = classShortNames[c];
                g2.drawString(name, lx + 26, itemY + 1);

                double score = isRocMode ? m.classAuc[c] : m.classAp[c];
                String scoreStr = String.format(isRocMode ? "AUC=%.3f" : "AP=%.3f", score);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.setColor(classColors[c]);
                int sw = g2.getFontMetrics().stringWidth(scoreStr);
                g2.drawString(scoreStr, lx + lw - sw - 10, itemY + 1);

                itemY += 25;
            }

            // Macro-average item
            if (isRocMode) {
                g2.setColor(dark ? new Color(55, 65, 80) : new Color(203, 213, 225));
                g2.drawLine(lx + 8, itemY - 5, lx + lw - 8, itemY - 5);

                g2.setColor(dark ? Color.WHITE : Color.BLACK);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawLine(lx + 10, itemY + 4, lx + 22, itemY + 4);
                g2.setStroke(new BasicStroke(1.0f));

                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.setColor(dark ? Color.WHITE : Color.BLACK);
                g2.drawString(isEnglishMode ? "Macro-Average" : "Średnia Makro", lx + 26, itemY + 7);

                String macroStr = String.format("AUC=%.4f", m.macroAuc);
                g2.setColor(dark ? new Color(0, 220, 255) : new Color(2, 132, 199));
                int msw = g2.getFontMetrics().stringWidth(macroStr);
                g2.drawString(macroStr, lx + lw - msw - 10, itemY + 7);
            }
        }

        private void drawCrosshair(Graphics2D g2, int mx, int my, int lx, int ly, int pw, int ph, boolean dark) {
            g2.setColor(dark ? new Color(255, 255, 255, 90) : new Color(15, 23, 42, 90));
            g2.drawLine(lx, my, lx + pw, my);
            g2.drawLine(mx, ly, mx, ly + ph);

            double xVal = (double) (mx - lx) / pw;
            double yVal = 1.0 - (double) (my - ly) / ph;

            String coordStr = String.format("(X: %.3f, Y: %.3f)", xVal, yVal);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(dark ? Color.WHITE : Color.BLACK);
            g2.drawString(coordStr, mx + 8, my - 8);
        }
    }
}
