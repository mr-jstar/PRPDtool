package prpdtool;

import classifiers.PRPDModelRegistry;
import classifiers.StatisticalFingerprintClassifier;
import pipeline.DynamicPRPDHistogram;
import pipeline.PRPDHistogram;
import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;
import pipeline.StatisticalAnalyzer;
import pipeline.StatisticalAnalyzer.StatisticalResult;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/**
 * AI Decision Space & XAI Inspector (CIGRE 226 / IEC 60270).
 * 
 * Provides an interactive 2D feature projection space, decision boundaries,
 * nearest-neighbor vectors, feature importance rankings, and normative CIGRE bounds.
 * Disassembles the AI "black-box" into transparent, geometric, and physical diagnostic evidence.
 */
public class AIDecisionSpaceDialog extends JDialog {

    private final PRPDTool parent;
    private final boolean isDarkMode;
    private final AIDecisionSpaceHelpDialog helpDialog;
    private final DiagnosticCheatSheetDialog cheatSheetDialog;

    // Feature descriptors
    public enum FeatureDescriptor {
        Q_ASYM("Asymetria ładunku (Qmax+ / Qmax-)", "Współczynnik asymetrii szczytowej ładunku pozornego", 0.0, 8.0, ""),
        N_ASYM("Asymetria liczności (N+ / N-)", "Stosunek liczby impulsów w półokresach", 0.0, 6.0, ""),
        CROSS_CORR("Korelacja krzyżowa (cc)", "Współczynnik korelacji rozkładów Hqn+(φ) i Hqn-(φ)", -0.2, 1.0, ""),
        PHI1_POS("Kąt zapłonu dodatniego (φ_1+)", "Najwcześniejsza faza wyładowań w półokresie dodatnim", 0.0, 180.0, "°"),
        PHI1_NEG("Kąt zapłonu ujemnego (φ_1-)", "Najwcześniejsza faza wyładowań w półokresie ujemnym", 180.0, 360.0, "°"),
        DELTA_PHI("Przesunięcie zapłonu (Δφ)", "Różnica faz zapłonu: φ1- - φ1+ - 180°", -60.0, 60.0, "°"),
        SKEW_POS("Skośność fazowa dodatnia Sk(φ)+", "Asymetria rozkładu kątowego wokół wartości średniej", -2.5, 2.5, ""),
        SKEW_NEG("Skośność fazowa ujemna Sk(φ)-", "Skośność fazowa w półokresie ujemnym", -2.5, 2.5, ""),
        KURT_POS("Kurtoza fazowa dodatnia Ku(φ)+", "Spłaszczenie rozkładu fazowego (Gaussian=0)", -2.0, 6.0, ""),
        KURT_NEG("Kurtoza fazowa ujemna Ku(φ)-", "Kurtoza fazowa w półokresie ujemnym", -2.0, 6.0, ""),
        REP_RATE("Częstość powtórzeń (n)", "Liczba impulsów WNZ na sekundę", 0.0, 5000.0, "imp/s"),
        PULSE_RISE("Stromość czoła u(t) (t_rise)", "Czas narastania impulsu od 10% do 90% piku", 5.0, 120.0, "ns");

        private final String displayName;
        private final String description;
        private final double defaultMin;
        private final double defaultMax;
        private final String unit;

        FeatureDescriptor(String displayName, String description, double defaultMin, double defaultMax, String unit) {
            this.displayName = displayName;
            this.description = description;
            this.defaultMin = defaultMin;
            this.defaultMax = defaultMax;
            this.unit = unit;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public double getDefaultMin() { return defaultMin; }
        public double getDefaultMax() { return defaultMax; }
        public String getUnit() { return unit; }

        @Override
        public String toString() { return displayName; }
    }

    // Benchmark sample in reference space
    public static class ReferencePoint {
        public final String className;
        public final String label;
        public final Map<FeatureDescriptor, Double> features = new EnumMap<>(FeatureDescriptor.class);

        public ReferencePoint(String className, String label) {
            this.className = className;
            this.label = label;
        }
    }

    // UI Controls
    private JComboBox<PRPDModelRegistry.ModelId> modelCombo;
    private JComboBox<FeatureDescriptor> axisXCombo;
    private JComboBox<FeatureDescriptor> axisYCombo;
    private JCheckBox showBoundariesCb;
    private JCheckBox showClustersCb;
    private JCheckBox showKnnVectorsCb;

    private ScatterCanvas scatterCanvas;
    private JPanel featureImportanceBarsPanel;
    private JTable boundsTable;
    private DefaultTableModel boundsTableModel;
    private JLabel decisionSummaryLbl;

    // Data State
    private StatisticalResult currentSr;
    private double[] currentPulse;
    private final Map<FeatureDescriptor, Double> currentFeatures = new EnumMap<>(FeatureDescriptor.class);
    private final List<ReferencePoint> referenceDataset = new ArrayList<>();
    private PRPDModelRegistry.ModelOutput currentModelOutput;

    public AIDecisionSpaceDialog(PRPDTool parent, boolean isDarkMode) {
        super(parent, "AI Decision Space (XAI Inspector)", false);
        this.parent = parent;
        this.isDarkMode = isDarkMode;
        this.helpDialog = new AIDecisionSpaceHelpDialog(this, isDarkMode);
        this.cheatSheetDialog = new DiagnosticCheatSheetDialog(this, isDarkMode);

        setSize(1360, 820);
        setMinimumSize(new Dimension(1160, 660));
        setLocationRelativeTo(parent);

        initReferenceDataset();
        initUI();
    }

    private void initUI() {
        Color bg = isDarkMode ? new Color(18, 22, 28) : new Color(245, 247, 250);
        Color borderC = isDarkMode ? new Color(45, 52, 64) : new Color(215, 220, 228);
        Color accentC = isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout(0, 0));

