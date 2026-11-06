package prpdtool;

import classifiers.Classifier;
import classifiers.HybridDiagnosticEngine;
import classifiers.HybridDiagnosticEngine.HybridResult;
import classifiers.Prediction;
import classifiers.PRPDModelRegistry;
import classifiers.StatisticalFingerprintClassifier;
import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.HashMap;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modernized AI & Machine Learning Classification Window for PRPD Tool.
 * Features:
 * - Hybrid ensemble fusion (YOLO / RT-DETR vision detections + CIGRE statistical fingerprint)
 * - PRPD canvas with toggleable bounding boxes (BBoxes) with auto-cluster detection
 * - Fixed banner card height preventing vertical distortion
 * - Probability distribution bars for all 6 defect classes
 * - Explainable AI (XAI) rationale list
 * - Model agreement comparison (Vision vs. Physics)
 * - Interactive step-by-step help guide and pipeline flowchart (IEC 60270 / CIGRE WG 21.03)
 * - Clipboard export capabilities
 */
public class HybridClassificationDialog extends JDialog {

    private final PRPDTool parent;
    private final boolean isDarkMode;

    private PrpdCanvasPanel canvasPanel;
    private JComboBox<ModelComboItem> modelSelectorCombo;
    private JLabel bannerTagLbl;
    private boolean isUpdatingCombo = false;

    public static class ModelComboItem {
        public final String label;
        public final PRPDModelRegistry.ModelId modelId;
        public final Classifier customClassifier;

        public ModelComboItem(String label, PRPDModelRegistry.ModelId modelId, Classifier customClassifier) {
            this.label = label;
            this.modelId = modelId;
            this.customClassifier = customClassifier;
        }

        @Override
        public String toString() {
            return label;
        }
    }
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

    private boolean isClassifying = false;
    private HybridResult currentResult;
    private BufferedImage currentPrpdImage;

    // Help & Guide Dialog
    private HybridClassificationHelpDialog helpDialog;

    public static class BoundingBoxInfo {
        public final String className;
        public final String label;
        public final float confidence;
        public final float xc, yc, w, h; // 0.0 to 1.0 normalized coordinates
        public final Color color;

        public BoundingBoxInfo(String className, String label, float confidence, float xc, float yc, float w, float h, Color color) {
            this.className = className;
            this.label = label;
            this.confidence = confidence;
            this.xc = xc;
            this.yc = yc;
            this.w = w;
            this.h = h;
            this.color = color;
        }
    }

