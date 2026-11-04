package prpdtool;

import classifiers.Classifier;
import classifiers.HybridDiagnosticEngine;
import classifiers.HybridDiagnosticEngine.HybridResult;
import classifiers.Prediction;
import pipeline.DynamicPRPDHistogram;
import pipeline.PRPDHistogram;
import pipeline.StatisticalAnalyzer;
import pipeline.StatisticalAnalyzer.StatisticalResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * Modernized AI & Machine Learning Classification Window for PRPD Tool.
 * Features:
 * - Hybrid ensemble fusion (YOLO / RT-DETR vision detections + CIGRE statistical fingerprint)
 * - PRPD canvas with toggleable bounding boxes and class labels
 * - Probability distribution bars for all 6 defect classes
 * - Explainable AI (XAI) rationale list
 * - Model agreement comparison (Vision vs. Physics)
 * - Live continuous auto-classification timer
 * - Clipboard and report export capabilities
 */
public class HybridClassificationDialog extends JDialog {

    private final PRPDTool parent;
    private final boolean isDarkMode;

    private PrpdCanvasPanel canvasPanel;
    private JComboBox<String> modelSelectorCombo;
    private JCheckBox liveAutoCb;
    private JCheckBox showBoxesCb;
    private JSlider confSlider;
    private JLabel confValueLbl;
    private JButton classifyBtn;

    // Diagnostic Results Display
    private JLabel primaryClassLbl;
    private JLabel confidenceBadgeLbl;
    private JLabel riskBadgeLbl;
    private JLabel agreementLbl;
    private JPanel probabilityBarsPanel;
    private JLabel visionModelResultLbl;
    private JLabel statModelResultLbl;
    private JTextArea xaiArea;
    private JTextArea recommendationArea;

    private Timer liveTimer;
    private boolean isClassifying = false;
    private HybridResult currentResult;
    private BufferedImage currentPrpdImage;

    public HybridClassificationDialog(PRPDTool owner, boolean isDarkMode) {
        super(owner, "Centrum Klasyfikacji Hybrydowej AI (YOLO + CIGRE Ensemble)", false);
        this.parent = owner;
        this.isDarkMode = isDarkMode;

        setSize(1060, 720);
        setMinimumSize(new Dimension(880, 600));
        setLocationRelativeTo(owner);

        initUI();
        initTimer();
    }

    private void initUI() {
        Color bg = isDarkMode ? new Color(24, 26, 30) : new Color(245, 247, 250);
        Color panelBg = isDarkMode ? new Color(30, 33, 38) : Color.WHITE;
        Color borderCol = isDarkMode ? new Color(55, 60, 68) : new Color(210, 215, 225);
        Color accentCol = isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout());