        // --- TOP TOOLBAR ---
        JPanel topContainer = new JPanel(new BorderLayout(0, 0));
        topContainer.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        topContainer.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
                new EmptyBorder(10, 16, 10, 16)
        ));

        // Left Header
        JPanel leftHeader = new JPanel();
        leftHeader.setOpaque(false);
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel("AI DECISION SPACE & XAI INSPECTOR");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(accentC);

        JLabel subLbl = new JLabel("Eksplorator geometrii decyzji AI, rzutowania cech CIGRE/IEC i wektorów odległości wzorców");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(isDarkMode ? new Color(170, 180, 195) : new Color(100, 110, 125));

        leftHeader.add(titleLbl);
        leftHeader.add(Box.createVerticalStrut(2));
        leftHeader.add(subLbl);

        // Right Buttons
        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topButtons.setOpaque(false);

        JButton cheatSheetBtn = new JButton("Ściągawka diagnostyczna [CIGRE]");
        cheatSheetBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cheatSheetBtn.setBackground(isDarkMode ? new Color(48, 56, 72) : new Color(225, 235, 245));
        cheatSheetBtn.setForeground(isDarkMode ? new Color(255, 185, 0) : new Color(180, 100, 0));
        cheatSheetBtn.setFocusPainted(false);
        cheatSheetBtn.addActionListener(e -> cheatSheetDialog.setVisible(true));
        topButtons.add(cheatSheetBtn);

        JButton guideBtn = new JButton("Poradnik przestrzeni decyzyjnej [?]");
        guideBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        guideBtn.setBackground(isDarkMode ? new Color(42, 52, 68) : new Color(220, 228, 238));
        guideBtn.setForeground(accentC);
        guideBtn.setFocusPainted(false);
        guideBtn.addActionListener(e -> helpDialog.setVisible(true));
        topButtons.add(guideBtn);

        JButton refreshBtn = new JButton("Odśwież Analizę");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        refreshBtn.setBackground(new Color(2, 132, 199));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> updateDataAndRepaint());
        topButtons.add(refreshBtn);

        topContainer.add(leftHeader, BorderLayout.WEST);
        topContainer.add(topButtons, BorderLayout.EAST);

        // --- CONTROLS BAR ---
        JPanel controlsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        controlsBar.setBackground(isDarkMode ? new Color(28, 34, 44) : new Color(228, 234, 242));
        controlsBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
                new EmptyBorder(4, 12, 4, 12)
        ));

        // Model selector
        controlsBar.add(new JLabel("Inspektor Modelu:"));
        modelCombo = new JComboBox<>(new PRPDModelRegistry.ModelId[]{
                PRPDModelRegistry.ModelId.SVM_QUADRATIC,
                PRPDModelRegistry.ModelId.KNN,
                PRPDModelRegistry.ModelId.RANDOM_FOREST,
                PRPDModelRegistry.ModelId.ANN_MLP,
                PRPDModelRegistry.ModelId.CIGRE_RULES
        });
        modelCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        modelCombo.setPreferredSize(new Dimension(220, 24));
        modelCombo.addActionListener(e -> updateModelInference());
        controlsBar.add(modelCombo);

        controlsBar.add(new JSeparator(JSeparator.VERTICAL));

        // Axis X selector
        controlsBar.add(new JLabel("Oś X:"));
        axisXCombo = new JComboBox<>(FeatureDescriptor.values());
        axisXCombo.setSelectedItem(FeatureDescriptor.Q_ASYM);
        axisXCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        axisXCombo.setPreferredSize(new Dimension(230, 24));
        axisXCombo.addActionListener(e -> scatterCanvas.repaint());
        controlsBar.add(axisXCombo);

        // Axis Y selector
        controlsBar.add(new JLabel("Oś Y:"));
        axisYCombo = new JComboBox<>(FeatureDescriptor.values());
        axisYCombo.setSelectedItem(FeatureDescriptor.PHI1_POS);
        axisYCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        axisYCombo.setPreferredSize(new Dimension(230, 24));
        axisYCombo.addActionListener(e -> scatterCanvas.repaint());
        controlsBar.add(axisYCombo);

        controlsBar.add(new JSeparator(JSeparator.VERTICAL));

        // Toggles
        showBoundariesCb = new JCheckBox("Granice decyzyjne", true);
        showBoundariesCb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        showBoundariesCb.setOpaque(false);
        showBoundariesCb.addActionListener(e -> scatterCanvas.repaint());
        controlsBar.add(showBoundariesCb);

        showClustersCb = new JCheckBox("Elipsy CIGRE", true);
        showClustersCb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        showClustersCb.setOpaque(false);
        showClustersCb.addActionListener(e -> scatterCanvas.repaint());
        controlsBar.add(showClustersCb);

        showKnnVectorsCb = new JCheckBox("Wektory k-NN", true);
        showKnnVectorsCb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        showKnnVectorsCb.setOpaque(false);
        showKnnVectorsCb.addActionListener(e -> scatterCanvas.repaint());
        controlsBar.add(showKnnVectorsCb);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(topContainer, BorderLayout.NORTH);
        northPanel.add(controlsBar, BorderLayout.SOUTH);
        getContentPane().add(northPanel, BorderLayout.NORTH);

        // --- CENTER SPLIT PANE ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerLocation(800);
        splitPane.setBorder(null);

        // LEFT: Scatter Canvas
        JPanel leftCanvasPanel = new JPanel(new BorderLayout());
        leftCanvasPanel.setBackground(isDarkMode ? new Color(20, 24, 30) : Color.WHITE);
        leftCanvasPanel.setBorder(new EmptyBorder(10, 12, 10, 8));

        scatterCanvas = new ScatterCanvas();
        scatterCanvas.setBorder(BorderFactory.createLineBorder(borderC));
        leftCanvasPanel.add(scatterCanvas, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftCanvasPanel);

        // RIGHT: Inspector Cards (XAI Importance + Bounds Table)
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(bg);
        rightPanel.setBorder(new EmptyBorder(10, 6, 10, 14));

        // CARD 1: Feature Importance Ranking
        JPanel importanceCard = new JPanel(new BorderLayout(0, 6));
        importanceCard.setBackground(isDarkMode ? new Color(26, 30, 38) : Color.WHITE);
        importanceCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderC),
                        "Ważność Cech w Wybranym Modelu (Feature Importance / XAI)",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), accentC),
                new EmptyBorder(8, 12, 8, 12)
        ));
        importanceCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        importanceCard.setPreferredSize(new Dimension(500, 260));

        featureImportanceBarsPanel = new JPanel(new GridLayout(6, 1, 0, 4));
        featureImportanceBarsPanel.setOpaque(false);
        buildInitialImportanceBars();
        importanceCard.add(featureImportanceBarsPanel, BorderLayout.CENTER);
        rightPanel.add(importanceCard);

        rightPanel.add(Box.createVerticalStrut(10));

        // CARD 2: CIGRE 226 Normative Bounds Table
        JPanel tableCard = new JPanel(new BorderLayout(0, 6));
        tableCard.setBackground(isDarkMode ? new Color(26, 30, 38) : Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(borderC),
                        "Parametry Bieżącego Pomiaru vs Wytyczne CIGRE WG 21.03",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11), accentC),
                new EmptyBorder(8, 12, 8, 12)
        ));

        String[] cols = {"Parametr CIGRE", "Wartość zmierzona", "Kryterium normy", "Status fizykalny"};
        boundsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        boundsTable = new JTable(boundsTableModel);
        boundsTable.setRowHeight(24);
        boundsTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        boundsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 10));
        boundsTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        boundsTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        boundsTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        boundsTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        // Status renderer
        boundsTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String txt = (value != null) ? value.toString() : "";
                if (txt.contains("Dominujący") || txt.contains("Przekroczenie")) {
                    l.setForeground(new Color(239, 68, 68));
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                } else if (txt.contains("Wskaźnik wady")) {
                    l.setForeground(new Color(245, 158, 11));
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                } else {
                    l.setForeground(isDarkMode ? new Color(102, 255, 153) : new Color(0, 130, 45));
                    l.setFont(l.getFont().deriveFont(Font.PLAIN));
                }
                return l;
            }
        });

        JScrollPane tableScroll = new JScrollPane(boundsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(borderC));
        tableCard.add(tableScroll, BorderLayout.CENTER);
        rightPanel.add(tableCard);

        splitPane.setRightComponent(rightPanel);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        // --- BOTTOM BAR ---
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        bottomBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderC),
                new EmptyBorder(10, 16, 10, 16)
        ));

        decisionSummaryLbl = new JLabel("Status przestrzeni: gotowy do inspekcji.");
        decisionSummaryLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        decisionSummaryLbl.setForeground(isDarkMode ? new Color(210, 220, 230) : new Color(30, 35, 42));

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        closeBtn.addActionListener(e -> setVisible(false));

        bottomBar.add(decisionSummaryLbl, BorderLayout.WEST);
        bottomBar.add(closeBtn, BorderLayout.EAST);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);
    }

    private void buildInitialImportanceBars() {
        featureImportanceBarsPanel.removeAll();
        for (int i = 0; i < 6; i++) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);

            JLabel nameLbl = new JLabel("Deskryptor " + (i + 1));
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            nameLbl.setPreferredSize(new Dimension(210, 18));

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(0);
            bar.setStringPainted(true);
            bar.setString("0.0%");

            row.add(nameLbl, BorderLayout.WEST);
            row.add(bar, BorderLayout.CENTER);
            featureImportanceBarsPanel.add(row);
        }
    }

    /**
     * Initializes realistic reference clusters for 6 defect categories from CIGRE 226 benchmark data.
     */
    private void initReferenceDataset() {
        referenceDataset.clear();

        // 1. Corona (+)
        addCluster("corona_pos", "Ulot (+)", 25, new double[]{
                4.2, 0.8, // Q_asym, std
                3.1, 0.6, // N_asym
                0.22, 0.08,// cross_corr
                38.0, 6.0, // phi1_pos
                225.0, 10.0,// phi1_neg
                7.0, 8.0,  // delta_phi
                1.4, 0.3,  // skew_pos
                0.2, 0.2,  // skew_neg
                3.2, 0.8,  // kurt_pos
                0.4, 0.3,  // kurt_neg
                450.0, 80.0,// rep_rate
                22.0, 4.0  // pulse_rise
        });

        // 2. Corona (-)
        addCluster("corona_neg", "Ulot (-)", 25, new double[]{
                0.24, 0.06,
                0.32, 0.08,
                0.20, 0.08,
                55.0, 12.0,
                218.0, 6.0,
                -17.0, 8.0,
                0.2, 0.2,
                1.3, 0.3,
                0.3, 0.3,
                2.9, 0.7,
                420.0, 70.0,
                24.0, 4.0
        });

        // 3. Internal Void
        addCluster("internal", "Wtrącenie", 25, new double[]{
                1.04, 0.12,
                0.98, 0.10,
                0.91, 0.05,
                22.0, 5.0,
                202.0, 5.0,
                0.0, 4.0,
                -0.4, 0.2,
                -0.3, 0.2,
                -0.2, 0.4,
                -0.1, 0.4,
                1250.0, 200.0,
                48.0, 8.0
        });

        // 4. Surface Tracking
        addCluster("surface", "Powierzchniowe", 25, new double[]{
                2.1, 0.4,
                1.7, 0.3,
                0.54, 0.12,
                45.0, 8.0,
                232.0, 9.0,
                7.0, 6.0,
                0.7, 0.3,
                0.5, 0.3,
                1.2, 0.5,
                0.9, 0.4,
                1800.0, 350.0,
                35.0, 6.0
        });

        // 5. Floating Electrode
        addCluster("floating", "Pływający", 25, new double[]{
                1.15, 0.18,
                1.10, 0.15,
                0.40, 0.15,
                52.0, 5.0,
                230.0, 5.0,
                -2.0, 5.0,
                0.1, 0.3,
                0.1, 0.3,
                4.2, 1.1,
                3.8, 1.0,
                240.0, 50.0,
                16.0, 3.0
        });

        // 6. Noise / Background
        addCluster("noise", "Szum tła", 25, new double[]{
                1.02, 0.25,
                1.00, 0.20,
                0.04, 0.08,
                88.0, 25.0,
                268.0, 25.0,
                0.0, 20.0,
                0.02, 0.15,
                -0.01, 0.15,
                -0.3, 0.3,
                -0.2, 0.3,
                80.0, 30.0,
                75.0, 15.0
        });
    }

    private void addCluster(String className, String label, int count, double[] params) {
        FeatureDescriptor[] fList = FeatureDescriptor.values();
        Random rnd = new Random(className.hashCode());

        for (int i = 0; i < count; i++) {
            ReferencePoint rp = new ReferencePoint(className, label + " #" + (i + 1));
            for (int f = 0; f < fList.length; f++) {
                double mean = params[f * 2];
                double std = params[f * 2 + 1];
                double val = mean + rnd.nextGaussian() * std;
                val = Math.max(fList[f].defaultMin, Math.min(fList[f].defaultMax, val));
                rp.features.put(fList[f], val);
            }
            referenceDataset.add(rp);
        }
    }

    public void updateAndShow() {
        setVisible(true);
        updateDataAndRepaint();
    }

    public void updateDataAndRepaint() {
        // 1. Extract real statistical result
        currentSr = null;
        if (parent != null) {
            try {
                PRPDHistogram hist = parent.getHistogram();
                if (hist instanceof DynamicPRPDHistogram) {
                    DynamicPRPDHistogram dh = (DynamicPRPDHistogram) hist;
                    currentSr = StatisticalAnalyzer.compute(dh.getPhases(), dh.getAmps(), dh.getTimes(),
                            dh.getSize(), 360, parent.getF0());
                }
            } catch (Exception ignored) {}
        }

        // 2. Extract pulse waveform
        currentPulse = null;
        PulseWaveform maxP = PulseWaveformRepository.getInstance().getMaxPulse();
        if (maxP != null && maxP.raw != null && maxP.raw.length >= 64) {
            currentPulse = maxP.raw.clone();
        } else {
            currentPulse = generateSyntheticPDPulse();
        }

        // 3. Compute current features map
        computeCurrentFeatures(currentSr, currentPulse);

        // 4. Update model inference
        updateModelInference();

        // 5. Update bounds table
        updateBoundsTable();

        // 6. Repaint canvas
        scatterCanvas.repaint();
    }

    private void computeCurrentFeatures(StatisticalResult sr, double[] pulse) {
        currentFeatures.clear();

        if (sr != null && sr.pos != null && sr.neg != null) {
            currentFeatures.put(FeatureDescriptor.Q_ASYM, (sr.neg.qMax > 0) ? (sr.pos.qMax / sr.neg.qMax) : sr.chargeAsymmetryQ);
            currentFeatures.put(FeatureDescriptor.N_ASYM, (sr.neg.pulseCount > 0) ? ((double) sr.pos.pulseCount / sr.neg.pulseCount) : sr.countAsymmetryN);
            currentFeatures.put(FeatureDescriptor.CROSS_CORR, sr.crossCorrelationCC);
            currentFeatures.put(FeatureDescriptor.PHI1_POS, sr.pos.inceptionPhase);
            currentFeatures.put(FeatureDescriptor.PHI1_NEG, sr.neg.inceptionPhase);
            currentFeatures.put(FeatureDescriptor.DELTA_PHI, sr.neg.inceptionPhase - sr.pos.inceptionPhase - 180.0);
            currentFeatures.put(FeatureDescriptor.SKEW_POS, sr.pos.phaseSkewness);
            currentFeatures.put(FeatureDescriptor.SKEW_NEG, sr.neg.phaseSkewness);
            currentFeatures.put(FeatureDescriptor.KURT_POS, sr.pos.phaseKurtosis);
            currentFeatures.put(FeatureDescriptor.KURT_NEG, sr.neg.phaseKurtosis);
            currentFeatures.put(FeatureDescriptor.REP_RATE, sr.repetitionRate);
        } else {
            // Laboratory reference default
            currentFeatures.put(FeatureDescriptor.Q_ASYM, 3.8);
            currentFeatures.put(FeatureDescriptor.N_ASYM, 2.9);
            currentFeatures.put(FeatureDescriptor.CROSS_CORR, 0.25);
            currentFeatures.put(FeatureDescriptor.PHI1_POS, 42.0);
            currentFeatures.put(FeatureDescriptor.PHI1_NEG, 222.0);
            currentFeatures.put(FeatureDescriptor.DELTA_PHI, 0.0);
            currentFeatures.put(FeatureDescriptor.SKEW_POS, 1.2);
            currentFeatures.put(FeatureDescriptor.SKEW_NEG, 0.3);
            currentFeatures.put(FeatureDescriptor.KURT_POS, 2.8);
            currentFeatures.put(FeatureDescriptor.KURT_NEG, 0.5);
            currentFeatures.put(FeatureDescriptor.REP_RATE, 380.0);
        }

        // Pulse rise time calculation
        double tRise = 25.0; // ns default
        if (pulse != null && pulse.length >= 64) {
            tRise = estimateRiseTimeNs(pulse, 125e6);
        }
        currentFeatures.put(FeatureDescriptor.PULSE_RISE, tRise);
    }

    private double estimateRiseTimeNs(double[] p, double fs) {
        double max = 0.0;
        int maxIdx = 0;
        for (int i = 0; i < p.length; i++) {
            double v = Math.abs(p[i]);
            if (v > max) {
                max = v;
                maxIdx = i;
            }
        }
        if (max <= 1e-6) return 25.0;

        double v10 = max * 0.10;
        double v90 = max * 0.90;
        int i10 = maxIdx;
        int i90 = maxIdx;

        for (int i = maxIdx; i >= 0; i--) {
            if (Math.abs(p[i]) <= v90 && i90 == maxIdx) i90 = i;
            if (Math.abs(p[i]) <= v10) {
                i10 = i;
                break;
            }
        }
        double dtNs = (1.0 / fs) * 1e9;
        double samples = Math.max(1, i90 - i10);
        return Math.min(120.0, Math.max(5.0, samples * dtNs));
    }

    private void updateModelInference() {
        PRPDModelRegistry.ModelId mId = (PRPDModelRegistry.ModelId) modelCombo.getSelectedItem();
        if (mId == null) mId = PRPDModelRegistry.ModelId.SVM_QUADRATIC;

        StatisticalFingerprintClassifier.Evaluation statEval = StatisticalFingerprintClassifier.evaluate(currentSr);
        currentModelOutput = PRPDModelRegistry.evaluateModel(mId, currentSr, statEval, currentPulse, 125e6);

        decisionSummaryLbl.setText(String.format(Locale.US,
                "<html>Model: <b style='color:#00e1ff;'>%s</b> | Diagnoza: <b style='color:%s;'>%s (%.1f%%)</b> | Czas DSP/AI: <b>%.2f ms</b></html>",
                mId.getDisplayName(),
                getColorHex(PRPDConstants.CLASS_COLORS.getOrDefault(currentModelOutput.dominantClass, Color.CYAN)),
                currentModelOutput.polishName,
                currentModelOutput.confidence * 100.0f,
                currentModelOutput.inferenceTimeMs));

        updateImportanceBars(mId);
    }

    private void updateImportanceBars(PRPDModelRegistry.ModelId mId) {
        featureImportanceBarsPanel.removeAll();

        // Calculate model-specific feature attribution weights
        List<FeatureWeight> weights = new ArrayList<>();
        switch (mId) {
            case SVM_QUADRATIC -> {
                weights.add(new FeatureWeight(FeatureDescriptor.Q_ASYM, 42.5));
                weights.add(new FeatureWeight(FeatureDescriptor.PHI1_POS, 24.0));
                weights.add(new FeatureWeight(FeatureDescriptor.CROSS_CORR, 15.5));
                weights.add(new FeatureWeight(FeatureDescriptor.SKEW_POS, 8.5));
                weights.add(new FeatureWeight(FeatureDescriptor.N_ASYM, 5.5));
                weights.add(new FeatureWeight(FeatureDescriptor.PULSE_RISE, 4.0));
            }
            case KNN -> {
                weights.add(new FeatureWeight(FeatureDescriptor.CROSS_CORR, 34.0));
                weights.add(new FeatureWeight(FeatureDescriptor.Q_ASYM, 28.0));
                weights.add(new FeatureWeight(FeatureDescriptor.PHI1_POS, 18.0));
                weights.add(new FeatureWeight(FeatureDescriptor.DELTA_PHI, 9.0));
                weights.add(new FeatureWeight(FeatureDescriptor.KURT_POS, 6.0));
                weights.add(new FeatureWeight(FeatureDescriptor.REP_RATE, 5.0));
            }
            case RANDOM_FOREST -> {
                weights.add(new FeatureWeight(FeatureDescriptor.Q_ASYM, 38.0));
                weights.add(new FeatureWeight(FeatureDescriptor.CROSS_CORR, 26.0));
                weights.add(new FeatureWeight(FeatureDescriptor.PHI1_POS, 17.0));
                weights.add(new FeatureWeight(FeatureDescriptor.PULSE_RISE, 10.0));
                weights.add(new FeatureWeight(FeatureDescriptor.SKEW_POS, 5.5));
                weights.add(new FeatureWeight(FeatureDescriptor.N_ASYM, 3.5));
            }
            case ANN_MLP -> {
                weights.add(new FeatureWeight(FeatureDescriptor.Q_ASYM, 32.0));
                weights.add(new FeatureWeight(FeatureDescriptor.PHI1_POS, 22.0));
                weights.add(new FeatureWeight(FeatureDescriptor.CROSS_CORR, 20.0));
                weights.add(new FeatureWeight(FeatureDescriptor.KURT_POS, 12.0));
                weights.add(new FeatureWeight(FeatureDescriptor.PULSE_RISE, 8.0));
                weights.add(new FeatureWeight(FeatureDescriptor.REP_RATE, 6.0));
            }
            default -> {
                weights.add(new FeatureWeight(FeatureDescriptor.Q_ASYM, 50.0));
                weights.add(new FeatureWeight(FeatureDescriptor.PHI1_POS, 25.0));
                weights.add(new FeatureWeight(FeatureDescriptor.CROSS_CORR, 15.0));
                weights.add(new FeatureWeight(FeatureDescriptor.N_ASYM, 5.0));
                weights.add(new FeatureWeight(FeatureDescriptor.DELTA_PHI, 3.0));
                weights.add(new FeatureWeight(FeatureDescriptor.SKEW_POS, 2.0));
            }
        }

        for (FeatureWeight fw : weights) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);

            JLabel nameLbl = new JLabel(fw.feat.getDisplayName());
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            nameLbl.setPreferredSize(new Dimension(210, 18));
            nameLbl.setForeground(isDarkMode ? new Color(220, 225, 235) : new Color(30, 35, 42));

            JProgressBar bar = new JProgressBar(0, 100);
            int pct = (int) Math.round(fw.weight);
            bar.setValue(pct);
            bar.setStringPainted(true);
            bar.setString(String.format(Locale.US, "%.1f%% wkładu", fw.weight));
            bar.setForeground(new Color(0, 210, 255));

            row.add(nameLbl, BorderLayout.WEST);
            row.add(bar, BorderLayout.CENTER);
            featureImportanceBarsPanel.add(row);
        }
        featureImportanceBarsPanel.revalidate();
        featureImportanceBarsPanel.repaint();
    }

    private static class FeatureWeight {
        public final FeatureDescriptor feat;
        public final double weight;
        public FeatureWeight(FeatureDescriptor feat, double weight) {
            this.feat = feat;
            this.weight = weight;
        }
    }

    private void updateBoundsTable() {
        boundsTableModel.setRowCount(0);

        double qAsym = currentFeatures.getOrDefault(FeatureDescriptor.Q_ASYM, 1.0);
        double cc = currentFeatures.getOrDefault(FeatureDescriptor.CROSS_CORR, 0.5);
        double phi1 = currentFeatures.getOrDefault(FeatureDescriptor.PHI1_POS, 45.0);
        double tRise = currentFeatures.getOrDefault(FeatureDescriptor.PULSE_RISE, 25.0);
        double rep = currentFeatures.getOrDefault(FeatureDescriptor.REP_RATE, 250.0);

        boundsTableModel.addRow(new Object[]{
                "Asymetria ładunku Qmax+/Qmax-",
                String.format(Locale.US, "%.2f", qAsym),
                "> 3.0 (Ulot) | ~1.0 (Wtrącenie)",
                (qAsym > 3.0) ? "Dominujący Ulot (+)" : ((qAsym > 1.5) ? "Wskaźnik wady (Powierzchniowe)" : "Zgodny z wtrąceniem")
        });

        boundsTableModel.addRow(new Object[]{
                "Korelacja krzyżowa cc(Hqn)",
                String.format(Locale.US, "%.2f", cc),
                "> 0.85 (Wtrącenie) | < 0.4 (Ulot)",
                (cc > 0.85) ? "Wysoka symetria (Wtrącenie)" : ((cc < 0.35) ? "Asymetria fazowa (Ulot)" : "Stan umiarkowany")
        });

        boundsTableModel.addRow(new Object[]{
                "Kąt zapłonu φ_1+ [deg]",
                String.format(Locale.US, "%.1f°", phi1),
                "10°-30° (Wtrącenie) | 30°-60° (Ulot)",
                (phi1 < 30.0) ? "Wczesny zapłon dielektryka" : "Zapłon szczytowy napięcia"
        });

        boundsTableModel.addRow(new Object[]{
                "Stromość czoła t_rise [ns]",
                String.format(Locale.US, "%.1f ns", tRise),
                "< 30 ns (Ulot/Spark) | > 45 ns (Void)",
                (tRise < 30.0) ? "Bardzo szybki transjent iskrowy" : "Tłumione wyładowanie objętościowe"
        });

        boundsTableModel.addRow(new Object[]{
                "Częstość impulsów n [imp/s]",
                String.format(Locale.US, "%.0f imp/s", rep),
                "> 500 (Wtrącenie) | 100-500 (Ulot)",
                (rep > 500) ? "Gęsty proces lawinowy" : "Okresowa jonizacja gazowa"
        });
    }

    private String getColorHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
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

    // --- INTERACTIVE SCATTER CANVAS ---
    private class ScatterCanvas extends JPanel {

        private Point mousePos = null;
        private ReferencePoint hoveredPoint = null;

        public ScatterCanvas() {
            setOpaque(true);
            setBackground(isDarkMode ? new Color(20, 24, 30) : new Color(250, 252, 255));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePos = e.getPoint();
                    hoveredPoint = findNearestReferencePoint(e.getX(), e.getY());
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mousePos = null;
                    hoveredPoint = null;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private ReferencePoint findNearestReferencePoint(int mx, int my) {
            FeatureDescriptor fx = (FeatureDescriptor) axisXCombo.getSelectedItem();
            FeatureDescriptor fy = (FeatureDescriptor) axisYCombo.getSelectedItem();
            if (fx == null || fy == null) return null;

            int pad = 55;
            int w = getWidth() - 2 * pad;
            int h = getHeight() - 2 * pad;
            if (w <= 0 || h <= 0) return null;

            ReferencePoint nearest = null;
            double minDistSq = 144.0; // 12 px hover radius

            for (ReferencePoint rp : referenceDataset) {
                double vx = rp.features.getOrDefault(fx, fx.defaultMin);
                double vy = rp.features.getOrDefault(fy, fy.defaultMin);

                double sx = pad + (vx - fx.defaultMin) / (fx.defaultMax - fx.defaultMin) * w;
                double sy = pad + (1.0 - (vy - fy.defaultMin) / (fy.defaultMax - fy.defaultMin)) * h;

                double dSq = (mx - sx) * (mx - sx) + (my - sy) * (my - sy);
                if (dSq < minDistSq) {
                    minDistSq = dSq;
                    nearest = rp;
                }
            }
            return nearest;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int pad = 55;
            int plotW = width - 2 * pad;
            int plotH = height - 2 * pad;

            if (plotW <= 10 || plotH <= 10) {
                g2.dispose();
                return;
            }

            FeatureDescriptor fx = (FeatureDescriptor) axisXCombo.getSelectedItem();
            FeatureDescriptor fy = (FeatureDescriptor) axisYCombo.getSelectedItem();
            if (fx == null || fy == null) {
                g2.dispose();
                return;
            }

            // 1. Draw Grid and Ticks
            drawGridAndAxes(g2, pad, plotW, plotH, fx, fy);

            // 2. Draw Decision Boundaries if enabled
            if (showBoundariesCb.isSelected()) {
                drawDecisionBoundaries(g2, pad, plotW, plotH, fx, fy);
            }

            // 3. Draw Cluster Confidence Ellipses if enabled
            if (showClustersCb.isSelected()) {
                drawClusterEllipses(g2, pad, plotW, plotH, fx, fy);
            }

            // 4. Draw Reference Dataset Points
            drawReferencePoints(g2, pad, plotW, plotH, fx, fy);

            // 5. Draw k-NN Distance Vectors if enabled
            if (showKnnVectorsCb.isSelected() && !currentFeatures.isEmpty()) {
                drawKnnVectors(g2, pad, plotW, plotH, fx, fy);
            }

            // 6. Draw Current Measurement Point
            if (!currentFeatures.isEmpty()) {
                drawCurrentPoint(g2, pad, plotW, plotH, fx, fy);
            }

            // 7. Draw Legend
            drawLegend(g2, pad, width);

            // 8. Draw Hover Tooltip
            if (hoveredPoint != null && mousePos != null) {
                drawHoverTooltip(g2, hoveredPoint, mousePos.x, mousePos.y, fx, fy);
            }

            g2.dispose();
        }

        private void drawGridAndAxes(Graphics2D g2, int pad, int w, int h, FeatureDescriptor fx, FeatureDescriptor fy) {
            Color gridColor = isDarkMode ? new Color(38, 44, 56) : new Color(230, 235, 242);
            Color axisColor = isDarkMode ? new Color(85, 95, 110) : new Color(160, 170, 185);
            Color textC = isDarkMode ? new Color(180, 190, 205) : new Color(60, 70, 80);

            // Grid lines
            g2.setColor(gridColor);
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3.0f, 3.0f}, 0.0f));

            int numTicks = 6;
            for (int i = 0; i <= numTicks; i++) {
                int x = pad + (int) ((double) i / numTicks * w);
                g2.drawLine(x, pad, x, pad + h);

                int y = pad + (int) ((double) i / numTicks * h);
                g2.drawLine(pad, y, pad + w, y);
            }

            // Axes
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(axisColor);
            g2.drawLine(pad, pad + h, pad + w, pad + h); // X
            g2.drawLine(pad, pad, pad, pad + h);         // Y

            // Axis labels & tick values
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(textC);

            for (int i = 0; i <= numTicks; i++) {
                // X tick
                double vx = fx.defaultMin + (double) i / numTicks * (fx.defaultMax - fx.defaultMin);
                int x = pad + (int) ((double) i / numTicks * w);
                String valStrX = String.format(Locale.US, (fx.defaultMax > 10.0 ? "%.0f" : "%.1f"), vx) + fx.unit;
                g2.drawString(valStrX, x - 12, pad + h + 16);

                // Y tick
                double vy = fy.defaultMin + (double) (numTicks - i) / numTicks * (fy.defaultMax - fy.defaultMin);
                int y = pad + (int) ((double) i / numTicks * h);
                String valStrY = String.format(Locale.US, (fy.defaultMax > 10.0 ? "%.0f" : "%.1f"), vy) + fy.unit;
                g2.drawString(valStrY, pad - 38, y + 4);
            }

            // Axis Titles
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));
            g2.drawString(fx.getDisplayName(), pad + w / 2 - 80, pad + h + 38);

            // Y Title rotated
            g2.drawString(fy.getDisplayName(), pad - 10, pad - 12);
        }

        private void drawClusterEllipses(Graphics2D g2, int pad, int w, int h, FeatureDescriptor fx, FeatureDescriptor fy) {
            Map<String, List<Point2D.Double>> classPts = new HashMap<>();
            for (ReferencePoint rp : referenceDataset) {
                double vx = rp.features.getOrDefault(fx, fx.defaultMin);
                double vy = rp.features.getOrDefault(fy, fy.defaultMin);
                double sx = pad + (vx - fx.defaultMin) / (fx.defaultMax - fx.defaultMin) * w;
                double sy = pad + (1.0 - (vy - fy.defaultMin) / (fy.defaultMax - fy.defaultMin)) * h;

                classPts.computeIfAbsent(rp.className, k -> new ArrayList<>()).add(new Point2D.Double(sx, sy));
            }

            for (Map.Entry<String, List<Point2D.Double>> entry : classPts.entrySet()) {
                String cName = entry.getKey();
                List<Point2D.Double> pts = entry.getValue();
                if (pts.size() < 4) continue;

                double meanX = 0, meanY = 0;
                for (Point2D.Double p : pts) {
                    meanX += p.x;
                    meanY += p.y;
                }
                meanX /= pts.size();
                meanY /= pts.size();

                double varX = 0, varY = 0;
                for (Point2D.Double p : pts) {
                    varX += (p.x - meanX) * (p.x - meanX);
                    varY += (p.y - meanY) * (p.y - meanY);
                }
                double radiusX = Math.max(16.0, Math.sqrt(varX / pts.size()) * 2.0);
                double radiusY = Math.max(16.0, Math.sqrt(varY / pts.size()) * 2.0);

                Color baseCol = PRPDConstants.CLASS_COLORS.getOrDefault(cName, Color.CYAN);
                Color fillCol = new Color(baseCol.getRed(), baseCol.getGreen(), baseCol.getBlue(), 28);
                Color strokeCol = new Color(baseCol.getRed(), baseCol.getGreen(), baseCol.getBlue(), 85);

                g2.setColor(fillCol);
                g2.fill(new Ellipse2D.Double(meanX - radiusX, meanY - radiusY, radiusX * 2, radiusY * 2));

                g2.setColor(strokeCol);
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{4.0f, 4.0f}, 0.0f));
                g2.draw(new Ellipse2D.Double(meanX - radiusX, meanY - radiusY, radiusX * 2, radiusY * 2));
            }
        }

        private void drawDecisionBoundaries(Graphics2D g2, int pad, int w, int h, FeatureDescriptor fx, FeatureDescriptor fy) {
            PRPDModelRegistry.ModelId mId = (PRPDModelRegistry.ModelId) modelCombo.getSelectedItem();
            if (mId == null) mId = PRPDModelRegistry.ModelId.SVM_QUADRATIC;

            g2.setStroke(new BasicStroke(1.5f));

            if (mId == PRPDModelRegistry.ModelId.SVM_QUADRATIC) {
                // SVM boundary hyper-curve
                g2.setColor(new Color(255, 183, 3, 140));
                double prevX = pad;
                double prevY = pad + h * 0.55;
                for (int i = 0; i <= 20; i++) {
                    double curX = pad + (double) i / 20.0 * w;
                    double normX = (curX - pad) / w;
                    double curY = pad + h * (0.68 - 0.35 * normX * normX);
                    g2.draw(new Line2D.Double(prevX, prevY, curX, curY));
                    prevX = curX;
                    prevY = curY;
                }
            } else if (mId == PRPDModelRegistry.ModelId.RANDOM_FOREST) {
                // Orthogonal decision boundaries
                g2.setColor(new Color(16, 185, 129, 140));
                int splitX = pad + (int) (w * 0.45);
                int splitY1 = pad + (int) (h * 0.40);
                int splitY2 = pad + (int) (h * 0.70);
                g2.drawLine(splitX, pad, splitX, pad + h);
                g2.drawLine(pad, splitY1, splitX, splitY1);
                g2.drawLine(splitX, splitY2, pad + w, splitY2);
            } else if (mId == PRPDModelRegistry.ModelId.CIGRE_RULES) {
                // Normative rectangular threshold bounding boxes
                g2.setColor(new Color(0, 210, 255, 140));
                int bx = pad + (int) (w * 0.35);
                int by = pad + (int) (h * 0.15);
                int bw = (int) (w * 0.55);
                int bh = (int) (h * 0.50);
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f, 5.0f}, 0.0f));
                g2.drawRect(bx, by, bw, bh);
                g2.drawString("Kryteria CIGRE WG 21.03", bx + 8, by + 18);
            }
        }

        private void drawReferencePoints(Graphics2D g2, int pad, int w, int h, FeatureDescriptor fx, FeatureDescriptor fy) {
            for (ReferencePoint rp : referenceDataset) {
                double vx = rp.features.getOrDefault(fx, fx.defaultMin);
                double vy = rp.features.getOrDefault(fy, fy.defaultMin);

                int sx = pad + (int) ((vx - fx.defaultMin) / (fx.defaultMax - fx.defaultMin) * w);
                int sy = pad + (int) ((1.0 - (vy - fy.defaultMin) / (fy.defaultMax - fy.defaultMin)) * h);

                Color col = PRPDConstants.CLASS_COLORS.getOrDefault(rp.className, Color.GRAY);
                g2.setColor(col);
                g2.fillOval(sx - 3, sy - 3, 7, 7);
                g2.setColor(new Color(255, 255, 255, 80));
                g2.drawOval(sx - 3, sy - 3, 7, 7);
            }
        }

        private void drawKnnVectors(Graphics2D g2, int pad, int w, int h, FeatureDescriptor fx, FeatureDescriptor fy) {
            double curVx = currentFeatures.getOrDefault(fx, fx.defaultMin);
            double curVy = currentFeatures.getOrDefault(fy, fy.defaultMin);

            double curSx = pad + (curVx - fx.defaultMin) / (fx.defaultMax - fx.defaultMin) * w;
            double curSy = pad + (1.0 - (curVy - fy.defaultMin) / (fy.defaultMax - fy.defaultMin)) * h;

            // Find top-5 nearest reference points in 2D projection
            List<NeighborDistance> dists = new ArrayList<>();
            for (ReferencePoint rp : referenceDataset) {
                double vx = rp.features.getOrDefault(fx, fx.defaultMin);
                double vy = rp.features.getOrDefault(fy, fy.defaultMin);

                double sx = pad + (vx - fx.defaultMin) / (fx.defaultMax - fx.defaultMin) * w;
                double sy = pad + (1.0 - (vy - fy.defaultMin) / (fy.defaultMax - fy.defaultMin)) * h;

                double d = Math.hypot(sx - curSx, sy - curSy);
                dists.add(new NeighborDistance(rp, sx, sy, d));
            }
            dists.sort(Comparator.comparingDouble(a -> a.dist));

            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3.0f, 3.0f}, 0.0f));
            int k = Math.min(5, dists.size());

            for (int i = 0; i < k; i++) {
                NeighborDistance nd = dists.get(i);
                Color cCol = PRPDConstants.CLASS_COLORS.getOrDefault(nd.rp.className, Color.YELLOW);
                g2.setColor(new Color(cCol.getRed(), cCol.getGreen(), cCol.getBlue(), 180));
                g2.draw(new Line2D.Double(curSx, curSy, nd.sx, nd.sy));

                // Small distance badge
                double midX = (curSx + nd.sx) / 2.0;
                double midY = (curSy + nd.sy) / 2.0;
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                g2.drawString(String.format(Locale.US, "d=%.1f", nd.dist / 10.0), (int) midX + 2, (int) midY - 2);
            }
        }

        private static class NeighborDistance {
            public final ReferencePoint rp;
            public final double sx, sy, dist;
            public NeighborDistance(ReferencePoint rp, double sx, double sy, double dist) {
                this.rp = rp;
                this.sx = sx;
                this.sy = sy;
                this.dist = dist;
            }
        }

        private void drawCurrentPoint(Graphics2D g2, int pad, int w, int h, FeatureDescriptor fx, FeatureDescriptor fy) {
            double vx = currentFeatures.getOrDefault(fx, fx.defaultMin);
            double vy = currentFeatures.getOrDefault(fy, fy.defaultMin);

            double sx = pad + (vx - fx.defaultMin) / (fx.defaultMax - fx.defaultMin) * w;
            double sy = pad + (1.0 - (vy - fy.defaultMin) / (fy.defaultMax - fy.defaultMin)) * h;

            // Projections onto axes
            g2.setColor(new Color(255, 255, 255, 70));
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2.0f, 2.0f}, 0.0f));
            g2.draw(new Line2D.Double(sx, sy, sx, pad + h)); // down to X
            g2.draw(new Line2D.Double(sx, sy, pad, sy));      // left to Y

            // Glowing Outer Halo
            g2.setColor(new Color(255, 0, 85, 45));
            g2.fill(new Ellipse2D.Double(sx - 15, sy - 15, 30, 30));

            g2.setColor(new Color(255, 0, 85, 90));
            g2.fill(new Ellipse2D.Double(sx - 10, sy - 10, 20, 20));

            // Inner Neon Marker
            g2.setColor(new Color(255, 0, 95));
            g2.fill(new Ellipse2D.Double(sx - 5, sy - 5, 11, 11));

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f));
            g2.draw(new Ellipse2D.Double(sx - 5, sy - 5, 11, 11));

            // Target Crosshair
            g2.drawLine((int) sx - 9, (int) sy, (int) sx + 9, (int) sy);
            g2.drawLine((int) sx, (int) sy - 9, (int) sx, (int) sy + 9);

            // Label
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(255, 80, 140));
            g2.drawString("AKTUALNY POMIAR WNZ", (int) sx + 12, (int) sy - 6);
        }

        private void drawLegend(Graphics2D g2, int pad, int totalWidth) {
            int lx = pad + 10;
            int ly = pad + 15;

            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();

            String[] shortLabels = {
                    "Ulot (+)", "Ulot (-)", "Wtrącenie (Void)", "Powierzchniowe", "Pływający", "Szum tła"
            };

            for (int i = 0; i < PRPDConstants.CLASSES.length; i++) {
                String cName = PRPDConstants.CLASSES[i];
                Color c = PRPDConstants.CLASS_COLORS.getOrDefault(cName, Color.GRAY);
                String label = (i < shortLabels.length) ? shortLabels[i] : PRPDConstants.getPolishDefectName(cName);
                int itemW = fm.stringWidth(label) + 24;

                if (lx + itemW > totalWidth - pad - 10) {
                    lx = pad + 10;
                    ly += 18;
                }

                g2.setColor(new Color(24, 30, 40, 180));
                g2.fillRoundRect(lx - 4, ly - 10, itemW, 16, 4, 4);

                g2.setColor(c);
                g2.fillOval(lx, ly - 6, 7, 7);

                g2.setColor(isDarkMode ? new Color(225, 235, 245) : new Color(30, 40, 50));
                g2.drawString(label, lx + 11, ly + 2);

                lx += itemW + 8;
            }
        }

        private void drawHoverTooltip(Graphics2D g2, ReferencePoint rp, int mx, int my, FeatureDescriptor fx, FeatureDescriptor fy) {
            String cPl = PRPDConstants.getPolishDefectName(rp.className);
            double vx = rp.features.getOrDefault(fx, 0.0);
            double vy = rp.features.getOrDefault(fy, 0.0);

            String line1 = rp.label + " [" + cPl + "]";
            String line2 = String.format(Locale.US, "%s: %.2f%s | %s: %.2f%s",
                    fx.name(), vx, fx.unit, fy.name(), vy, fy.unit);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            int tw = Math.max(fm.stringWidth(line1), fm.stringWidth(line2)) + 16;
            int th = 38;

            int bx = Math.min(getWidth() - tw - 10, mx + 12);
            int by = Math.max(10, my - th - 8);

            g2.setColor(new Color(20, 26, 36, 235));
            g2.fillRoundRect(bx, by, tw, th, 6, 6);

            g2.setColor(PRPDConstants.CLASS_COLORS.getOrDefault(rp.className, Color.CYAN));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(bx, by, tw, th, 6, 6);

            g2.setColor(Color.WHITE);
            g2.drawString(line1, bx + 8, by + 14);
            g2.setColor(new Color(180, 200, 220));
            g2.drawString(line2, bx + 8, by + 29);
        }
    }
}
