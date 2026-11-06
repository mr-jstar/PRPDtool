package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * Interactive Learning Curves Canvas displaying Training & Validation Loss and Accuracy dynamics.
 * Supports publication-grade Light/Dark modes, zone visualization, and high-res PNG export.
 */
public class LearningCurvesPanel extends JPanel {

    private final Window parentWindow;
    private final boolean isDarkMode;

    private boolean isLightMode = false;
    private boolean isEnglishMode = false;
    private String currentModelKey = "RESNET_18";
    private final JLabel modelLbl;
    private final JComboBox<String> modelCombo;
    private final JToggleButton lightModeToggle;
    private final JToggleButton langToggle;
    private final JButton exportBtn;
    private final JLabel infoLbl;
    private final CurvesCanvas canvas;

    public LearningCurvesPanel(Window parentWindow, boolean isDarkMode) {
        this.parentWindow = parentWindow;
        this.isDarkMode = isDarkMode;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        // Curves canvas initialized first
        this.canvas = new CurvesCanvas();

        // --- TOP TOOLBAR ---
        JPanel toolBar = new JPanel(new BorderLayout(10, 0));
        toolBar.setOpaque(false);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        modelLbl = new JLabel("Architektura Ucząca się:");
        modelLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        modelLbl.setForeground(isDarkMode ? new Color(200, 215, 230) : new Color(40, 50, 60));
        leftControls.add(modelLbl);

        modelCombo = new JComboBox<>(new String[]{
                "ResNet-18 (Deep Residual CNN)",
                "YOLOv8-PRPD (Spatial Object BBoxes)",
                "2D-CNN-LSTM (Spatio-Temporal)",
                "1D-CNN-LSTM (Waveform & Recurrence)",
                "1D-CNN (Temporal Pulse Morphology)",
                "ANN / MLP (Wielowarstwowy Perceptron)"
        });
        modelCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modelCombo.addActionListener(e -> {
            int idx = modelCombo.getSelectedIndex();
            switch (idx) {
                case 0: currentModelKey = "RESNET_18"; break;
                case 1: currentModelKey = "YOLO_V8"; break;
                case 2: currentModelKey = "CNN_LSTM_2D"; break;
                case 3: currentModelKey = "CNN_LSTM_1D"; break;
                case 4: currentModelKey = "CNN_1D"; break;
                case 5: currentModelKey = "ANN_MLP"; break;
                default: currentModelKey = "RESNET_18"; break;
            }
            canvas.repaint();
        });
        leftControls.add(modelCombo);

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
                (isEnglishMode ? "Learning_Curves_" : "Krzywe_Uczenia_") + currentModelKey + (isLightMode ? "_Light.png" : ".png")
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

        infoLbl = new JLabel();
        infoLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLbl.setForeground(isDarkMode ? new Color(200, 215, 230) : new Color(40, 50, 60));
        updateInfoText();
        bottomPanel.add(infoLbl, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void updateInfoText() {
        if (isEnglishMode) {
            infoLbl.setText("<html><b>Engineering Interpretation:</b> Learning curves illustrate optimization dynamics during neural network training. "
                    + "Early stopping preserves generalization, preventing the model from memorizing stationary interference (overfitting) on live substation measurements.</html>");
        } else {
            infoLbl.setText("<html><b>Interpretacja Inżynierska:</b> Krzywe uczenia ujawniają kondycję procesu optymalizacji sieci neuronowej. "
                    + "Optymalny punkt zatrzymania (Early Stopping) zapobiega zapamiętywaniu zakłóceń stacyjnych (overfitting) i gwarantuje stabilną generalizację w pomiarach on-site.</html>");
        }
    }

    public void setLightMode(boolean light) {
        this.isLightMode = light;
        if (lightModeToggle.isSelected() != light) {
            lightModeToggle.setSelected(light);
        }
        lightModeToggle.setText(light ? (isEnglishMode ? "Dark Mode" : "Tryb ciemny (Dark)") : (isEnglishMode ? "Light Mode (Paper)" : "Tryb do artykułu (Jasny)"));
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
        modelLbl.setText(en ? "Learning Architecture:" : "Architektura Ucząca się:");
        exportBtn.setText(en ? "Export Chart (PNG)" : "Eksportuj wykres (PNG)");
        lightModeToggle.setText(isLightMode ? (en ? "Dark Mode" : "Tryb ciemny (Dark)") : (en ? "Light Mode (Paper)" : "Tryb do artykułu (Jasny)"));
        updateInfoText();
        canvas.repaint();
    }

    public boolean isEnglishMode() {
        return isEnglishMode;
    }

    private class CurvesCanvas extends JPanel {

        private Point mousePos = null;

        CurvesCanvas() {
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

            int leftMargin = 60;
            int rightMargin = 60;
            int topMargin = 50;
            int bottomMargin = 50;

            int plotW = w - leftMargin - rightMargin;
            int plotH = h - topMargin - bottomMargin;
            if (plotW <= 0 || plotH <= 0) {
                g2.dispose();
                return;
            }

            // Draw Background Shaded Zones (Underfitting, Optimal, Overfitting)
            double xEp12 = leftMargin + (12.0 / 49.0) * plotW;
            double xEp36 = leftMargin + (36.0 / 49.0) * plotW;

            // Zone 1: Underfitting
            g2.setColor(dark ? new Color(40, 45, 55, 100) : new Color(241, 245, 249));
            g2.fillRect(leftMargin, topMargin, (int) Math.round(xEp12 - leftMargin), plotH);

            // Zone 2: Optimal Generalization
            g2.setColor(dark ? new Color(16, 75, 45, 50) : new Color(240, 253, 244));
            g2.fillRect((int) Math.round(xEp12), topMargin, (int) Math.round(xEp36 - xEp12), plotH);

            // Zone 3: Overfitting Risk
            g2.setColor(dark ? new Color(75, 30, 30, 60) : new Color(254, 242, 242));
            g2.fillRect((int) Math.round(xEp36), topMargin, (int) Math.round(leftMargin + plotW - xEp36), plotH);

            // Zone Labels at top of plot
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(dark ? new Color(150, 165, 185) : new Color(100, 116, 139));
            String z1 = isEnglishMode ? "UNDERFITTING ZONE" : "STREFA NIEDOUCZENIA";
            g2.drawString(z1, leftMargin + 8, topMargin + 18);

            g2.setColor(dark ? new Color(16, 185, 129) : new Color(21, 128, 61));
            String z2 = isEnglishMode ? "OPTIMAL GENERALIZATION" : "OPTYMALNA GENERALIZACJA";
            g2.drawString(z2, (int) Math.round(xEp12) + 10, topMargin + 18);

            g2.setColor(dark ? new Color(245, 100, 100) : new Color(185, 28, 28));
            String z3 = isEnglishMode ? "OVERFITTING RISK" : "RYZYKO PRZEUCZENIA";
            g2.drawString(z3, (int) Math.round(xEp36) + 10, topMargin + 18);

            // Draw Horizontal Grid (Loss on left 0.0 - 1.6, Accuracy on right 40% - 100%)
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            int gridSteps = 8;
            for (int i = 0; i <= gridSteps; i++) {
                double t = (double) i / gridSteps;
                int y = topMargin + (int) Math.round((1.0 - t) * plotH);

                g2.setColor(dark ? new Color(42, 50, 65) : new Color(226, 232, 240));
                g2.drawLine(leftMargin, y, leftMargin + plotW, y);

                // Left: Loss label (0.0 to 1.6)
                double lossVal = t * 1.6;
                g2.setColor(dark ? new Color(0, 210, 255) : new Color(2, 132, 199));
                String lStr = String.format("%.2f", lossVal);
                int lw = g2.getFontMetrics().stringWidth(lStr);
                g2.drawString(lStr, leftMargin - lw - 8, y + 4);

                // Right: Accuracy label (40% to 100%)
                double accVal = 40.0 + t * 60.0;
                g2.setColor(dark ? new Color(16, 185, 129) : new Color(21, 128, 61));
                String aStr = String.format("%.0f%%", accVal);
                g2.drawString(aStr, leftMargin + plotW + 8, y + 4);
            }

            // Draw Epoch markers on bottom axis
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(dark ? new Color(160, 175, 190) : new Color(71, 85, 105));
            for (int ep = 0; ep < 50; ep += 5) {
                int x = leftMargin + (int) Math.round((double) ep / 49.0 * plotW);
                g2.drawLine(x, topMargin + plotH, x, topMargin + plotH + 4);
                String epStr = String.valueOf(ep + 1);
                int ew = g2.getFontMetrics().stringWidth(epStr);
                g2.drawString(epStr, x - ew / 2, topMargin + plotH + 18);
            }

            // Axis Titles
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(dark ? new Color(200, 215, 230) : new Color(15, 23, 42));
            String epTitle = isEnglishMode ? "Training Epochs (1 .. 50)" : "Epoka Treningowa (Epochs 1 .. 50)";
            int etw = g2.getFontMetrics().stringWidth(epTitle);
            g2.drawString(epTitle, leftMargin + (plotW - etw) / 2, topMargin + plotH + 34);

            // Left Axis Title: Funkcja Straty (Loss)
            Graphics2D gRotL = (Graphics2D) g2.create();
            gRotL.translate(18, topMargin + plotH / 2);
            gRotL.rotate(-Math.PI / 2);
            gRotL.setColor(dark ? new Color(0, 210, 255) : new Color(2, 132, 199));
            String lossTitle = isEnglishMode ? "Loss Function (Cross-Entropy Loss)" : "Funkcja Straty (Loss - Cross-Entropy)";
            int ltw = gRotL.getFontMetrics().stringWidth(lossTitle);
            gRotL.drawString(lossTitle, -ltw / 2, 0);
            gRotL.dispose();

            // Right Axis Title: Dokładność (Accuracy)
            Graphics2D gRotR = (Graphics2D) g2.create();
            gRotR.translate(w - 18, topMargin + plotH / 2);
            gRotR.rotate(Math.PI / 2);
            gRotR.setColor(dark ? new Color(16, 185, 129) : new Color(21, 128, 61));
            String accTitle = isEnglishMode ? "Classification Accuracy (%)" : "Dokładność Klasyfikacji (Accuracy %)";
            int atw = gRotR.getFontMetrics().stringWidth(accTitle);
            gRotR.drawString(accTitle, -atw / 2, 0);
            gRotR.dispose();

            // Draw Curves (Clamped and clipped strictly inside the plot area)
            Path2D.Double trainLossPath = new Path2D.Double();
            Path2D.Double valLossPath = new Path2D.Double();
            Path2D.Double trainAccPath = new Path2D.Double();
            Path2D.Double valAccPath = new Path2D.Double();

            for (int ep = 0; ep < 50; ep++) {
                double px = leftMargin + (double) ep / 49.0 * plotW;

                // Loss: scale 0.0 to 1.6 (clamped strictly to [0.0, 1.55] so it never breaches top border)
                double clampedTL = Math.max(0.0, Math.min(1.55, m.trainLoss[ep]));
                double clampedVL = Math.max(0.0, Math.min(1.55, m.valLoss[ep]));
                double yTL = topMargin + (1.0 - clampedTL / 1.6) * plotH;
                double yVL = topMargin + (1.0 - clampedVL / 1.6) * plotH;

                // Accuracy: scale 0.40 to 1.00
                double clampedTA = Math.max(0.40, Math.min(1.0, m.trainAcc[ep]));
                double clampedVA = Math.max(0.40, Math.min(1.0, m.valAcc[ep]));
                double yTA = topMargin + (1.0 - (clampedTA - 0.40) / 0.60) * plotH;
                double yVA = topMargin + (1.0 - (clampedVA - 0.40) / 0.60) * plotH;

                if (ep == 0) {
                    trainLossPath.moveTo(px, yTL);
                    valLossPath.moveTo(px, yVL);
                    trainAccPath.moveTo(px, yTA);
                    valAccPath.moveTo(px, yVA);
                } else {
                    trainLossPath.lineTo(px, yTL);
                    valLossPath.lineTo(px, yVL);
                    trainAccPath.lineTo(px, yTA);
                    valAccPath.lineTo(px, yVA);
                }
            }

            // Curve Colors
            Color colTrainLoss = dark ? new Color(0, 180, 240) : new Color(29, 78, 216);
            Color colValLoss = dark ? new Color(130, 220, 255) : new Color(2, 132, 199);
            Color colTrainAcc = dark ? new Color(16, 185, 129) : new Color(21, 128, 61);
            Color colValAcc = dark ? new Color(163, 230, 53) : new Color(22, 163, 74);

            // Clip curves and early stopping marker strictly to plot boundaries
            Shape oldClip = g2.getClip();
            g2.clipRect(leftMargin, topMargin, plotW, plotH);

            // 1. Train Loss
            g2.setColor(colTrainLoss);
            g2.setStroke(new BasicStroke(2.2f));
            g2.draw(trainLossPath);

            // 2. Val Loss
            float[] dash = {6f, 5f};
            g2.setColor(colValLoss);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.draw(valLossPath);

            // 3. Train Acc
            g2.setColor(colTrainAcc);
            g2.setStroke(new BasicStroke(2.2f));
            g2.draw(trainAccPath);

            // 4. Val Acc
            g2.setColor(colValAcc);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.draw(valAccPath);

            // Early Stopping Checkpoint (Vertical line at best epoch)
            double bestX = leftMargin + (double) (m.bestEpoch - 1) / 49.0 * plotW;
            Color colBest = dark ? new Color(255, 200, 0) : new Color(180, 83, 9);
            g2.setColor(colBest);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawLine((int) Math.round(bestX), topMargin, (int) Math.round(bestX), topMargin + plotH);

            // Restore clip
            g2.setClip(oldClip);

            // Badge for Early Stopping (Cleanly positioned above curves)
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String bestText = isEnglishMode ? ("BEST MODEL (Epoch " + m.bestEpoch + ")") : ("BEST MODEL (Epoka " + m.bestEpoch + ")");
            int btw = g2.getFontMetrics().stringWidth(bestText);
            int badgeY = topMargin + 10;
            g2.setColor(dark ? new Color(20, 24, 32, 230) : new Color(255, 255, 255, 240));
            g2.fillRect((int) Math.round(bestX - btw / 2.0 - 6), badgeY, btw + 12, 20);
            g2.setColor(colBest);
            g2.drawRect((int) Math.round(bestX - btw / 2.0 - 6), badgeY, btw + 12, 20);
            g2.drawString(bestText, (int) Math.round(bestX - btw / 2.0), badgeY + 14);

            // Draw Top Legend
            drawLegend(g2, leftMargin, 18, colTrainLoss, colValLoss, colTrainAcc, colValAcc, dark);

            // Border
            g2.setColor(dark ? new Color(75, 90, 110) : new Color(148, 163, 184));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(leftMargin, topMargin, plotW, plotH);

            g2.dispose();
        }

        private void drawLegend(Graphics2D g2, int startX, int y, Color cTL, Color cVL, Color cTA, Color cVA, boolean dark) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            int curX = startX;

            curX = drawItem(g2, curX, y, "Train Loss", cTL, false, dark);
            curX = drawItem(g2, curX, y, "Validation Loss", cVL, true, dark);
            curX = drawItem(g2, curX, y, "Train Accuracy", cTA, false, dark);
            curX = drawItem(g2, curX, y, "Validation Accuracy", cVA, true, dark);
        }

        private int drawItem(Graphics2D g2, int x, int y, String label, Color c, boolean dashed, boolean dark) {
            g2.setColor(c);
            if (dashed) {
                float[] dash = {4f, 4f};
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            } else {
                g2.setStroke(new BasicStroke(2.5f));
            }
            g2.drawLine(x, y - 4, x + 18, y - 4);
            g2.setStroke(new BasicStroke(1.0f));

            g2.setColor(dark ? new Color(210, 225, 240) : new Color(15, 23, 42));
            g2.drawString(label, x + 24, y);
            return x + 24 + g2.getFontMetrics().stringWidth(label) + 20;
        }
    }
}