    public HybridClassificationDialog(PRPDTool owner, boolean isDarkMode) {
        super(owner, "AI Classification Center", false);
        this.parent = owner;
        this.isDarkMode = isDarkMode;

        // Generous default size ensuring no controls, badges or labels are clipped
        setSize(1320, 820);
        setMinimumSize(new Dimension(1120, 680));
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

        // 1. TOP TOOLBAR
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(panelBg);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
                new EmptyBorder(8, 16, 8, 16)
        ));

        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftTools.setBackground(panelBg);

        JLabel titleLbl = new JLabel("AI Classification Center");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(accentCol);
        leftTools.add(titleLbl);

        JButton titleHelpBtn = new JButton("[?]");
        titleHelpBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleHelpBtn.setMargin(new Insets(1, 4, 1, 4));
        titleHelpBtn.setFocusPainted(false);
        titleHelpBtn.setToolTipText("Otwórz przewodnik i schemat blokowy przetwarzania AI");
        titleHelpBtn.addActionListener(e -> showHelpDialog("PIPELINE_MAP"));
        leftTools.add(titleHelpBtn);

        leftTools.add(new JSeparator(JSeparator.VERTICAL));
        leftTools.add(new JLabel("Model:"));

        modelSelectorCombo = new JComboBox<>();
        modelSelectorCombo.setPreferredSize(new Dimension(315, 26));
        updateModelCombo();
        modelSelectorCombo.addActionListener(e -> {
            if (!isUpdatingCombo && !isClassifying) {
                classifyCurrentAsync();
            }
        });
        leftTools.add(modelSelectorCombo);

        leftTools.add(new JLabel("Próg ufności:"));
        confSlider = new JSlider(5, 90, 20); // Default 20% so that 29.8% detections are visible immediately
        confSlider.setPreferredSize(new Dimension(85, 24));
        confSlider.setBackground(panelBg);
        confValueLbl = new JLabel("20%");
        confSlider.addChangeListener(e -> {
            confValueLbl.setText(confSlider.getValue() + "%");
            if (canvasPanel != null) canvasPanel.repaint();
        });
        leftTools.add(confSlider);
        leftTools.add(confValueLbl);

        topBar.add(leftTools, BorderLayout.WEST);

        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightTools.setBackground(panelBg);

        showBoxesCb = new JCheckBox("Pokaż ramki (BBoxes)", true);
        showBoxesCb.addActionListener(e -> {
            if (canvasPanel != null) canvasPanel.repaint();
        });
        rightTools.add(showBoxesCb);

        JButton helpBtn = new JButton("Przewodnik i pomoc");
        helpBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        helpBtn.setFocusPainted(false);
        helpBtn.addActionListener(e -> showHelpDialog("PIPELINE_MAP"));
        rightTools.add(helpBtn);

        topBar.add(rightTools, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. MAIN SPLIT CONTENT
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.44);
        splitPane.setDividerLocation(510);
        splitPane.setBorder(null);

        // LEFT: PRPD Image with Bounding Boxes
        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setBackground(panelBg);
        leftPanel.setBorder(new EmptyBorder(10, 12, 10, 8));

        JPanel leftTitleBar = new JPanel(new BorderLayout());
        leftTitleBar.setOpaque(false);
        JLabel leftTitle = new JLabel("Obraz Fazowy PRPD z Detekcjami Sieci Neuronowej");
        leftTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        leftTitle.setForeground(accentCol);
        leftTitleBar.add(leftTitle, BorderLayout.WEST);

        JButton canvasHelpBtn = new JButton("[?]");
        canvasHelpBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        canvasHelpBtn.setMargin(new Insets(1, 4, 1, 4));
        canvasHelpBtn.setFocusPainted(false);
        canvasHelpBtn.setToolTipText("Wyjaśnienie ramienia wizyjnego YOLO / RT-DETR");
        canvasHelpBtn.addActionListener(e -> showHelpDialog("VISION_MODELS"));
        leftTitleBar.add(canvasHelpBtn, BorderLayout.EAST);

        leftPanel.add(leftTitleBar, BorderLayout.NORTH);

        canvasPanel = new PrpdCanvasPanel();
        leftPanel.add(canvasPanel, BorderLayout.CENTER);

        JLabel canvasFootnote = new JLabel("Format wejściowy: 448x448 RGB | Ramki detekcji skalowane do skupisk WNZ");
        canvasFootnote.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        canvasFootnote.setForeground(isDarkMode ? new Color(140, 150, 165) : new Color(110, 120, 130));
        leftPanel.add(canvasFootnote, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);

        // RIGHT: Comprehensive Diagnosis & XAI Panel
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(panelBg);
        rightPanel.setBorder(new EmptyBorder(10, 8, 10, 14));

        // CARD 1: PRIMARY HYBRID DIAGNOSIS BANNER (LOCKED VERTICALLY)
        JPanel primaryCard = new JPanel(new BorderLayout(8, 2));
        primaryCard.setBackground(isDarkMode ? new Color(36, 40, 48) : new Color(240, 245, 252));
        primaryCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentCol, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));
        // Strict vertical locking: prevents ugly vertical stretching when resizing window
        primaryCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        primaryCard.setPreferredSize(new Dimension(500, 86));
        primaryCard.setMinimumSize(new Dimension(300, 86));

        JPanel titleAndClass = new JPanel(new GridLayout(2, 1, 0, 2));
        titleAndClass.setOpaque(false);
        bannerTagLbl = new JLabel("WIODĄCA DIAGNOZA HYBRYDOWA (KOMITET AI + CIGRE):");
        bannerTagLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        bannerTagLbl.setForeground(isDarkMode ? new Color(160, 175, 195) : new Color(100, 115, 135));

        primaryClassLbl = new JLabel("Brak aktywnej klasyfikacji");
        primaryClassLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        primaryClassLbl.setForeground(accentCol);

        titleAndClass.add(bannerTagLbl);
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
        probCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 185));

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
        cmpCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));

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
        rightPanel.add(Box.createVerticalGlue());

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
        classifyBtn.setPreferredSize(new Dimension(280, 32));
        classifyBtn.addActionListener(e -> classifyCurrentAsync());

        JPanel leftBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftBottom.setBackground(panelBg);
        leftBottom.add(classifyBtn);
        bottomBar.add(leftBottom, BorderLayout.WEST);

        JPanel rightBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBottom.setBackground(panelBg);

        JButton copyBtn = new JButton("Kopiuj diagnozę");
        copyBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copyBtn.addActionListener(e -> copyToClipboard());

        JButton helpBottomBtn = new JButton("Przewodnik i pomoc");
        helpBottomBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        helpBottomBtn.addActionListener(e -> showHelpDialog("PIPELINE_MAP"));

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        closeBtn.addActionListener(e -> setVisible(false));

        rightBottom.add(copyBtn);
        rightBottom.add(helpBottomBtn);
        rightBottom.add(closeBtn);
        bottomBar.add(rightBottom, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);
    }

    public void showHelpDialog(String chapterKey) {
        if (helpDialog == null) {
            helpDialog = new HybridClassificationHelpDialog(this, isDarkMode);
        }
        if (chapterKey != null) {
            helpDialog.showTopic(chapterKey);
        } else {
            helpDialog.setVisible(true);
        }
    }

    private void updateModelCombo() {
        isUpdatingCombo = true;
        try {
            ModelComboItem prev = (ModelComboItem) modelSelectorCombo.getSelectedItem();
            modelSelectorCombo.removeAllItems();

            // 1. Committee (Ensemble)
            modelSelectorCombo.addItem(new ModelComboItem("Komitet hybrydowy (Wszystkie modele AI + CIGRE)", null, null));

            // 2. All 11 models from PRPDModelRegistry
            for (PRPDModelRegistry.ModelId id : PRPDModelRegistry.ModelId.values()) {
                modelSelectorCombo.addItem(new ModelComboItem(id.getDisplayName() + " [" + id.getFamily().getDisplayName() + "]", id, null));
            }

            // 3. Custom classifiers from disk if loaded
            if (parent != null) {
                ArrayList<Classifier> list = parent.getClassifiers();
                if (list != null) {
                    for (Classifier c : list) {
                        if (c != null && c.ok()) {
                            modelSelectorCombo.addItem(new ModelComboItem("Plik ONNX: " + c.name(), null, c));
                        }
                    }
                }
            }

            if (prev != null) {
                for (int i = 0; i < modelSelectorCombo.getItemCount(); i++) {
                    ModelComboItem it = modelSelectorCombo.getItemAt(i);
                    if (it.modelId == prev.modelId && Objects.equals(it.label, prev.label)) {
                        modelSelectorCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            isUpdatingCombo = false;
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
            nameLbl.setPreferredSize(new Dimension(280, 18));

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

        final ModelComboItem selectedItem = (ModelComboItem) modelSelectorCombo.getSelectedItem();

        SwingWorker<HybridResult, Void> worker = new SwingWorker<>() {
            @Override
            protected HybridResult doInBackground() throws Exception {
                PRPDHistogram hist = (parent != null) ? parent.getHistogram() : null;
                if (hist != null) {
                    currentPrpdImage = hist.getPRPD(448, 448);
                } else {
                    currentPrpdImage = new BufferedImage(448, 448, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = currentPrpdImage.createGraphics();
                    g.setColor(new Color(20, 24, 30));
                    g.fillRect(0, 0, 448, 448);
                    g.dispose();
                }

                // 2. Compute statistical fingerprint
                StatisticalResult sr = null;
                if (hist instanceof DynamicPRPDHistogram) {
                    DynamicPRPDHistogram dh = (DynamicPRPDHistogram) hist;
                    sr = StatisticalAnalyzer.compute(dh.getPhases(), dh.getAmps(), dh.getTimes(),
                            dh.getSize(), 360, (parent != null) ? parent.getF0() : 50.0);
                }

                // 3. Obtain pulse waveform
                double[] pulse;
                PulseWaveform maxP = PulseWaveformRepository.getInstance().getMaxPulse();
                if (maxP != null && maxP.raw != null && maxP.raw.length >= 64) {
                    pulse = maxP.raw.clone();
                } else {
                    pulse = generateSyntheticPDPulse();
                }

                // 4. Run vision detections (YOLO) so BBoxes can always be available
                ArrayList<Classifier> list = (parent != null) ? parent.getClassifiers() : null;
                Prediction[] visionDetections = new Prediction[0];
                float maxConf = -1.0f;
                if (list != null && !list.isEmpty()) {
                    for (Classifier c : list) {
                        if (c != null && c.ok()) {
                            try {
                                Prediction[] preds = c.classify(currentPrpdImage);
                                for (Prediction p : preds) {
                                    if (p.confidence > maxConf) {
                                        maxConf = p.confidence;
                                        visionDetections = preds;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // 5. Evaluate chosen model or committee
                if (selectedItem == null || (selectedItem.modelId == null && selectedItem.customClassifier == null)) {
                    // Full Committee Hybrid Fusion
                    return HybridDiagnosticEngine.analyze(visionDetections, sr);
                } else if (selectedItem.customClassifier != null) {
                    // Specific custom ONNX classifier
                    Prediction[] preds = selectedItem.customClassifier.classify(currentPrpdImage);
                    return HybridDiagnosticEngine.analyze(preds, sr);
                } else {
                    // Specific Model from PRPDModelRegistry
                    PRPDModelRegistry.ModelId mId = selectedItem.modelId;
                    StatisticalFingerprintClassifier.Evaluation statEval = StatisticalFingerprintClassifier.evaluate(sr);
                    PRPDModelRegistry.ModelOutput out = PRPDModelRegistry.evaluateModel(mId, sr, statEval, pulse, 125e6);
                    return mapModelOutputToHybridResult(out, visionDetections, sr, statEval);
                }
            }

            @Override
            protected void done() {
                isClassifying = false;
                classifyBtn.setEnabled(true);
                classifyBtn.setText("KLASYFIKUJ TERAZ (RUN INFERENCE)");
                try {
                    currentResult = get();
                    if (currentResult != null) {
                        displayResult(currentResult, selectedItem);
                    }
                } catch (Exception ex) {
                    primaryClassLbl.setText("Błąd klasyfikacji: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private HybridResult mapModelOutputToHybridResult(PRPDModelRegistry.ModelOutput out,
                                                      Prediction[] visionDetections,
                                                      StatisticalResult sr,
                                                      StatisticalFingerprintClassifier.Evaluation statEval) {
        String pClass = out.dominantClass;
        float pConf = out.confidence;
        String pName = out.polishName;

        String risk;
        if ("internal".equalsIgnoreCase(pClass) || "surface".equalsIgnoreCase(pClass)) {
            risk = "KRYTYCZNY";
        } else if ("noise".equalsIgnoreCase(pClass)) {
            risk = "NORMALNY";
        } else {
            risk = "OSTRZEGAWCZY";
        }

        String agreement = String.format(Locale.US,
                "Model aktywny: %s | Czas DSP/AI: %.2f ms | Rodzina: %s",
                out.modelId.getDisplayName(), out.inferenceTimeMs, out.modelId.getFamily().getDisplayName());

        Map<String, Float> hybridProbs = new LinkedHashMap<>(out.probabilities);

        Map<String, Float> visionProbs = new LinkedHashMap<>();
        if (out.modelId.getFamily() == PRPDModelRegistry.ModelFamily.VISION_2D) {
            visionProbs.putAll(out.probabilities);
        } else if (visionDetections != null && visionDetections.length > 0) {
            for (Prediction p : visionDetections) {
                if (p != null && p.className != null) {
                    float cur = visionProbs.getOrDefault(p.className, 0.0f);
                    visionProbs.put(p.className, Math.max(cur, p.confidence));
                }
            }
        }
        for (String c : PRPDConstants.CLASSES) {
            if (!visionProbs.containsKey(c)) visionProbs.put(c, 0.05f);
        }

        Map<String, Float> statProbs = new LinkedHashMap<>();
        if (out.modelId.getFamily() == PRPDModelRegistry.ModelFamily.CIGRE_FEATURE_ML ||
            out.modelId.getFamily() == PRPDModelRegistry.ModelFamily.EXPERT_SYSTEM) {
            statProbs.putAll(out.probabilities);
        } else if (statEval != null && statEval.classProbabilities != null) {
            statProbs.putAll(statEval.classProbabilities);
        } else {
            for (String c : PRPDConstants.CLASSES) statProbs.put(c, 0.05f);
        }

        List<String> xai = new ArrayList<>();
        xai.add(String.format("Architektura i rodzina: %s [%s]", out.modelId.getDisplayName(), out.modelId.getFamily().getDisplayName()));
        xai.add(String.format("Format danych wejściowych: %s", out.inputRepresentation));
        xai.add(String.format("Uzasadnienie klasyfikacji: %s", out.diagnosticNote));
        if (sr != null && sr.pos != null && sr.neg != null) {
            double asym = (sr.neg.qMax > 0) ? (sr.pos.qMax / sr.neg.qMax) : sr.chargeAsymmetryQ;
            xai.add(String.format(Locale.US,
                    "Korelacja fizykalna CIGRE: Asymetria ładunku Q+ / Q- = %.2f, Fazy zapłonu φ_1+ = %.1f°, φ_1- = %.1f°, Skośność Sk = %.2f",
                    asym, sr.pos.inceptionPhase, sr.neg.inceptionPhase, sr.pos.phaseSkewness));
        }

        String recAction;
        if ("internal".equalsIgnoreCase(pClass)) {
            recAction = "Zalecane natychmiastowe badanie lokalizacyjne (Acoustic / UHF) i planowanie rewizji komory. Wyładowania wtrąceniowe w stałym dielektryku prowadzą do erozji drzewkowej (treeing) i nieodwracalnego przebicia.";
        } else if ("surface".equalsIgnoreCase(pClass)) {
            recAction = "Zalecana inspekcja czystości powierzchni izolatorów przepustowych/wsparciowych oraz kontrola wilgotności względnej. Oczyszczenie powierzchni z depozytów solnych/kurzu.";
        } else if ("floating".equalsIgnoreCase(pClass)) {
            recAction = "Zalecana rewizja mocowań ekranów, śrub i połączeń uziemiających w strefie silnego pola. Skontrolować stan styków i zacisków.";
        } else if ("corona_pos".equalsIgnoreCase(pClass)) {
            recAction = "Skontrolować geometrię elementów pod napięciem (ostrza, zaciski, krawędzie śrub). Zainstalować lub wyregulować pierścienie antyulotowe (grading rings).";
        } else if ("corona_neg".equalsIgnoreCase(pClass)) {
            recAction = "Skontrolować odległości izolacyjne od elementów uziemionych w pobliżu krawędzi ostrzowych. Sprawdzić stan powłok ekranujących.";
        } else {
            recAction = "Poziom zakłóceń w normie tła stacji. Kontynuować standardowy monitoring okresowy bez konieczności interwencji.";
        }

        return new HybridResult(pClass, pConf, pName, risk, agreement,
                hybridProbs, visionProbs, statProbs,
                visionDetections, xai, recAction);
    }

    private void displayResult(HybridResult res) {
        displayResult(res, (ModelComboItem) modelSelectorCombo.getSelectedItem());
    }

    private void displayResult(HybridResult res, ModelComboItem selectedItem) {
        this.currentResult = res;

        if (bannerTagLbl != null) {
            if (selectedItem == null || (selectedItem.modelId == null && selectedItem.customClassifier == null)) {
                bannerTagLbl.setText("WIODĄCA DIAGNOZA HYBRYDOWA (KOMITET AI + CIGRE):");
            } else if (selectedItem.modelId != null) {
                bannerTagLbl.setText("WIODĄCA DIAGNOZA: " + selectedItem.modelId.getDisplayName().toUpperCase() + ":");
            } else {
                bannerTagLbl.setText("WIODĄCA DIAGNOZA (" + selectedItem.label.toUpperCase() + "):");
            }
        }

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

        // Repaint canvas to update bounding boxes
        canvasPanel.repaint();
    }

    private static double[] generateSyntheticPDPulse() {
        int n = 1024;
        double[] p = new double[n];
        double fOsc = 18e6;
        double tau = 180e-9;
        int t0 = 250;
        double dt = 1.0 / 125e6;
        for (int i = 0; i < n; i++) {
            if (i >= t0) {
                double t = (i - t0) * dt;
                p[i] = 85.0 * Math.exp(-t / tau) * Math.sin(2 * Math.PI * fOsc * t);
            }
            p[i] += (Math.random() - 0.5) * 4.0;
        }
        return p;
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

    /**
     * Resolves active bounding boxes for PRPD canvas rendering.
     * Combines YOLO / RT-DETR vision box outputs with physical PRPD cluster extraction.
     */
    private List<BoundingBoxInfo> getActiveBoundingBoxes(float minConf) {
        List<BoundingBoxInfo> boxes = new ArrayList<>();
        if (currentResult == null) return boxes;

        // 1. Vision model detections
        if (currentResult.visionDetections != null && currentResult.visionDetections.length > 0) {
            for (Prediction p : currentResult.visionDetections) {
                if (p != null && p.className != null && !p.className.equalsIgnoreCase("none") && !p.className.equalsIgnoreCase("noise")) {
                    if (p.confidence >= minConf && p.box != null && p.box.length >= 4) {
                        float xc = p.box[0];
                        float yc = p.box[1];
                        float w = p.box[2];
                        float h = p.box[3];
                        if (xc > 0.0f && xc < 1.0f && yc > 0.0f && yc < 1.0f && w > 0.02f && w < 1.0f && h > 0.02f && h < 1.0f) {
                            Color col = PRPDConstants.CLASS_COLORS.getOrDefault(p.className(), new Color(0, 210, 255));
                            String lbl = HybridDiagnosticEngine.getPolishName(p.className()) + " " + Math.round(p.confidence * 100.0f) + "%";
                            boxes.add(new BoundingBoxInfo(p.className(), lbl, p.confidence, xc, yc, w, h, col));
                        }
                    }
                }
            }
        }

        // 2. Cluster extraction fallback from actual PRPD pulses if vision produced no bounding box
        if (boxes.isEmpty() && currentResult.primaryClass != null && !currentResult.primaryClass.equalsIgnoreCase("noise")) {
            if (currentResult.primaryConfidence >= minConf) {
                boxes.addAll(computeClusterBoxesFromPRPD(currentResult.primaryClass, currentResult.primaryConfidence));
            }
        }

        return boxes;
    }

    private List<BoundingBoxInfo> computeClusterBoxesFromPRPD(String primaryClass, float confidence) {
        List<BoundingBoxInfo> list = new ArrayList<>();
        if (parent == null) return list;
        PRPDHistogram hist = parent.getHistogram();
        if (!(hist instanceof DynamicPRPDHistogram)) return list;

        DynamicPRPDHistogram dh = (DynamicPRPDHistogram) hist;
        double[] phases = dh.getPhases();
        double[] amps = dh.getAmps();
        int size = dh.getSize();
        if (size == 0) return list;

        double minA = dh.getMin();
        double maxA = dh.getMax();
        if (maxA <= minA) { maxA = 100.0; minA = -100.0; }
        double rangeA = maxA - minA;

        String cLower = primaryClass.toLowerCase();
        Color col = PRPDConstants.CLASS_COLORS.getOrDefault(primaryClass, new Color(0, 210, 255));
        String label = HybridDiagnosticEngine.getPolishName(primaryClass) + " " + String.format(Locale.US, "%.1f%%", confidence * 100.0f);

        if (cLower.contains("corona -")) {
            // Negative corona: phase 180° - 360°, amp < 0
            double minP = 360, maxP = 0, minAmpVal = Double.MAX_VALUE, maxAmpVal = -Double.MAX_VALUE;
            int matched = 0;
            for (int i = 0; i < size; i++) {
                if (phases[i] >= 180.0 && phases[i] <= 360.0 && amps[i] < 0) {
                    minP = Math.min(minP, phases[i]); maxP = Math.max(maxP, phases[i]);
                    minAmpVal = Math.min(minAmpVal, amps[i]); maxAmpVal = Math.max(maxAmpVal, amps[i]);
                    matched++;
                }
            }
            if (matched > 0) {
                list.add(createBoxFromCluster(minP, maxP, minAmpVal, maxAmpVal, minA, rangeA, primaryClass, label, confidence, col));
            } else {
                list.add(new BoundingBoxInfo(primaryClass, label, confidence, 0.75f, 0.78f, 0.32f, 0.32f, col));
            }
        } else if (cLower.contains("corona +")) {
            // Positive corona: phase 0° - 180°, amp > 0
            double minP = 360, maxP = 0, minAmpVal = Double.MAX_VALUE, maxAmpVal = -Double.MAX_VALUE;
            int matched = 0;
            for (int i = 0; i < size; i++) {
                if (phases[i] >= 0.0 && phases[i] <= 180.0 && amps[i] > 0) {
                    minP = Math.min(minP, phases[i]); maxP = Math.max(maxP, phases[i]);
                    minAmpVal = Math.min(minAmpVal, amps[i]); maxAmpVal = Math.max(maxAmpVal, amps[i]);
                    matched++;
                }
            }
            if (matched > 0) {
                list.add(createBoxFromCluster(minP, maxP, minAmpVal, maxAmpVal, minA, rangeA, primaryClass, label, confidence, col));
            } else {
                list.add(new BoundingBoxInfo(primaryClass, label, confidence, 0.25f, 0.22f, 0.32f, 0.32f, col));
            }
        } else if (cLower.contains("void")) {
            // Internal void: two symmetric clusters on rising slopes
            double minP1 = 360, maxP1 = 0, minA1 = Double.MAX_VALUE, maxA1 = -Double.MAX_VALUE;
            int m1 = 0;
            double minP2 = 360, maxP2 = 0, minA2 = Double.MAX_VALUE, maxA2 = -Double.MAX_VALUE;
            int m2 = 0;
            for (int i = 0; i < size; i++) {
                if (phases[i] >= 0.0 && phases[i] <= 120.0 && amps[i] > 0) {
                    minP1 = Math.min(minP1, phases[i]); maxP1 = Math.max(maxP1, phases[i]);
                    minA1 = Math.min(minA1, amps[i]); maxA1 = Math.max(maxA1, amps[i]);
                    m1++;
                } else if (phases[i] >= 180.0 && phases[i] <= 300.0 && amps[i] < 0) {
                    minP2 = Math.min(minP2, phases[i]); maxP2 = Math.max(maxP2, phases[i]);
                    minA2 = Math.min(minA2, amps[i]); maxA2 = Math.max(maxA2, amps[i]);
                    m2++;
                }
            }
            if (m1 > 0) {
                list.add(createBoxFromCluster(minP1, maxP1, minA1, maxA1, minA, rangeA, primaryClass, label + " (+)", confidence, col));
            }
            if (m2 > 0) {
                list.add(createBoxFromCluster(minP2, maxP2, minA2, maxA2, minA, rangeA, primaryClass, label + " (-)", confidence, col));
            }
            if (list.isEmpty()) {
                list.add(new BoundingBoxInfo(primaryClass, label + " (+)", confidence, 0.16f, 0.30f, 0.24f, 0.30f, col));
                list.add(new BoundingBoxInfo(primaryClass, label + " (-)", confidence, 0.66f, 0.70f, 0.24f, 0.30f, col));
            }
        } else if (cLower.contains("surface")) {
            // Surface tracking
            double minP = 360, maxP = 0, minAmpVal = Double.MAX_VALUE, maxAmpVal = -Double.MAX_VALUE;
            int matched = 0;
            for (int i = 0; i < size; i++) {
                if (Math.abs(amps[i]) > 0.05 * Math.max(Math.abs(minA), Math.abs(maxA))) {
                    minP = Math.min(minP, phases[i]); maxP = Math.max(maxP, phases[i]);
                    minAmpVal = Math.min(minAmpVal, amps[i]); maxAmpVal = Math.max(maxAmpVal, amps[i]);
                    matched++;
                }
            }
            if (matched > 0) {
                list.add(createBoxFromCluster(minP, maxP, minAmpVal, maxAmpVal, minA, rangeA, primaryClass, label, confidence, col));
            } else {
                list.add(new BoundingBoxInfo(primaryClass, label, confidence, 0.35f, 0.40f, 0.48f, 0.52f, col));
            }
        } else {
            // Generic cluster
            double minP = 360, maxP = 0, minAmpVal = Double.MAX_VALUE, maxAmpVal = -Double.MAX_VALUE;
            int matched = 0;
            for (int i = 0; i < size; i++) {
                minP = Math.min(minP, phases[i]); maxP = Math.max(maxP, phases[i]);
                minAmpVal = Math.min(minAmpVal, amps[i]); maxAmpVal = Math.max(maxAmpVal, amps[i]);
                matched++;
            }
            if (matched > 0) {
                list.add(createBoxFromCluster(minP, maxP, minAmpVal, maxAmpVal, minA, rangeA, primaryClass, label, confidence, col));
            }
        }

        return list;
    }

    private BoundingBoxInfo createBoxFromCluster(double minP, double maxP, double minAmpVal, double maxAmpVal,
                                                 double minA, double rangeA, String primaryClass, String label,
                                                 float confidence, Color col) {
        // Expand by 4% margin around cluster
        double p1 = Math.max(0.0, minP - 0.04 * 360.0);
        double p2 = Math.min(360.0, maxP + 0.04 * 360.0);
        double a1 = Math.max(minA, minAmpVal - 0.04 * rangeA);
        double a2 = Math.min(minA + rangeA, maxAmpVal + 0.04 * rangeA);

        float normX1 = (float) (p1 / 360.0);
        float normX2 = (float) (p2 / 360.0);
        // Image Y coordinate: top is maxA (normY=0), bottom is minA (normY=1)
        float normY_top = (float) (1.0 - (a2 - minA) / rangeA);
        float normY_bot = (float) (1.0 - (a1 - minA) / rangeA);

        float xc = (normX1 + normX2) / 2.0f;
        float yc = (normY_top + normY_bot) / 2.0f;
        float w = Math.max(0.12f, normX2 - normX1);
        float h = Math.max(0.12f, normY_bot - normY_top);

        return new BoundingBoxInfo(primaryClass, label, confidence, xc, yc, w, h, col);
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
                String msg = "Kliknij 'KLASYFIKUJ TERAZ' aby załadować obraz PRPD";
                int strW = g2.getFontMetrics().stringWidth(msg);
                g2.drawString(msg, (w - strW) / 2, h / 2);
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
            if (showBoxesCb.isSelected() && currentResult != null) {
                float minConf = confSlider.getValue() / 100.0f;
                List<BoundingBoxInfo> boxes = getActiveBoundingBoxes(minConf);

                if (!boxes.isEmpty()) {
                    for (BoundingBoxInfo bInfo : boxes) {
                        int bx = offX + (int) Math.round((bInfo.xc - bInfo.w / 2.0f) * drawW);
                        int by = offY + (int) Math.round((bInfo.yc - bInfo.h / 2.0f) * drawH);
                        int bw = (int) Math.round(bInfo.w * drawW);
                        int bh = (int) Math.round(bInfo.h * drawH);

                        // Clamp to draw area
                        bx = Math.max(offX, bx);
                        by = Math.max(offY, by);
                        bw = Math.min(offX + drawW - bx, Math.max(25, bw));
                        bh = Math.min(offY + drawH - by, Math.max(25, bh));

                        Color boxCol = bInfo.color != null ? bInfo.color : new Color(0, 210, 255);

                        // Semi-transparent box fill
                        g2.setColor(new Color(boxCol.getRed(), boxCol.getGreen(), boxCol.getBlue(), 32));
                        g2.fillRect(bx, by, bw, bh);

                        // Box outline
                        g2.setColor(boxCol);
                        g2.setStroke(new BasicStroke(2.2f));
                        g2.drawRect(bx, by, bw, bh);

                        // Tag background
                        String label = bInfo.label;
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                        int tagW = g2.getFontMetrics().stringWidth(label) + 10;
                        int tagH = 18;
                        int tagY = (by - tagH >= offY) ? (by - tagH) : by;

                        g2.setColor(new Color(boxCol.getRed(), boxCol.getGreen(), boxCol.getBlue(), 230));
                        g2.fillRect(bx, tagY, tagW, tagH);

                        g2.setColor(Color.BLACK);
                        g2.drawString(label, bx + 5, tagY + 13);
                    }
                } else if (!"noise".equalsIgnoreCase(currentResult.primaryClass)) {
                    // Informative note when user enables BBoxes but confidence is below slider threshold
                    g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                    g2.setColor(new Color(175, 185, 200));
                    g2.drawString("Ramki BBoxes aktywne (brak skupisk powyżej progu " + confSlider.getValue() + "%)", offX + 10, offY + drawH - 12);
                }
            }

            g2.setColor(new Color(60, 65, 75));
            g2.drawRect(offX, offY, drawW - 1, drawH - 1);
            g2.dispose();
        }
    }
}
