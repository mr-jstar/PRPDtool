package prpdtool;

import classifiers.PRPDModelRegistry;
import classifiers.PRPDModelRegistry.BenchmarkSuiteResult;
import classifiers.PRPDModelRegistry.ModelFamily;
import classifiers.PRPDModelRegistry.ModelId;
import classifiers.PRPDModelRegistry.ModelOutput;
import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;
import pipeline.StatisticalAnalyzer;
import pipeline.StatisticalAnalyzer.StatisticalResult;
import pipeline.DynamicPRPDHistogram;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Benchmark Arena Dialog for Comparing 11 AI & ML Models
 * across 4 diverse paradigms in Partial Discharge Diagnosis (IEEE Trans. DEI / CIGRE 226).
 */
public class AIModelBenchmarkDialog extends JDialog {

    private final PRPDTool parent;
    private final boolean isDarkMode;
    private final AIModelBenchmarkHelpDialog helpDialog;
    private final DiagnosticCheatSheetDialog cheatSheetDialog;

    private final ConfusionMatrixPanel confusionMatrixPanel;
    private final MetricsBarChartPanel metricsBarChartPanel;
    private final RocPrCurvePanel rocPrCurvePanel;
    private final LearningCurvesPanel learningCurvesPanel;
    private final JToggleButton lightModeToggleBtn;
    private final JToggleButton langToggleBtn;
    private final JTabbedPane tabbedPane;
    private boolean isEnglishMode = false;

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel consensusLabel;
    private final JLabel consensusBadge;
    private final JLabel summaryStatsLabel;
    private final JButton runBenchmarkBtn;

    private BenchmarkSuiteResult lastResult;
    private ModelFamily activeFilter = null; // null = all
    private final List<ModelOutput> allOutputs = new ArrayList<>();

    public AIModelBenchmarkDialog(PRPDTool parent, boolean isDarkMode) {
        super(parent, "AI Model comparison (1D, 2D, Sequential, CIGRE ML)", false);
        this.parent = parent;
        this.isDarkMode = isDarkMode;
        this.helpDialog = new AIModelBenchmarkHelpDialog(this, isDarkMode);
        this.cheatSheetDialog = new DiagnosticCheatSheetDialog(this, isDarkMode);
        this.confusionMatrixPanel = new ConfusionMatrixPanel(this, isDarkMode);
        this.metricsBarChartPanel = new MetricsBarChartPanel(this, isDarkMode);
        this.rocPrCurvePanel = new RocPrCurvePanel(this, isDarkMode);
        this.learningCurvesPanel = new LearningCurvesPanel(this, isDarkMode);

        setSize(1380, 850);
        setMinimumSize(new Dimension(860, 520));
        setLocationRelativeTo(parent);

        Color bg = isDarkMode ? new Color(18, 22, 28) : new Color(245, 247, 250);
        Color borderC = isDarkMode ? new Color(45, 52, 64) : new Color(215, 220, 228);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout(0, 0));

