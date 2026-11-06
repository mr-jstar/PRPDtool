package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Interactive 6x6 Confusion Matrix Heatmap for Partial Discharge Defect Classes.
 * Guarantees exact 1:1 SQUARE cells, publication-ready Light/Dark modes, and high-res PNG export.
 */
public class ConfusionMatrixPanel extends JPanel {

    private final Window parentWindow;
    private final boolean isDarkMode;

    private boolean isLightMode = false;
    private boolean isEnglishMode = false;
    private String currentModelKey = "ENSEMBLE";
    private boolean showPercentages = false;

    private final JLabel selectLbl;
    private final JComboBox<String> modelCombo;
    private final JToggleButton percentToggle;
    private final JToggleButton lightModeToggle;
    private final JToggleButton langToggle;
    private final JButton exportBtn;
    private final HeatmapCanvas canvas;
    private final JLabel summaryLabel;

    public ConfusionMatrixPanel(Window parentWindow, boolean isDarkMode) {
        this.parentWindow = parentWindow;
        this.isDarkMode = isDarkMode;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        // Heatmap canvas initialized first so listeners can reference it safely
        this.canvas = new HeatmapCanvas();

        // --- TOP TOOLBAR ---
        JPanel toolBar = new JPanel(new BorderLayout(10, 0));
        toolBar.setOpaque(false);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        selectLbl = new JLabel("Wybór Modelu AI:");
        selectLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        selectLbl.setForeground(isDarkMode ? new Color(200, 215, 230) : new Color(40, 50, 60));
        leftControls.add(selectLbl);

        modelCombo = new JComboBox<>();
        modelCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        for (String key : ModelValidationData.getAllModelKeys()) {
            modelCombo.addItem(ModelValidationData.getMetrics(key).displayName);
        }
        modelCombo.addActionListener(e -> {
            int idx = modelCombo.getSelectedIndex();
            if (idx >= 0 && idx < ModelValidationData.getAllModelKeys().size()) {
                currentModelKey = ModelValidationData.getAllModelKeys().get(idx);
                canvas.repaint();
                updateSummary();
            }
        });
        leftControls.add(modelCombo);

        percentToggle = new JToggleButton("Pokaż Procenty (%)");
        percentToggle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        percentToggle.setFocusPainted(false);
        percentToggle.addActionListener(e -> {
            showPercentages = percentToggle.isSelected();
            updateToggleTexts();
            canvas.repaint();
        });
        leftControls.add(percentToggle);

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
                (isEnglishMode ? "Confusion_Matrix_" : "Macierz_Pomylek_") + currentModelKey + (isLightMode ? "_Light" : "") + ".png"
        ));
        rightControls.add(exportBtn);

        toolBar.add(leftControls, BorderLayout.WEST);
        toolBar.add(rightControls, BorderLayout.EAST);
        add(toolBar, BorderLayout.NORTH);

        // --- HEATMAP CANVAS ---
        add(canvas, BorderLayout.CENTER);

        // --- BOTTOM SUMMARY ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(8, 6, 4, 6));

        summaryLabel = new JLabel();
        summaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        summaryLabel.setForeground(isDarkMode ? new Color(210, 225, 240) : new Color(30, 40, 50));
        bottomPanel.add(summaryLabel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        updateSummary();
    }

    private void updateToggleTexts() {
        if (showPercentages) {
            percentToggle.setText(isEnglishMode ? "Show Counts (N)" : "Pokaż Liczności (N)");
        } else {
            percentToggle.setText(isEnglishMode ? "Show Percentages (%)" : "Pokaż Procenty (%)");
        }
        lightModeToggle.setText(isLightMode
                ? (isEnglishMode ? "Dark Mode" : "Tryb ciemny (Dark)")
                : (isEnglishMode ? "Light Mode (Paper)" : "Tryb do artykułu (Jasny)"));
        langToggle.setText(isEnglishMode ? "Język: EN (Artykuł)" : "Podpisy: PL / EN");
        selectLbl.setText(isEnglishMode ? "Select AI Model:" : "Wybór Modelu AI:");
        exportBtn.setText(isEnglishMode ? "Export Chart (PNG)" : "Eksportuj wykres (PNG)");
    }

    public void setLightMode(boolean light) {
        this.isLightMode = light;
        if (lightModeToggle.isSelected() != light) {
            lightModeToggle.setSelected(light);
        }
        updateToggleTexts();
        canvas.setBackground(light ? Color.WHITE : (isDarkMode ? new Color(20, 24, 32) : Color.WHITE));
        canvas.repaint();
        updateSummary();
    }

    public boolean isLightMode() {
        return isLightMode;
    }

    public void setEnglishMode(boolean en) {
        this.isEnglishMode = en;
        if (langToggle.isSelected() != en) {
            langToggle.setSelected(en);
        }
        updateToggleTexts();
        updateSummary();
        canvas.repaint();
    }

    public boolean isEnglishMode() {
        return isEnglishMode;
    }

    private void updateSummary() {
        ModelValidationData.ModelMetrics m = ModelValidationData.getMetrics(currentModelKey);
        if (m == null) return;
        String colorAcc = isLightMode ? "#0284c7" : "#00e1ff";
        if (isEnglishMode) {
            summaryLabel.setText(String.format(
                    "<html>Model: <b style='color:%s;'>%s</b> | Overall Accuracy: <b>%.2f%%</b> | "
                            + "Macro Precision: <b>%.2f%%</b> | Macro Recall: <b>%.2f%%</b> | Macro F1-Score: <b>%.2f%%</b> | Test Set: <b>1,200 samples</b></html>",
                    colorAcc,
                    m.displayName,
                    m.accuracy * 100.0,
                    m.precision * 100.0,
                    m.recall * 100.0,
                    m.f1Score * 100.0
            ));
        } else {
            summaryLabel.setText(String.format(
                    "<html>Model: <b style='color:%s;'>%s</b> | Średnia Dokładność (Accuracy): <b>%.2f%%</b> | "
                            + "Precyzja Makro: <b>%.2f%%</b> | Czułość Makro: <b>%.2f%%</b> | F1-Score: <b>%.2f%%</b> | Testset: <b>1200 prób</b></html>",
                    colorAcc,
                    m.displayName,
                    m.accuracy * 100.0,
                    m.precision * 100.0,
                    m.recall * 100.0,
                    m.f1Score * 100.0
            ));
        }
    }

    private class HeatmapCanvas extends JPanel {
        private int hoveredRow = -1;
        private int hoveredCol = -1;

        HeatmapCanvas() {
            setOpaque(true);
            setBackground(isDarkMode ? new Color(20, 24, 32) : Color.WHITE);
            setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(45, 55, 70) : new Color(210, 220, 230), 1));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    checkHover(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredRow = -1;
                    hoveredCol = -1;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void checkHover(int mx, int my) {
            int leftMargin = 145;
            int topMargin = 65;
            int rightMargin = 85;
            int bottomMargin = 60;

            int availW = getWidth() - leftMargin - rightMargin;
            int availH = getHeight() - topMargin - bottomMargin;
            if (availW <= 0 || availH <= 0) return;

            int cellSize = Math.min(availW / 6, availH / 6);
            int gridTotal = cellSize * 6;
            int startX = leftMargin + (availW - gridTotal) / 2;
            int startY = topMargin + (availH - gridTotal) / 2;

            int c = (mx - startX) / cellSize;
            int r = (my - startY) / cellSize;

            if (c >= 0 && c < 6 && r >= 0 && r < 6 && mx >= startX && mx < startX + gridTotal && my >= startY && my < startY + gridTotal) {
                if (hoveredRow != r || hoveredCol != c) {
                    hoveredRow = r;
                    hoveredCol = c;
                    ModelValidationData.ModelMetrics m = ModelValidationData.getMetrics(currentModelKey);
                    int count = m.confusionMatrix[r][c];
                    double pct = (double) count / m.classSupport[r] * 100.0;
                    String[] shortNames = ModelValidationData.getClassShortNames(isEnglishMode);
                    setToolTipText(String.format(
                            isEnglishMode
                                    ? "<html><b>True Class:</b> %s<br><b>AI Prediction:</b> %s<br>Count: <b>%d</b> (%.1f%% of class samples)</html>"
                                    : "<html><b>Rzeczywista:</b> %s<br><b>Predykcja AI:</b> %s<br>Liczność: <b>%d</b> (%.1f%% próbek tej klasy)</html>",
                            shortNames[r],
                            shortNames[c],
                            count, pct));
                    repaint();
                }
            } else {
                if (hoveredRow != -1 || hoveredCol != -1) {
                    hoveredRow = -1;
                    hoveredCol = -1;
                    setToolTipText(null);
                    repaint();
                }
            }
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

            int leftMargin = 145;
            int topMargin = 65;
            int rightMargin = 85;
            int bottomMargin = 60;

            int availW = w - leftMargin - rightMargin;
            int availH = h - topMargin - bottomMargin;
            if (availW <= 0 || availH <= 0) {
                g2.dispose();
                return;
            }

            // GUARANTEE 1:1 SQUARE CELLS
            int cellSize = Math.min(availW / 6, availH / 6);
            cellSize = Math.max(26, cellSize);
            int gridTotal = cellSize * 6;

            // Center the square matrix within the available canvas
            int startX = leftMargin + (availW - gridTotal) / 2;
            int startY = topMargin + (availH - gridTotal) / 2;

            // Fill canvas background if in light mode
            if (!dark) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, w, h);
            }

            // Top Header: "PREDYKCJA MODELU AI (PREDICTED CLASS)"
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(dark ? new Color(0, 210, 255) : new Color(2, 132, 199));
            String topTitle = isEnglishMode ? "PREDICTED CLASS (AI CLASSIFIER OUTPUT)" : "PREDYKCJA MODELU AI (PRZEWIDYWANA KLASA WŁASNA)";
            int tw = g2.getFontMetrics().stringWidth(topTitle);
            g2.drawString(topTitle, startX + (gridTotal - tw) / 2, Math.max(22, startY - 32));

            // Left Header: "KLASA RZECZYWISTA (TRUE GROUND TRUTH)"
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(dark ? new Color(255, 180, 0) : new Color(194, 65, 12));
            Graphics2D gRot = (Graphics2D) g2.create();
            gRot.translate(Math.max(16, startX - 110), startY + gridTotal / 2);
            gRot.rotate(-Math.PI / 2);
            String leftTitle = isEnglishMode ? "TRUE CLASS (GROUND TRUTH)" : "KLASA RZECZYWISTA (GROUND TRUTH)";
            int ltw = gRot.getFontMetrics().stringWidth(leftTitle);
            gRot.drawString(leftTitle, -ltw / 2, 0);
            gRot.dispose();

            // Draw Column Headers (adapt text length to square cell size to avoid any overlaps)
            g2.setFont(new Font("Segoe UI", Font.BOLD, cellSize < 55 ? 9 : (cellSize < 75 ? 10 : 11)));
            for (int c = 0; c < 6; c++) {
                int cx = startX + c * cellSize;
                String cName;
                if (isEnglishMode) {
                    if (cellSize < 55) {
                        switch (c) {
                            case 0: cName = "Corona+"; break;
                            case 1: cName = "Corona-"; break;
                            case 2: cName = "Surface"; break;
                            case 3: cName = "Void"; break;
                            case 4: cName = "Float"; break;
                            default: cName = "Noise"; break;
                        }
                    } else if (cellSize < 82) {
                        switch (c) {
                            case 0: cName = "Corona (+)"; break;
                            case 1: cName = "Corona (-)"; break;
                            case 2: cName = "Surface"; break;
                            case 3: cName = "Void"; break;
                            case 4: cName = "Floating"; break;
                            default: cName = "Noise"; break;
                        }
                    } else {
                        cName = ModelValidationData.CLASS_SHORT_NAMES_EN[c];
                    }
                } else {
                    if (cellSize < 55) {
                        switch (c) {
                            case 0: cName = "Ulot+"; break;
                            case 1: cName = "Ulot-"; break;
                            case 2: cName = "Powierz."; break;
                            case 3: cName = "Void"; break;
                            case 4: cName = "Pływ."; break;
                            default: cName = "Szum"; break;
                        }
                    } else if (cellSize < 82) {
                        switch (c) {
                            case 0: cName = "Ulot (+)"; break;
                            case 1: cName = "Ulot (-)"; break;
                            case 2: cName = "Powierzchn."; break;
                            case 3: cName = "Pęcherz"; break;
                            case 4: cName = "Pływający"; break;
                            default: cName = "Szum tła"; break;
                        }
                    } else {
                        cName = ModelValidationData.CLASS_SHORT_NAMES[c];
                    }
                }
                g2.setColor(dark ? new Color(190, 205, 220) : new Color(30, 41, 59));
                int nw = g2.getFontMetrics().stringWidth(cName);
                g2.drawString(cName, cx + (cellSize - nw) / 2, startY - 9);
            }

            // Right header: "Czułość [TPR]" / "Recall [TPR]"
            g2.setColor(dark ? new Color(16, 185, 129) : new Color(5, 150, 105));
            g2.drawString(isEnglishMode ? "Recall [TPR]" : "Czułość [TPR]", startX + gridTotal + 10, startY - 10);

            // Draw Row Headers
            for (int r = 0; r < 6; r++) {
                int ry = startY + r * cellSize;
                String rName = ModelValidationData.getClassShortNames(isEnglishMode)[r];
                g2.setColor(dark ? new Color(190, 205, 220) : new Color(30, 41, 59));
                int rnw = g2.getFontMetrics().stringWidth(rName);
                g2.drawString(rName, startX - rnw - 10, ry + cellSize / 2 + 4);
            }

            // Draw Square Cells
            for (int r = 0; r < 6; r++) {
                int ry = startY + r * cellSize;
                int totalInRow = m.classSupport[r];

                for (int c = 0; c < 6; c++) {
                    int cx = startX + c * cellSize;
                    int count = m.confusionMatrix[r][c];
                    double ratio = (double) count / Math.max(1, totalInRow);

                    // Cell Color
                    Color cellColor;
                    if (r == c) {
                        // True positive gradient
                        float t = (float) Math.min(1.0, Math.max(0.0, (ratio - 0.70) / 0.30));
                        if (dark) {
                            int red = (int) (10 * (1 - t) + 16 * t);
                            int green = (int) (120 * (1 - t) + 185 * t);
                            int blue = (int) (190 * (1 - t) + 129 * t);
                            cellColor = new Color(red, green, blue);
                        } else {
                            // Light mode TP: royal blue/cyan gradient
                            int red = (int) (186 * (1 - t) + 2 * t);
                            int green = (int) (230 * (1 - t) + 132 * t);
                            int blue = (int) (253 * (1 - t) + 199 * t);
                            cellColor = new Color(red, green, blue);
                        }
                    } else {
                        // Misclassification gradient
                        if (count == 0) {
                            cellColor = dark ? new Color(26, 32, 42) : Color.WHITE;
                        } else {
                            float t = (float) Math.min(1.0, count / 20.0);
                            if (dark) {
                                int red = (int) (35 + 160 * t);
                                int green = (int) (32 - 10 * t);
                                int blue = (int) (40 - 10 * t);
                                cellColor = new Color(red, green, blue);
                            } else {
                                // Light mode error: soft coral/rose gradient
                                int red = (int) (255 * (1 - t) + 254 * t);
                                int green = (int) (255 * (1 - t) + 205 * t);
                                int blue = (int) (255 * (1 - t) + 205 * t);
                                cellColor = new Color(red, green, blue);
                            }
                        }
                    }

                    // Hover outline
                    if (r == hoveredRow && c == hoveredCol) {
                        cellColor = dark ? cellColor.brighter() : cellColor.darker();
                    }

                    g2.setColor(cellColor);
                    g2.fillRect(cx, ry, cellSize, cellSize);

                    // Cell Border
                    g2.setColor(dark ? new Color(42, 50, 65) : new Color(203, 213, 225));
                    g2.drawRect(cx, ry, cellSize, cellSize);

                    if (r == hoveredRow && c == hoveredCol) {
                        g2.setColor(dark ? Color.WHITE : new Color(2, 132, 199));
                        g2.setStroke(new BasicStroke(2f));
                        g2.drawRect(cx + 1, ry + 1, cellSize - 2, cellSize - 2);
                        g2.setStroke(new BasicStroke(1f));
                    }

                    // Text inside cell
                    g2.setFont(new Font("Segoe UI", Font.BOLD, cellSize > 50 ? 13 : 11));

                    Color textColor;
                    if (dark) {
                        textColor = Color.WHITE;
                    } else {
                        if (r == c) {
                            textColor = (ratio > 0.85) ? Color.WHITE : new Color(15, 23, 42);
                        } else {
                            textColor = (count > 0) ? new Color(153, 27, 27) : new Color(100, 116, 139);
                        }
                    }
                    g2.setColor(textColor);

                    String mainStr = showPercentages
                            ? String.format("%.1f%%", ratio * 100.0)
                            : String.valueOf(count);

                    int mw = g2.getFontMetrics().stringWidth(mainStr);
                    int textY = ry + (cellSize / 2) + (cellSize > 50 ? -2 : 4);
                    g2.drawString(mainStr, cx + (cellSize - mw) / 2, textY);

                    if (cellSize > 50) {
                        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                        g2.setColor(dark ? new Color(220, 235, 250, 200) : (r == c && ratio > 0.85 ? new Color(240, 249, 255) : new Color(71, 85, 105)));
                        String subStr = showPercentages
                                ? ("N=" + count)
                                : String.format("%.1f%%", ratio * 100.0);
                        int sw = g2.getFontMetrics().stringWidth(subStr);
                        g2.drawString(subStr, cx + (cellSize - sw) / 2, textY + 14);
                    }
                }

                // Row Sensitivity / Recall on the right
                int tp = m.confusionMatrix[r][r];
                double recall = (double) tp / totalInRow;
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.setColor(dark ? new Color(16, 185, 129) : new Color(5, 150, 105));
                String recStr = String.format("%.1f%%", recall * 100.0);
                g2.drawString(recStr, startX + gridTotal + 14, ry + cellSize / 2 + 4);
            }

            // Bottom Precision row
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(dark ? new Color(0, 210, 255) : new Color(2, 132, 199));
            String precHeader = isEnglishMode ? "Precision:" : "Precyzja:";
            int phw = g2.getFontMetrics().stringWidth(precHeader);
            g2.drawString(precHeader, startX - phw - 10, startY + gridTotal + 20);

            for (int c = 0; c < 6; c++) {
                int cx = startX + c * cellSize;
                int tp = m.confusionMatrix[c][c];
                int colSum = 0;
                for (int i = 0; i < 6; i++) colSum += m.confusionMatrix[i][c];
                double prec = (double) tp / Math.max(1, colSum);

                g2.setColor(dark ? new Color(0, 210, 255) : new Color(2, 132, 199));
                String precVal = String.format("%.1f%%", prec * 100.0);
                int pw = g2.getFontMetrics().stringWidth(precVal);
                g2.drawString(precVal, cx + (cellSize - pw) / 2, startY + gridTotal + 20);

                if (cellSize >= 55) {
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    g2.setColor(dark ? new Color(150, 165, 180) : new Color(100, 116, 139));
                    String colTotal = "(N=" + colSum + ")";
                    int ctw = g2.getFontMetrics().stringWidth(colTotal);
                    g2.drawString(colTotal, cx + (cellSize - ctw) / 2, startY + gridTotal + 34);
                }
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            }

            g2.dispose();
        }
    }
}