        // 1. TOP TOOLBAR
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(panelBg);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
                new EmptyBorder(8, 16, 8, 16)
        ));

        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTools.setBackground(panelBg);

        JLabel titleLbl = new JLabel("Klasyfikacja Hybrydowa WNZ");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(accentCol);
        leftTools.add(titleLbl);

        leftTools.add(new JSeparator(JSeparator.VERTICAL));
        leftTools.add(new JLabel("Model wizualny:"));

        modelSelectorCombo = new JComboBox<>();
        modelSelectorCombo.setPreferredSize(new Dimension(190, 26));
        updateModelCombo();
        leftTools.add(modelSelectorCombo);

        leftTools.add(new JLabel("Próg ufności:"));
        confSlider = new JSlider(10, 90, 30);
        confSlider.setPreferredSize(new Dimension(100, 24));
        confSlider.setBackground(panelBg);
        confValueLbl = new JLabel("30%");
        confSlider.addChangeListener(e -> {
            confValueLbl.setText(confSlider.getValue() + "%");
            if (canvasPanel != null) canvasPanel.repaint();
        });
        leftTools.add(confSlider);
        leftTools.add(confValueLbl);

        topBar.add(leftTools, BorderLayout.WEST);

        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightTools.setBackground(panelBg);
        showBoxesCb = new JCheckBox("Pokaż ramki detekcji (BBoxes)", true);
        showBoxesCb.addActionListener(e -> canvasPanel.repaint());
        rightTools.add(showBoxesCb);

        liveAutoCb = new JCheckBox("Live Auto-Classify (1s)", false);
        liveAutoCb.addActionListener(e -> {
            if (liveAutoCb.isSelected()) {
                liveTimer.start();
            } else {
                liveTimer.stop();
            }
        });
        rightTools.add(liveAutoCb);

        topBar.add(rightTools, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. MAIN SPLIT CONTENT
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.48);
        splitPane.setDividerLocation(470);
        splitPane.setBorder(null);

        // LEFT: PRPD Image with Bounding Boxes
        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setBackground(panelBg);
        leftPanel.setBorder(new EmptyBorder(10, 12, 10, 8));

        JLabel leftTitle = new JLabel("Obraz Fazowy PRPD z Detekcjami Sieci Neuronowej");
        leftTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        leftTitle.setForeground(accentCol);
        leftPanel.add(leftTitle, BorderLayout.NORTH);

        canvasPanel = new PrpdCanvasPanel();
        leftPanel.add(canvasPanel, BorderLayout.CENTER);

        JLabel canvasFootnote = new JLabel("Format wejściowy: 448x448 RGB | Ramki YOLO skalowane proporcjonalnie");
        canvasFootnote.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        canvasFootnote.setForeground(isDarkMode ? new Color(140, 150, 165) : new Color(110, 120, 130));
        leftPanel.add(canvasFootnote, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);

        // RIGHT: Comprehensive Diagnosis & XAI Panel
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(panelBg);
        rightPanel.setBorder(new EmptyBorder(10, 8, 10, 14));

        // CARD 1: PRIMARY HYBRID DIAGNOSIS BANNER
        JPanel primaryCard = new JPanel(new BorderLayout(8, 4));
        primaryCard.setBackground(isDarkMode ? new Color(36, 40, 48) : new Color(240, 245, 252));
        primaryCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentCol, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JPanel titleAndClass = new JPanel(new GridLayout(2, 1, 0, 2));
        titleAndClass.setOpaque(false);
        JLabel tag = new JLabel("WIODĄCA DIAGNOZA HYBRYDOWA (AI + CIGRE):");
        tag.setFont(new Font("Segoe UI", Font.BOLD, 10));
        tag.setForeground(isDarkMode ? new Color(160, 175, 195) : new Color(100, 115, 135));

        primaryClassLbl = new JLabel("Brak aktywnej klasyfikacji");
        primaryClassLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        primaryClassLbl.setForeground(accentCol);

        titleAndClass.add(tag);
        titleAndClass.add(primaryClassLbl);
        primaryCard.add(titleAndClass, BorderLayout.CENTER);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badges.setOpaque(false);

        confidenceBadgeLbl = new JLabel("--%");
        confidenceBadgeLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        confidenceBadgeLbl.setOpaque(true);
        confidenceBadgeLbl.setBackground(new Color(2, 132, 199));
        confidenceBadgeLbl.setForeground(Color.WHITE);
        confidenceBadgeLbl.setBorder(new EmptyBorder(4, 10, 4, 10));

        riskBadgeLbl = new JLabel("NORMALNY");
        riskBadgeLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        riskBadgeLbl.setOpaque(true);
        riskBadgeLbl.setBackground(new Color(16, 185, 129));
        riskBadgeLbl.setForeground(Color.WHITE);
        riskBadgeLbl.setBorder(new EmptyBorder(4, 8, 4, 8));

        badges.add(confidenceBadgeLbl);
        badges.add(riskBadgeLbl);
        primaryCard.add(badges, BorderLayout.EAST);

        agreementLbl = new JLabel("Status zgodności modeli: oczekiwanie na pomiar");
        agreementLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        agreementLbl.setForeground(isDarkMode ? new Color(175, 185, 200) : new Color(90, 100, 115));
        primaryCard.add(agreementLbl, BorderLayout.SOUTH);

        rightPanel.add(primaryCard);
        rightPanel.add(Box.createVerticalStrut(10));

        // CARD 2: PROBABILITY DISTRIBUTION BARS
        JPanel probCard = new JPanel(new BorderLayout(0, 6));
        probCard.setBackground(panelBg);
        probCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "Rozkład Prawdopodobieństw Klas WNZ",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), accentCol),
                new EmptyBorder(6, 10, 8, 10)
        ));

        probabilityBarsPanel = new JPanel(new GridLayout(6, 1, 0, 4));
        probabilityBarsPanel.setBackground(panelBg);
        buildProbabilityBars();
        probCard.add(probabilityBarsPanel, BorderLayout.CENTER);

        rightPanel.add(probCard);
        rightPanel.add(Box.createVerticalStrut(10));

        // CARD 3: MODEL COMPARISON (VISION vs STATISTICAL)
        JPanel cmpCard = new JPanel(new GridLayout(2, 2, 8, 4));
        cmpCard.setBackground(panelBg);
        cmpCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "Porównanie Decyzji Modeli Składowych",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), accentCol),
                new EmptyBorder(4, 10, 6, 10)
        ));

        cmpCard.add(new JLabel("Detektor obrazowy YOLO/ONNX:"));
        visionModelResultLbl = new JLabel("-");
        visionModelResultLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cmpCard.add(visionModelResultLbl);

        cmpCard.add(new JLabel("Odcisk statystyczny CIGRE:"));
        statModelResultLbl = new JLabel("-");
        statModelResultLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cmpCard.add(statModelResultLbl);

        rightPanel.add(cmpCard);
        rightPanel.add(Box.createVerticalStrut(10));

        // CARD 4: EXPLAINABLE AI (XAI)
        JPanel xaiCard = new JPanel(new BorderLayout(0, 4));
        xaiCard.setBackground(panelBg);
        xaiCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "Wyjaśnienie Decyzji Modeli (Explainable AI - XAI)",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), accentCol),
                new EmptyBorder(4, 10, 6, 10)
        ));

        xaiArea = new JTextArea(4, 30);
        xaiArea.setEditable(false);
        xaiArea.setLineWrap(true);
        xaiArea.setWrapStyleWord(true);
        xaiArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        xaiArea.setText("Uruchom klasyfikację, aby wyświetlić uzasadnienie fizykalne decyzji.");
        xaiArea.setBackground(isDarkMode ? new Color(24, 26, 30) : new Color(250, 252, 255));
        xaiCard.add(new JScrollPane(xaiArea), BorderLayout.CENTER);

        rightPanel.add(xaiCard);
        rightPanel.add(Box.createVerticalStrut(10));

        // CARD 5: RECOMMENDATIONS
        JPanel recCard = new JPanel(new BorderLayout(0, 4));
        recCard.setBackground(panelBg);
        recCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderCol), "Rekomendacje Eksploatacyjne CIGRE",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), accentCol),
                new EmptyBorder(4, 10, 6, 10)
        ));

        recommendationArea = new JTextArea(2, 30);
        recommendationArea.setEditable(false);
        recommendationArea.setLineWrap(true);
        recommendationArea.setWrapStyleWord(true);
        recommendationArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        recommendationArea.setText("Brak wskazań.");
        recommendationArea.setBackground(isDarkMode ? new Color(24, 26, 30) : new Color(250, 252, 255));
        recCard.add(new JScrollPane(recommendationArea), BorderLayout.CENTER);

        rightPanel.add(recCard);

        JScrollPane rightScroll = new JScrollPane(rightPanel);
        rightScroll.setBorder(null);
        rightScroll.getVerticalScrollBar().setUnitIncrement(12);
        splitPane.setRightComponent(rightScroll);

        add(splitPane, BorderLayout.CENTER);

        // 3. BOTTOM CONTROL BAR
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(panelBg);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol),
                new EmptyBorder(10, 16, 10, 16)
        ));

        classifyBtn = new JButton("KLASYFIKUJ TERAZ (RUN INFERENCE)");
        classifyBtn.setFont(classifyBtn.getFont().deriveFont(Font.BOLD, 12f));
        classifyBtn.setBackground(new Color(2, 132, 199));
        classifyBtn.setForeground(Color.WHITE);
        classifyBtn.setPreferredSize(new Dimension(270, 32));
        classifyBtn.addActionListener(e -> classifyCurrentAsync());

        JPanel leftBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftBottom.setBackground(panelBg);
        leftBottom.add(classifyBtn);
        bottomBar.add(leftBottom, BorderLayout.WEST);

        JPanel rightBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBottom.setBackground(panelBg);

        JButton copyBtn = new JButton("Kopiuj diagnozę");
        copyBtn.addActionListener(e -> copyToClipboard());

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.addActionListener(e -> setVisible(false));

        rightBottom.add(copyBtn);
        rightBottom.add(closeBtn);
        bottomBar.add(rightBottom, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void initTimer() {
        liveTimer = new Timer(1000, e -> {
            if (isVisible() && !isClassifying) {
                classifyCurrentAsync();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (liveTimer != null) liveTimer.stop();
            }
        });
    }

    private void updateModelCombo() {
        modelSelectorCombo.removeAllItems();
        modelSelectorCombo.addItem("Wszystkie modele (Komitet)");
        if (parent == null) return;
        ArrayList<Classifier> list = parent.getClassifiers();
        if (list != null) {
            for (Classifier c : list) {
                if (c != null && c.ok()) {
                    modelSelectorCombo.addItem(c.name());
                }
            }
        }
    }

    private void buildProbabilityBars() {
        probabilityBarsPanel.removeAll();
        for (String cName : PRPDConstants.CLASSES) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);

            String polishLabel = HybridDiagnosticEngine.getPolishName(cName);
            JLabel nameLbl = new JLabel(polishLabel);
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            nameLbl.setPreferredSize(new Dimension(240, 18));

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(0);
            bar.setStringPainted(true);
            bar.setString("0.0%");
            bar.setForeground(PRPDConstants.CLASS_COLORS.getOrDefault(cName, Color.GRAY));
            bar.putClientProperty("className", cName);

            row.add(nameLbl, BorderLayout.WEST);
            row.add(bar, BorderLayout.CENTER);

            probabilityBarsPanel.add(row);
        }
    }

    public void updateAndShow() {
        updateModelCombo();
        setVisible(true);
        classifyCurrentAsync();
    }

    public void classifyCurrentAsync() {
        if (isClassifying) return;
        isClassifying = true;
        classifyBtn.setEnabled(false);
        classifyBtn.setText("Klasyfikowanie...");

        SwingWorker<HybridResult, Void> worker = new SwingWorker<>() {
            @Override
            protected HybridResult doInBackground() throws Exception {
                PRPDHistogram hist = parent.getHistogram();
                if (hist == null) return null;

                // 1. Grab 448x448 PRPD image
                currentPrpdImage = hist.getPRPD(448, 448);

                // 2. Select classifier and run vision inference
                ArrayList<Classifier> list = parent.getClassifiers();
                Prediction[] bestPredictions = new Prediction[0];
                float maxConf = -1.0f;

                int selectedIdx = modelSelectorCombo.getSelectedIndex();
                if (list != null && !list.isEmpty()) {
                    if (selectedIdx > 0 && (selectedIdx - 1) < list.size()) {
                        Classifier c = list.get(selectedIdx - 1);
                        if (c != null && c.ok()) {
                            bestPredictions = c.classify(currentPrpdImage);
                        }
                    } else {
                        // Committee: evaluate all and pick highest-confidence detection set
                        for (Classifier c : list) {
                            if (c != null && c.ok()) {
                                try {
                                    Prediction[] preds = c.classify(currentPrpdImage);
                                    for (Prediction p : preds) {
                                        if (p.confidence > maxConf) {
                                            maxConf = p.confidence;
                                            bestPredictions = preds;
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                // 3. Compute statistical fingerprint
                StatisticalResult sr = null;
                if (hist instanceof DynamicPRPDHistogram) {
                    DynamicPRPDHistogram dh = (DynamicPRPDHistogram) hist;
                    sr = StatisticalAnalyzer.compute(dh.getPhases(), dh.getAmps(), dh.getTimes(),
                            dh.getSize(), 360, parent.getF0());
                }

                // 4. Perform hybrid fusion
                return HybridDiagnosticEngine.analyze(bestPredictions, sr);
            }

            @Override
            protected void done() {
                isClassifying = false;
                classifyBtn.setEnabled(true);
                classifyBtn.setText("KLASYFIKUJ TERAZ (RUN INFERENCE)");
                try {
                    currentResult = get();
                    if (currentResult != null) {
                        displayResult(currentResult);
                    }
                } catch (Exception ex) {
                    primaryClassLbl.setText("Błąd klasyfikacji: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void displayResult(HybridResult res) {
        primaryClassLbl.setText(res.polishName.toUpperCase());
        Color classCol = PRPDConstants.CLASS_COLORS.getOrDefault(res.primaryClass, Color.CYAN);
        primaryClassLbl.setForeground(classCol);

        confidenceBadgeLbl.setText(String.format(Locale.US, "%.1f%%", res.primaryConfidence * 100.0f));

        riskBadgeLbl.setText(res.riskLevel);
        riskBadgeLbl.setBackground(switch (res.riskLevel) {
            case "KRYTYCZNY" -> new Color(239, 68, 68);
            case "OSTRZEGAWCZY" -> new Color(245, 158, 11);
            default -> new Color(16, 185, 129);
        });

        agreementLbl.setText(res.modelAgreement);

        // Update probability bars
        for (Component comp : probabilityBarsPanel.getComponents()) {
            if (comp instanceof JPanel) {
                for (Component sub : ((JPanel) comp).getComponents()) {
                    if (sub instanceof JProgressBar) {
                        JProgressBar bar = (JProgressBar) sub;
                        String cKey = (String) bar.getClientProperty("className");
                        float p = res.hybridProbabilities.getOrDefault(cKey, 0.0f);
                        int pct = Math.round(p * 100.0f);
                        bar.setValue(pct);
                        bar.setString(String.format(Locale.US, "%.1f%%", p * 100.0f));
                    }
                }
            }
        }

        // Model comparison labels
        String topVision = "Brak jednoznacznej detekcji";
        float topVConf = 0.0f;
        for (Map.Entry<String, Float> entry : res.visionProbabilities.entrySet()) {
            if (entry.getValue() > topVConf) {
                topVConf = entry.getValue();
                topVision = HybridDiagnosticEngine.getPolishName(entry.getKey()) + String.format(Locale.US, " (%.1f%%)", topVConf * 100.0f);
            }
        }
        visionModelResultLbl.setText(topVision);

        String topStat = "Brak odcisku";
        float topSConf = 0.0f;
        for (Map.Entry<String, Float> entry : res.statisticalProbabilities.entrySet()) {
            if (entry.getValue() > topSConf) {
                topSConf = entry.getValue();
                topStat = HybridDiagnosticEngine.getPolishName(entry.getKey()) + String.format(Locale.US, " (%.1f%%)", topSConf * 100.0f);
            }
        }
        statModelResultLbl.setText(topStat);

        // XAI text
        StringBuilder xaiSb = new StringBuilder();
        for (String rationale : res.xaiExplanations) {
            xaiSb.append("• ").append(rationale).append("\n\n");
        }
        xaiArea.setText(xaiSb.toString().trim());

        // Recommendation
        recommendationArea.setText(res.recommendedAction);

        // Repaint canvas
        canvasPanel.repaint();
    }

    private void copyToClipboard() {
        if (currentResult == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("DIAGNOZA HYBRYDOWA WNZ:\n");
        sb.append("Forma wyładowania: ").append(currentResult.polishName).append("\n");
        sb.append("Pewność fuzji: ").append(String.format(Locale.US, "%.1f%%", currentResult.primaryConfidence * 100.0f)).append("\n");
        sb.append("Ocena ryzyka: ").append(currentResult.riskLevel).append("\n");
        sb.append("Zgodność modeli: ").append(currentResult.modelAgreement).append("\n\n");
        sb.append("Wyjaśnienie decyzji (XAI):\n");
        for (String s : currentResult.xaiExplanations) {
            sb.append("- ").append(s).append("\n");
        }
        sb.append("\nRekomendacja:\n").append(currentResult.recommendedAction).append("\n");

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        JOptionPane.showMessageDialog(this, "Skopiowano podsumowanie diagnozy hybrydowej do schowka!", "Schowek", JOptionPane.INFORMATION_MESSAGE);
    }

    // CANVAS PANEL RENDERING PRPD IMAGE & BOUNDING BOXES
    private class PrpdCanvasPanel extends JPanel {
        public PrpdCanvasPanel() {
            setBackground(isDarkMode ? new Color(18, 20, 24) : Color.BLACK);
            setPreferredSize(new Dimension(448, 448));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth();
            int h = getHeight();

            if (currentPrpdImage == null) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                g2.drawString("Kliknij 'KLASYFIKUJ TERAZ' aby załadować obraz PRPD", w / 2 - 140, h / 2);
                g2.dispose();
                return;
            }

            // Draw scaled PRPD image preserving aspect ratio
            int imgW = currentPrpdImage.getWidth();
            int imgH = currentPrpdImage.getHeight();
            double scale = Math.min((double) w / imgW, (double) h / imgH);
            int drawW = (int) (imgW * scale);
            int drawH = (int) (imgH * scale);
            int offX = (w - drawW) / 2;
            int offY = (h - drawH) / 2;

            g2.drawImage(currentPrpdImage, offX, offY, drawW, drawH, null);

            // Draw bounding boxes if selected
            if (showBoxesCb.isSelected() && currentResult != null && currentResult.visionDetections != null) {
                float minConf = confSlider.getValue() / 100.0f;
                for (Prediction p : currentResult.visionDetections) {
                    if (p.classId != -1 && p.box != null && p.confidence >= minConf) {
                        float[] b = p.box; // [xc, yc, w, h] normalized 0.0 to 1.0
                        int bx = offX + (int) Math.round((b[0] - b[2] / 2.0f) * drawW);
                        int by = offY + (int) Math.round((b[1] - b[3] / 2.0f) * drawH);
                        int bw = (int) Math.round(b[2] * drawW);
                        int bh = (int) Math.round(b[3] * drawH);

                        Color boxCol = PRPDConstants.CLASS_COLORS.getOrDefault(p.className(), Color.CYAN);

                        // Box outline
                        g2.setColor(boxCol);
                        g2.setStroke(new BasicStroke(2.2f));
                        g2.drawRect(bx, by, bw, bh);

                        // Tag background
                        String label = p.className() + " " + Math.round(p.confidence * 100.0f) + "%";
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                        int tagW = g2.getFontMetrics().stringWidth(label) + 8;
                        int tagH = 16;
                        int tagY = Math.max(offY, by - tagH);

                        g2.setColor(new Color(boxCol.getRed(), boxCol.getGreen(), boxCol.getBlue(), 220));
                        g2.fillRect(bx, tagY, tagW, tagH);

                        g2.setColor(Color.BLACK);
                        g2.drawString(label, bx + 4, tagY + 12);
                    }
                }
            }

            g2.setColor(new Color(60, 65, 75));
            g2.drawRect(offX, offY, drawW - 1, drawH - 1);
            g2.dispose();
        }
    }
}