        // --- TOP HEADER ---
        JPanel topHeader = new JPanel(new BorderLayout(10, 0));
        topHeader.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        topHeader.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel("AI MODEL COMPARISON (11 ARCHITEKTUR)");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));

        JLabel subLbl = new JLabel("Porównanie 11 modeli (Wizja 2D, Przebiegi 1D, Cechy CIGRE, Baza Wiedzy)");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(isDarkMode ? new Color(170, 180, 195) : new Color(100, 110, 125));

        titleBox.add(titleLbl);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subLbl);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        topButtons.setOpaque(false);

        lightModeToggleBtn = new JToggleButton("Wykresy do artykułu");
        lightModeToggleBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lightModeToggleBtn.setFocusPainted(false);
        lightModeToggleBtn.addActionListener(e -> {
            boolean light = lightModeToggleBtn.isSelected();
            lightModeToggleBtn.setText(light ? (isEnglishMode ? "Light Mode (Print)" : "Wykresy: Jasny (Druk)") : (isEnglishMode ? "Paper Mode" : "Wykresy do artykułu"));
            confusionMatrixPanel.setLightMode(light);
            metricsBarChartPanel.setLightMode(light);
            rocPrCurvePanel.setLightMode(light);
            learningCurvesPanel.setLightMode(light);
        });
        topButtons.add(lightModeToggleBtn);

        langToggleBtn = new JToggleButton("Język: EN (Artykuł)");
        langToggleBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        langToggleBtn.setFocusPainted(false);
        langToggleBtn.addActionListener(e -> {
            setEnglishMode(langToggleBtn.isSelected());
        });
        topButtons.add(langToggleBtn);

        JButton cheatSheetBtn = new JButton("Ściągawka [CIGRE]");
        cheatSheetBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cheatSheetBtn.setBackground(isDarkMode ? new Color(48, 56, 72) : new Color(225, 235, 245));
        cheatSheetBtn.setForeground(isDarkMode ? new Color(255, 185, 0) : new Color(180, 100, 0));
        cheatSheetBtn.setFocusPainted(false);
        cheatSheetBtn.addActionListener(e -> cheatSheetDialog.setVisible(true));
        topButtons.add(cheatSheetBtn);

        JButton helpBtn = new JButton("Poradnik [?]");
        helpBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        helpBtn.setBackground(isDarkMode ? new Color(38, 45, 58) : new Color(225, 232, 242));
        helpBtn.setForeground(isDarkMode ? new Color(255, 224, 102) : new Color(140, 100, 0));
        helpBtn.setFocusPainted(false);
        helpBtn.addActionListener(e -> helpDialog.setVisible(true));
        topButtons.add(helpBtn);

        runBenchmarkBtn = new JButton("Uruchom Benchmark");
        runBenchmarkBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        runBenchmarkBtn.setBackground(isDarkMode ? new Color(0, 120, 215) : new Color(0, 102, 204));
        runBenchmarkBtn.setForeground(Color.WHITE);
        runBenchmarkBtn.setFocusPainted(false);
        runBenchmarkBtn.addActionListener(e -> runBenchmarkAsync());
        topButtons.add(runBenchmarkBtn);

        topHeader.add(titleBox, BorderLayout.WEST);
        topHeader.add(topButtons, BorderLayout.EAST);
        getContentPane().add(topHeader, BorderLayout.NORTH);

        // --- CENTER PANEL (Consensus Banner + Filter Tabs + Table) ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // 1. Consensus Banner
        JPanel consensusCard = new JPanel(new BorderLayout(10, 0));
        consensusCard.setBackground(isDarkMode ? new Color(26, 32, 42) : Color.WHITE);
        consensusCard.setBorder(new CompoundBorder(
                new LineBorder(isDarkMode ? new Color(0, 180, 255, 120) : new Color(0, 120, 215, 120), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JPanel consLeft = new JPanel();
        consLeft.setOpaque(false);
        consLeft.setLayout(new BoxLayout(consLeft, BoxLayout.Y_AXIS));

        JLabel consPre = new JLabel("WIODĄCY KONSENSUS DIAGNOSTYCZNY ARENY:");
        consPre.setFont(new Font("Segoe UI", Font.BOLD, 10));
        consPre.setForeground(isDarkMode ? new Color(150, 165, 185) : new Color(90, 105, 125));

        consensusLabel = new JLabel("Oczekiwanie na uruchomienie benchmarku...");
        consensusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        consensusLabel.setForeground(isDarkMode ? new Color(255, 80, 80) : new Color(200, 30, 30));

        consLeft.add(consPre);
        consLeft.add(Box.createVerticalStrut(2));
        consLeft.add(consensusLabel);

        consensusBadge = new JLabel("  0.0% ZGODY  ");
        consensusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        consensusBadge.setOpaque(true);
        consensusBadge.setBackground(isDarkMode ? new Color(0, 102, 204) : new Color(210, 230, 255));
        consensusBadge.setForeground(Color.WHITE);
        consensusBadge.setBorder(new EmptyBorder(4, 10, 4, 10));

        consensusCard.add(consLeft, BorderLayout.WEST);
        consensusCard.add(consensusBadge, BorderLayout.EAST);
        centerPanel.add(consensusCard, BorderLayout.NORTH);

        // 2. Filter Bar
        JPanel middleBox = new JPanel(new BorderLayout(0, 8));
        middleBox.setOpaque(false);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterBar.setOpaque(false);

        ButtonGroup filterGroup = new ButtonGroup();
        filterBar.add(createFilterButton("Wszystkie Modele (11)", null, filterGroup, true));
        filterBar.add(createFilterButton("Wizja 2D (4)", ModelFamily.VISION_2D, filterGroup, false));
        filterBar.add(createFilterButton("Przebieg 1D (2)", ModelFamily.WAVEFORM_1D, filterGroup, false));
        filterBar.add(createFilterButton("Cechy CIGRE (4)", ModelFamily.CIGRE_FEATURE_ML, filterGroup, false));
        filterBar.add(createFilterButton("Baza Wiedzy (1)", ModelFamily.EXPERT_SYSTEM, filterGroup, false));

        middleBox.add(filterBar, BorderLayout.NORTH);

        // 3. Benchmark Table
        String[] columnNames = {
                "Model AI / Algorytm",
                "Paradygmat / Rodzina",
                "Predykcja (Klasa WNZ)",
                "Pewność (Confidence)",
                "Czas (ms)",
                "Reprezentacja Danych",
                "Pomoc"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6; // only help button clickable
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.setBackground(isDarkMode ? new Color(22, 26, 34) : Color.WHITE);
        table.setForeground(isDarkMode ? new Color(230, 235, 240) : new Color(25, 30, 35));
        table.getTableHeader().setBackground(isDarkMode ? new Color(30, 36, 46) : new Color(230, 235, 242));
        table.getTableHeader().setForeground(isDarkMode ? new Color(200, 210, 220) : new Color(40, 50, 60));
        table.setSelectionBackground(isDarkMode ? new Color(45, 60, 80) : new Color(210, 230, 255));
        table.setSelectionForeground(Color.WHITE);
        table.setShowGrid(true);
        table.setGridColor(isDarkMode ? new Color(38, 44, 56) : new Color(225, 230, 238));

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(210);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(75);
        table.getColumnModel().getColumn(5).setPreferredWidth(320);
        table.getColumnModel().getColumn(6).setPreferredWidth(60);

        // Custom renderers
        table.getColumnModel().getColumn(2).setCellRenderer(new DefectCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new ConfidenceCellRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(6).setCellEditor(new ButtonEditor(new JCheckBox(), this));

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(new LineBorder(borderC, 1));
        tableScroll.getViewport().setBackground(isDarkMode ? new Color(22, 26, 34) : Color.WHITE);
        middleBox.add(tableScroll, BorderLayout.CENTER);

        centerPanel.add(middleBox, BorderLayout.CENTER);
        this.tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 11));
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.addTab("Arena Modeli (Tabela i Konsensus)", centerPanel);
        tabbedPane.addTab("Macierz Pomyłek (Confusion Matrix)", confusionMatrixPanel);
        tabbedPane.addTab("Wykres Słupkowy (Porównanie Metryk)", metricsBarChartPanel);
        tabbedPane.addTab("Krzywe ROC & Precision-Recall", rocPrCurvePanel);
        tabbedPane.addTab("Dynamika Uczenia (Learning Curves)", learningCurvesPanel);

        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        // --- BOTTOM BAR ---
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        bottomBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderC),
                new EmptyBorder(10, 16, 10, 16)
        ));

        summaryStatsLabel = new JLabel("Kliknij 'Uruchom Benchmark Wszystkich Modeli', aby przetestować komplet 11 architektur.");
        summaryStatsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        summaryStatsLabel.setForeground(isDarkMode ? new Color(210, 220, 230) : new Color(30, 35, 42));

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        closeBtn.addActionListener(e -> setVisible(false));

        bottomBar.add(summaryStatsLabel, BorderLayout.WEST);
        bottomBar.add(closeBtn, BorderLayout.EAST);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);
    }

    private JToggleButton createFilterButton(String text, ModelFamily family, ButtonGroup group, boolean select) {
        JToggleButton b = new JToggleButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setFocusPainted(false);
        b.setSelected(select);
        b.addActionListener(e -> {
            this.activeFilter = family;
            refreshTableRows();
        });
        group.add(b);
        return b;
    }

    public void setEnglishMode(boolean en) {
        this.isEnglishMode = en;
        if (langToggleBtn.isSelected() != en) {
            langToggleBtn.setSelected(en);
        }
        langToggleBtn.setText(en ? "Język: EN (Artykuł)" : "Podpisy EN (Artykuł)");
        lightModeToggleBtn.setText(lightModeToggleBtn.isSelected()
                ? (en ? "Light Mode (Print)" : "Wykresy: Jasny (Druk)")
                : (en ? "Paper Mode" : "Wykresy do artykułu"));
        tabbedPane.setTitleAt(0, en ? "Model Arena (Table & Consensus)" : "Arena Modeli (Tabela i Konsensus)");
        tabbedPane.setTitleAt(1, en ? "Confusion Matrix (Heatmap)" : "Macierz Pomyłek (Confusion Matrix)");
        tabbedPane.setTitleAt(2, en ? "Metrics Bar Chart (Comparison)" : "Wykres Słupkowy (Porównanie Metryk)");
        tabbedPane.setTitleAt(3, en ? "ROC & Precision-Recall Curves" : "Krzywe ROC & Precision-Recall");
        tabbedPane.setTitleAt(4, en ? "Learning Curves (Dynamics)" : "Dynamika Uczenia (Learning Curves)");
        confusionMatrixPanel.setEnglishMode(en);
        metricsBarChartPanel.setEnglishMode(en);
        rocPrCurvePanel.setEnglishMode(en);
        learningCurvesPanel.setEnglishMode(en);
    }

    public boolean isEnglishMode() {
        return isEnglishMode;
    }

    public void updateAndShow() {
        setVisible(true);
        runBenchmarkAsync();
    }

    public void runBenchmarkAsync() {
        runBenchmarkBtn.setEnabled(false);
        runBenchmarkBtn.setText("Wykonywanie benchmarku...");

        SwingWorker<BenchmarkSuiteResult, Void> worker = new SwingWorker<>() {
            @Override
            protected BenchmarkSuiteResult doInBackground() throws Exception {
                // 1. Get pulse from repository or generate fallback
                double[] pulse;
                PulseWaveform maxP = PulseWaveformRepository.getInstance().getMaxPulse();
                if (maxP != null && maxP.raw != null && maxP.raw.length >= 64) {
                    pulse = maxP.raw.clone();
                } else {
                    pulse = generateSyntheticPDPulse();
                }

                // 2. Compute statistical result from histogram
                StatisticalResult sr = null;
                try {
                    DynamicPRPDHistogram d = (DynamicPRPDHistogram) parent.getHistogram();
                    if (d != null && d.getSize() > 0) {
                        sr = StatisticalAnalyzer.compute(d.getPhases(), d.getAmps(), d.getTimes(), d.getSize(), 360, parent.getF0());
                    }
                } catch (Exception ignored) {
                }

                // 3. Execute full suite
                return PRPDModelRegistry.runFullBenchmark(sr, pulse, 125e6);
            }

            @Override
            protected void done() {
                try {
                    lastResult = get();
                    allOutputs.clear();
                    if (lastResult != null) {
                        allOutputs.addAll(lastResult.outputs);
                        consensusLabel.setText(lastResult.consensusPolishName.toUpperCase());
                        consensusBadge.setText(String.format("  %.1f%% ZGODY (%d/11)  ",
                                lastResult.consensusScorePercent,
                                (int) Math.round(lastResult.consensusScorePercent * 11.0 / 100.0)));

                        summaryStatsLabel.setText(String.format(
                                "<html>Najszybszy model: <b style='color:#00e1ff;'>%s (%.2f ms)</b> | Najwyższa pewność: <b>%s (%.1f%%)</b> | Łączny czas: <b>%.1f ms</b></html>",
                                lastResult.fastestModel.modelId.getDisplayName(),
                                lastResult.fastestModel.inferenceTimeMs,
                                lastResult.mostConfidentModel.modelId.getDisplayName(),
                                lastResult.mostConfidentModel.confidence * 100.0f,
                                lastResult.totalBenchmarkTimeMs
                        ));
                    }
                    refreshTableRows();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    runBenchmarkBtn.setEnabled(true);
                    runBenchmarkBtn.setText("Uruchom Benchmark Wszystkich Modeli");
                }
            }
        };
        worker.execute();
    }

    private void refreshTableRows() {
        tableModel.setRowCount(0);
        for (ModelOutput mo : allOutputs) {
            if (activeFilter != null && mo.modelId.getFamily() != activeFilter) {
                continue;
            }
            tableModel.addRow(new Object[]{
                    mo.modelId.getDisplayName(),
                    mo.modelId.getFamily().getDisplayName(),
                    mo.polishName,
                    mo.confidence,
                    String.format("%.2f", mo.inferenceTimeMs),
                    mo.inputRepresentation,
                    mo.modelId.name()
            });
        }
    }

    private static double[] generateSyntheticPDPulse() {
        int n = 1024;
        double[] s = new double[n];
        int center = 300;
        for (int i = 0; i < n; i++) {
            double t = (i - center) / 30.0;
            double pulse = Math.exp(-t * t) * Math.sin(3.5 * t) * 85.0;
            double noise = (Math.random() - 0.5) * 8.0;
            s[i] = pulse + noise;
        }
        return s;
    }

    public void showHelpForModel(String modelIdStr) {
        try {
            ModelId id = ModelId.valueOf(modelIdStr);
            String docKey;
            switch (id) {
                case YOLO_V8: docKey = "yolo_v8"; break;
                case SQUEEZENET: docKey = "squeezenet"; break;
                case RESNET_18: docKey = "resnet_18"; break;
                case CNN_LSTM_2D: docKey = "cnn_lstm_2d"; break;
                case CNN_1D: docKey = "cnn_1d"; break;
                case CNN_LSTM_1D: docKey = "cnn_lstm_1d"; break;
                case SVM_QUADRATIC: docKey = "svm_quadratic"; break;
                case RANDOM_FOREST: docKey = "random_forest"; break;
                case KNN: docKey = "knn"; break;
                case ANN_MLP: docKey = "ann_mlp"; break;
                case CIGRE_RULES:
                default:
                    docKey = "cigre_rules";
                    break;
            }
            helpDialog.showModel(docKey);
        } catch (Exception ignored) {
            helpDialog.setVisible(true);
        }
    }

    // --- TABLE RENDERERS ---

    private class DefectCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = (value != null) ? value.toString() : "";
            lbl.setText(text);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));

            if (!isSelected) {
                if (text.contains("Ulot w półokresie dodatnim")) {
                    lbl.setForeground(new Color(255, 75, 75));
                } else if (text.contains("Ulot w półokresie ujemnym")) {
                    lbl.setForeground(new Color(0, 190, 255));
                } else if (text.contains("powierzchniowe")) {
                    lbl.setForeground(new Color(0, 220, 120));
                } else if (text.contains("wewnętrzne")) {
                    lbl.setForeground(new Color(190, 80, 255));
                } else if (text.contains("pływający")) {
                    lbl.setForeground(new Color(255, 200, 0));
                } else {
                    lbl.setForeground(Color.GRAY);
                }
            }
            return lbl;
        }
    }

    private class ConfidenceCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            float conf = (value instanceof Float) ? (Float) value : 0.0f;
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue((int) (conf * 100));
            bar.setStringPainted(true);
            bar.setString(String.format("%.1f%%", conf * 100.0f));
            bar.setFont(new Font("Segoe UI", Font.BOLD, 10));
            bar.setForeground(conf > 0.85f ? new Color(0, 180, 255) : (conf > 0.65f ? new Color(255, 180, 0) : Color.GRAY));
            bar.setBackground(isDarkMode ? new Color(30, 36, 46) : new Color(230, 235, 242));
            bar.setBorder(new EmptyBorder(4, 6, 4, 6));
            return bar;
        }
    }

    private static class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        ButtonRenderer() {
            setText("[?]");
            setFont(new Font("Segoe UI", Font.BOLD, 10));
            setMargin(new Insets(1, 2, 1, 2));
            setFocusPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return this;
        }
    }

    private static class ButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private String currentModelId;
        private final AIModelBenchmarkDialog dialog;

        ButtonEditor(JCheckBox checkBox, AIModelBenchmarkDialog dialog) {
            super(checkBox);
            this.dialog = dialog;
            button = new JButton("[?]");
            button.setFont(new Font("Segoe UI", Font.BOLD, 10));
            button.setFocusPainted(false);
            button.addActionListener(e -> {
                fireEditingStopped();
                dialog.showHelpForModel(currentModelId);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentModelId = (value != null) ? value.toString() : "";
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return currentModelId;
        }
    }
}
