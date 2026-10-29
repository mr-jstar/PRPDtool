package prpdtool;

import pipeline.DynamicPRPDHistogram;
import pipeline.PRPDHistogram;
import pipeline.StatisticalAnalyzer;
import pipeline.StatisticalAnalyzer.StatisticalResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * Window for Phase-Resolved Statistical Analysis & Fingerprint of Partial Discharges.
 * Compliant with IEC 60270 and CIGRE WG 21.03 diagnostics standards.
 * 
 * Features:
 * - 3 synchronized phase profiles: Hn(phi), Hqn(phi), Hqmax(phi) across 0-360 deg
 * - Dedicated titles above each chart box (never covered by pulses/signal)
 * - Free two-way splitter resizing between charts and statistics table
 * - Background 50 Hz reference voltage sine wave
 * - Synchronized vertical crosshair cursor with real-time measurement readout
 * - Comprehensive IEC/CIGRE statistical descriptors table (+ vs - half-cycles)
 * - Interactive help [?] next to each descriptor with plain-language explanations
 * - Automatic CIGRE diagnostic pattern advisory
 * - Live update timer (500 ms) for online streaming
 * - Export to CSV and Copy to Clipboard
 */
public class StatisticalProfilesDialog extends JDialog {

    private final PRPDTool parent;
    private final PhaseProfileChartPanel chartPanel;
    private final JTable statsTable;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;
    private final JTextArea advisoryArea;
    private final JCheckBox liveUpdateCb;
    private final JComboBox<String> binsCombo;
    private final Timer liveTimer;
    private final DescriptorHelpDialog helpDialog;

    private StatisticalResult currentResult;
    private boolean isUpdating = false;

    public StatisticalProfilesDialog(PRPDTool parent) {
        super(parent, "Phase Profiles & Statistical Fingerprint (IEC 60270 / CIGRE)", false);
        this.parent = parent;

        // Expanded default size so all buttons and charts fit comfortably without truncation
        setSize(1380, 820);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(parent);

        helpDialog = new DescriptorHelpDialog(this, parent.isDarkMode());

        chartPanel = new PhaseProfileChartPanel();
        chartPanel.setMinimumSize(new Dimension(280, 200));

        // Table setup with custom tooltip provider
        String[] columns = {"Descriptor", "Positive (+) [0°-180°]", "Negative (-) [180°-360°]", "Relation / Ratio"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        statsTable = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row >= 0 && row < getRowCount()) {
                    Object val = getValueAt(row, 0);
                    if (val != null) {
                        StatisticalDescriptorDoc.Entry entry = StatisticalDescriptorDoc.findByRowName(val.toString());
                        if (entry != null) {
                            return entry.getShortTooltipHtml();
                        }
                    }
                }
                return super.getToolTipText(e);
            }
        };

        ToolTipManager.sharedInstance().registerComponent(statsTable);
        ToolTipManager.sharedInstance().setInitialDelay(250);
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        setupTableStyle(statsTable);

        JScrollPane tableScroll = new JScrollPane(statsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 64, 70)));
        tableScroll.getViewport().setBackground(new Color(30, 32, 36));
        tableScroll.setMinimumSize(new Dimension(220, 150));

        // Advisory box
        advisoryArea = new JTextArea(3, 20);
        advisoryArea.setEditable(false);
        advisoryArea.setLineWrap(true);
        advisoryArea.setWrapStyleWord(true);
        advisoryArea.setFont(new Font("Segoe UI", Font.BOLD, 12));
        advisoryArea.setBackground(new Color(24, 26, 30));
        advisoryArea.setForeground(new Color(100, 220, 255));
        advisoryArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JPanel advisoryPanel = new JPanel(new BorderLayout());
        advisoryPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 75, 82)),
                "CIGRE Diagnostic Advisory",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(200, 205, 215)
        ));
        advisoryPanel.setBackground(new Color(30, 32, 36));
        advisoryPanel.add(advisoryArea, BorderLayout.CENTER);
        advisoryPanel.setMinimumSize(new Dimension(220, 65));

        // Right panel (Table + Advisory)
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.setPreferredSize(new Dimension(560, 600));
        rightPanel.setMinimumSize(new Dimension(260, 200)); // Enables dragging divider freely to the right
        rightPanel.setBorder(new EmptyBorder(6, 4, 6, 6));
        rightPanel.setBackground(new Color(35, 38, 43));
        rightPanel.add(tableScroll, BorderLayout.CENTER);
        rightPanel.add(advisoryPanel, BorderLayout.SOUTH);

        // Split pane with continuous layout and free 2-way dragging
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartPanel, rightPanel);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(780);
        splitPane.setResizeWeight(0.62);
        splitPane.setBorder(null);
        splitPane.setDividerSize(7);

        // Top toolbar
        JPanel topBar = new JPanel(new BorderLayout(10, 4));
        topBar.setBorder(new EmptyBorder(6, 12, 6, 12));
        topBar.setBackground(new Color(28, 30, 34));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        statusLabel = new JLabel("Pulses: 0 | Duration: 0.0 s | Rate: 0.0 pps");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(new Color(130, 220, 255));
        leftControls.add(statusLabel);

        binsCombo = new JComboBox<>(new String[]{
                "360 bins (1° / bin)",
                "180 bins (2° / bin)",
                "100 bins (CIGRE std)"
        });
        binsCombo.setSelectedIndex(0);
        binsCombo.addActionListener(e -> updateData());
        leftControls.add(new JLabel("Bins:"));
        leftControls.add(binsCombo);

        liveUpdateCb = new JCheckBox("Live update (500 ms)", true);
        liveUpdateCb.setOpaque(false);
        liveUpdateCb.setForeground(new Color(220, 225, 230));
        leftControls.add(liveUpdateCb);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> updateData());
        leftControls.add(refreshBtn);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightControls.setOpaque(false);

        JButton helpBtn = new JButton("Opis parametrów [?]");
        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD));
        helpBtn.setForeground(new Color(0, 190, 245));
        helpBtn.setToolTipText("Otwórz przewodnik po parametrach, wzorach i ich znaczeniu diagnostycznym");
        helpBtn.addActionListener(e -> helpDialog.showDescriptor(null));
        rightControls.add(helpBtn);

        JButton copyBtn = new JButton("Copy Table");
        copyBtn.setToolTipText("Copy formatted statistical descriptors to clipboard");
        copyBtn.addActionListener(e -> copyTableToClipboard());
        rightControls.add(copyBtn);

        JButton exportStatsBtn = new JButton("Export Stats CSV");
        exportStatsBtn.addActionListener(e -> exportStatsCSV());
        rightControls.add(exportStatsBtn);

        JButton exportProfilesBtn = new JButton("Export Profiles CSV");
        exportProfilesBtn.addActionListener(e -> exportProfilesCSV());
        rightControls.add(exportProfilesBtn);

        topBar.add(leftControls, BorderLayout.WEST);
        topBar.add(rightControls, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(topBar, BorderLayout.NORTH);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Live update timer (500 ms)
        liveTimer = new Timer(500, e -> {
            if (isVisible() && liveUpdateCb.isSelected() && !isUpdating) {
                updateData();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                updateData();
                liveTimer.start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                liveTimer.stop();
            }
        });
    }

    private void setupTableStyle(JTable table) {
        table.setRowHeight(25);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setBackground(new Color(30, 32, 36));
        table.setForeground(new Color(230, 235, 240));
        table.setGridColor(new Color(48, 52, 58));
        table.setSelectionBackground(new Color(55, 65, 80));
        table.setSelectionForeground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBackground(new Color(42, 45, 52));
        header.setForeground(new Color(240, 245, 250));
        header.setReorderingAllowed(false);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground((row % 2 == 0) ? new Color(28, 30, 34) : new Color(34, 37, 42));
                    if (col == 0) {
                        String textVal = String.valueOf(value);
                        setText("<html><span style='color:#e6ebf2; font-weight:bold;'>" + textVal + "</span> &nbsp;<span style='color:#00e5ff; font-weight:bold;'>[?]</span></html>");
                    } else if (col == 1) {
                        c.setForeground(new Color(110, 215, 255)); // Cyan for positive half
                        setFont(getFont().deriveFont(Font.PLAIN));
                    } else if (col == 2) {
                        c.setForeground(new Color(215, 140, 255)); // Purple for negative half
                        setFont(getFont().deriveFont(Font.PLAIN));
                    } else {
                        c.setForeground(new Color(255, 215, 110)); // Gold for ratio/cross-cycle
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        };

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Resizing behavior: allow columns to shrink flexibly without blocking JSplitPane
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(0).setMinWidth(160);

        table.getColumnModel().getColumn(1).setPreferredWidth(95);
        table.getColumnModel().getColumn(1).setMinWidth(65);

        table.getColumnModel().getColumn(2).setPreferredWidth(95);
        table.getColumnModel().getColumn(2).setMinWidth(65);

        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setMinWidth(75);

        // Click on row / descriptor opens explanation
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0) {
                    if (col == 0 || e.getClickCount() == 2) {
                        Object val = table.getValueAt(row, 0);
                        if (val != null) {
                            helpDialog.showDescriptor(val.toString());
                        }
                    }
                }
            }
        });

        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                table.setCursor(col == 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }

    /**
     * Pulls data from PRPDTool and recomputes statistical metrics.
     */
    public void updateData() {
        if (isUpdating) return;
        isUpdating = true;
        try {
            PRPDHistogram hist = parent.getHistogram();
            if (!(hist instanceof DynamicPRPDHistogram)) {
                return;
            }

            DynamicPRPDHistogram d = (DynamicPRPDHistogram) hist;
            int size = d.getSize();
            double[] phases = d.getPhases();
            double[] amps = d.getAmps();
            double[] times = d.getTimes();
            double f0 = parent.getF0();

            int binsCount = 360;
            int selected = binsCombo.getSelectedIndex();
            if (selected == 1) binsCount = 180;
            else if (selected == 2) binsCount = 100;

            currentResult = StatisticalAnalyzer.compute(phases, amps, times, size, binsCount, f0);

            // Update UI on Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                chartPanel.setResult(currentResult);
                updateTable(currentResult);
                statusLabel.setText(String.format(Locale.US,
                        "Pulses: %,d | Duration: %.2f s | Rate: %.1f pps",
                        currentResult.totalPulses, currentResult.durationSec, currentResult.repetitionRate));
                advisoryArea.setText(currentResult.diagnosticHint);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            isUpdating = false;
        }
    }

    private void updateTable(StatisticalResult r) {
        tableModel.setRowCount(0);
        if (r == null) return;

        addRow("Pulse Count (N)",
                String.format(Locale.US, "%d (%.1f%%)", r.pos.pulseCount, r.pos.pulsePercentage),
                String.format(Locale.US, "%d (%.1f%%)", r.neg.pulseCount, r.neg.pulsePercentage),
                String.format(Locale.US, "N+/N- = %.3f", r.countAsymmetryN));

        addRow("Peak Magnitude (Qmax) [mV]",
                String.format(Locale.US, "%.2f mV", r.pos.qMax),
                String.format(Locale.US, "%.2f mV", r.neg.qMax),
                String.format(Locale.US, "Q+/Q- = %.3f", r.chargeAsymmetryQ));

        addRow("Mean Magnitude (μ_Q) [mV]",
                String.format(Locale.US, "%.2f mV", r.pos.qMean),
                String.format(Locale.US, "%.2f mV", r.neg.qMean),
                String.format(Locale.US, "μ+/μ- = %.3f", r.meanChargeAsymmetry));

        addRow("Std Deviation (σ_Q) [mV]",
                String.format(Locale.US, "%.2f mV", r.pos.qStd),
                String.format(Locale.US, "%.2f mV", r.neg.qStd),
                "-");

        addRow("Inception Phase (φ_inc) [°]",
                String.format(Locale.US, "%.1f°", r.pos.inceptionPhase),
                String.format(Locale.US, "%.1f°", r.neg.inceptionPhase),
                String.format(Locale.US, "Δφ = %.1f°", Math.abs(r.neg.inceptionPhase - r.pos.inceptionPhase)));

        addRow("Mean Phase (μ_φ) [°]",
                String.format(Locale.US, "%.1f°", r.pos.phaseMean),
                String.format(Locale.US, "%.1f°", r.neg.phaseMean),
                String.format(Locale.US, "Δμ = %.1f°", Math.abs(r.neg.phaseMean - r.pos.phaseMean)));

        addRow("Phase Skewness (Sk_φ)",
                String.format(Locale.US, "%+.3f", r.pos.phaseSkewness),
                String.format(Locale.US, "%+.3f", r.neg.phaseSkewness),
                "-");

        addRow("Phase Kurtosis (Ku_φ)",
                String.format(Locale.US, "%+.3f", r.pos.phaseKurtosis),
                String.format(Locale.US, "%+.3f", r.neg.phaseKurtosis),
                "-");

        addRow("Amp Skewness (Sk_Q)",
                String.format(Locale.US, "%+.3f", r.pos.ampSkewness),
                String.format(Locale.US, "%+.3f", r.neg.ampSkewness),
                "-");

        addRow("Amp Kurtosis (Ku_Q)",
                String.format(Locale.US, "%+.3f", r.pos.ampKurtosis),
                String.format(Locale.US, "%+.3f", r.neg.ampKurtosis),
                "-");

        addRow("Cross-Correlation (CC)",
                "-",
                "-",
                String.format(Locale.US, "CC = %.3f", r.crossCorrelationCC));

        addRow("Repetition Rate (n) [pps]",
                "-",
                "-",
                String.format(Locale.US, "%.1f pps", r.repetitionRate));

        addRow("Total Apparent Charge",
                "-",
                "-",
                String.format(Locale.US, "%.1f mV*pts", r.totalApparentEnergy));

        addRow("Total Pulses (N_total)",
                "-",
                "-",
                String.format(Locale.US, "%d", r.totalPulses));

        adjustDescriptorColumnWidth(statsTable);
    }

    private boolean col0WidthAdjusted = false;

    /**
     * Automatically adjusts the width of the Descriptor column on first data load
     * so that the full label name and the [?] question mark badge fit comfortably without truncation.
     */
    private void adjustDescriptorColumnWidth(JTable table) {
        if (col0WidthAdjusted || table.getRowCount() == 0) return;
        int maxW = 220; // safe baseline
        for (int r = 0; r < table.getRowCount(); r++) {
            TableCellRenderer rdr = table.getCellRenderer(r, 0);
            Component comp = table.prepareRenderer(rdr, r, 0);
            if (comp != null) {
                maxW = Math.max(maxW, comp.getPreferredSize().width);
            }
        }
        int targetWidth = maxW + 24; // comfortable breathing room so [?] badge is completely visible
        table.getColumnModel().getColumn(0).setPreferredWidth(targetWidth);
        table.getColumnModel().getColumn(0).setWidth(targetWidth);
        table.getColumnModel().getColumn(0).setMinWidth(160);
        col0WidthAdjusted = true;
    }

    private void addRow(String desc, String pos, String neg, String relation) {
        tableModel.addRow(new Object[]{desc, pos, neg, relation});
    }

    private void copyTableToClipboard() {
        if (currentResult == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Descriptor\tPositive (+) [0°-180°]\tNegative (-) [180°-360°]\tRelation / Ratio\n");
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            sb.append(tableModel.getValueAt(r, 0)).append("\t")
              .append(tableModel.getValueAt(r, 1)).append("\t")
              .append(tableModel.getValueAt(r, 2)).append("\t")
              .append(tableModel.getValueAt(r, 3)).append("\n");
        }
        sb.append("\nDiagnostic Advisory:\n").append(currentResult.diagnosticHint).append("\n");

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
        JOptionPane.showMessageDialog(this,
                "Statistical descriptors copied to clipboard in TSV format.\nPaste directly into Excel or Word table.",
                "Copied to Clipboard", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportStatsCSV() {
        if (currentResult == null) {
            JOptionPane.showMessageDialog(this, "No data available to export.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("prpd_statistical_descriptors.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(currentResult.toCSV());
                JOptionPane.showMessageDialog(this, "Statistical descriptors exported to:\n" + f.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportProfilesCSV() {
        if (currentResult == null) {
            JOptionPane.showMessageDialog(this, "No profile data available to export.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("prpd_phase_profiles_hn_hqn.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(currentResult.profilesToCSV());
                JOptionPane.showMessageDialog(this, "Phase profiles exported to:\n" + f.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Custom component rendering 3 synchronized phase profiles with hover crosshair
     * and clean dedicated title headers positioned above the graph boxes.
     */
    private static class PhaseProfileChartPanel extends JPanel {
        private StatisticalResult result;
        private int hoverPhaseDeg = -1;

        public PhaseProfileChartPanel() {
            setBackground(new Color(22, 24, 27));
            setDoubleBuffered(true);

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    handleMouse(e.getX());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverPhaseDeg = -1;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public void setResult(StatisticalResult r) {
            this.result = r;
            repaint();
        }

        private void handleMouse(int mouseX) {
            int leftMargin = 65;
            int rightMargin = 25;
            int w = getWidth() - leftMargin - rightMargin;
            if (w <= 0) return;

            int relX = mouseX - leftMargin;
            if (relX >= 0 && relX <= w) {
                hoverPhaseDeg = (int) Math.round((double) relX / w * 360.0);
                if (hoverPhaseDeg < 0) hoverPhaseDeg = 0;
                if (hoverPhaseDeg > 360) hoverPhaseDeg = 360;
            } else {
                hoverPhaseDeg = -1;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g1) {
            super.paintComponent(g1);
            Graphics2D g = (Graphics2D) g1;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            if (result == null || result.profiles == null) {
                g.setColor(new Color(140, 145, 155));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                g.drawString("No PRPD data available. Connect Red Pitaya or load file.", 50, height / 2);
                return;
            }

            int leftMargin = 65;
            int rightMargin = 25;
            int topMargin = 22;
            int bottomMargin = 42;
            int gap = 16;
            int headerH = 18; // Dedicated header strip above each box

            int totalPlotH = height - topMargin - bottomMargin;
            int singleSlotH = (totalPlotH - 2 * gap) / 3;
            int boxH = singleSlotH - headerH;
            int plotW = width - leftMargin - rightMargin;

            if (plotW <= 50 || boxH <= 20) return;

            int slotY1 = topMargin;
            int slotY2 = slotY1 + singleSlotH + gap;
            int slotY3 = slotY2 + singleSlotH + gap;

            drawProfileHn(g, leftMargin, slotY1, plotW, boxH, headerH);
            drawProfileHqn(g, leftMargin, slotY2, plotW, boxH, headerH);
            drawProfileHqmax(g, leftMargin, slotY3, plotW, boxH, headerH);

            // Synchronized crosshair across all 3 charts
            if (hoverPhaseDeg >= 0 && hoverPhaseDeg <= 360) {
                int cursorX = leftMargin + (int) Math.round((double) hoverPhaseDeg / 360.0 * plotW);

                g.setColor(new Color(255, 235, 120, 190));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
                g.drawLine(cursorX, slotY1 + headerH, cursorX, slotY3 + headerH + boxH);

                // Read values at cursor
                int binIdx = (int) Math.round((double) hoverPhaseDeg / 360.0 * (result.profiles.binsCount - 1));
                if (binIdx < 0) binIdx = 0;
                if (binIdx >= result.profiles.binsCount) binIdx = result.profiles.binsCount - 1;

                int hnVal = result.profiles.Hn[binIdx];
                double hqnVal = result.profiles.Hqn[binIdx];
                double hqmaxVal = result.profiles.Hqmax[binIdx];
                String cycle = hoverPhaseDeg < 180 ? "Positive (+)" : "Negative (-)";

                String hud = String.format(Locale.US,
                        "φ: %d° | Hn: %d counts | Hqn: %.2f mV | Hqmax: %.2f mV | Cycle: %s",
                        hoverPhaseDeg, hnVal, hqnVal, hqmaxVal, cycle);

                g.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g.getFontMetrics();
                int hudW = fm.stringWidth(hud) + 16;
                int hudX = Math.max(leftMargin, Math.min(width - rightMargin - hudW, cursorX - hudW / 2));
                int hudY = slotY1 + 14;

                g.setColor(new Color(30, 34, 40, 240));
                g.fillRoundRect(hudX, hudY - 14, hudW, 18, 6, 6);
                g.setColor(new Color(255, 220, 100));
                g.drawRoundRect(hudX, hudY - 14, hudW, 18, 6, 6);
                g.drawString(hud, hudX + 8, hudY);
            }
        }

        private void drawTitleHeader(Graphics2D g, int x0, int slotY, String title, Color accentColor) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            // Bullet indicator
            g.setColor(accentColor);
            g.fillOval(x0 + 2, slotY + 4, 7, 7);
            // Title text above chart
            g.setColor(new Color(230, 235, 245));
            g.drawString(title, x0 + 15, slotY + 12);
        }

        private void drawProfileHn(Graphics2D g, int x0, int slotY, int w, int boxH, int headerH) {
            int boxY = slotY + headerH;
            drawTitleHeader(g, x0, slotY, "Hn(φ) — Pulse Count Distribution [Counts]", new Color(0, 210, 255));
            drawChartBox(g, x0, boxY, w, boxH, result.profiles.maxHn, false);

            int bins = result.profiles.binsCount;
            int maxHn = Math.max(1, result.profiles.maxHn);
            double binW = (double) w / bins;

            for (int i = 0; i < bins; i++) {
                int val = result.profiles.Hn[i];
                if (val <= 0) continue;
                int barH = (int) Math.round((double) val / maxHn * boxH);
                int bx = x0 + (int) Math.round(i * binW);
                int bw = Math.max(1, (int) Math.ceil(binW));
                int by = boxY + boxH - barH;

                // Color tint: cyan for pos half, purple for neg half
                Color col = (i < bins / 2)
                        ? new Color(0, 195, 245, 220)
                        : new Color(185, 110, 255, 220);

                g.setColor(col);
                g.fillRect(bx, by, bw, barH);
            }
        }

        private void drawProfileHqn(Graphics2D g, int x0, int slotY, int w, int boxH, int headerH) {
            int boxY = slotY + headerH;
            drawTitleHeader(g, x0, slotY, "Hqn(φ) — Mean Pulse Magnitude [mV]", new Color(45, 230, 140));
            drawChartBox(g, x0, boxY, w, boxH, result.profiles.maxHqn, false);

            int bins = result.profiles.binsCount;
            double maxHqn = Math.max(1e-3, result.profiles.maxHqn);
            double binW = (double) w / bins;

            int[] xPoints = new int[bins + 2];
            int[] yPoints = new int[bins + 2];

            xPoints[0] = x0;
            yPoints[0] = boxY + boxH;

            for (int i = 0; i < bins; i++) {
                double val = result.profiles.Hqn[i];
                int py = boxY + boxH - (int) Math.round(val / maxHqn * boxH);
                int px = x0 + (int) Math.round((i + 0.5) * binW);
                xPoints[i + 1] = px;
                yPoints[i + 1] = py;
            }

            xPoints[bins + 1] = x0 + w;
            yPoints[bins + 1] = boxY + boxH;

            // Semi-transparent fill
            g.setColor(new Color(40, 220, 130, 45));
            g.fillPolygon(xPoints, yPoints, bins + 2);

            // Outline line
            g.setColor(new Color(45, 230, 140));
            g.setStroke(new BasicStroke(1.8f));
            for (int i = 1; i < bins; i++) {
                g.drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
            }
        }

        private void drawProfileHqmax(Graphics2D g, int x0, int slotY, int w, int boxH, int headerH) {
            int boxY = slotY + headerH;
            drawTitleHeader(g, x0, slotY, "Hqmax(φ) — Peak Magnitude Envelope [mV]", new Color(255, 100, 75));
            drawChartBox(g, x0, boxY, w, boxH, result.profiles.maxHqmax, true);

            int bins = result.profiles.binsCount;
            double maxHqmax = Math.max(1e-3, result.profiles.maxHqmax);
            double binW = (double) w / bins;

            int prevX = -1, prevY = -1;
            g.setColor(new Color(255, 100, 75));
            g.setStroke(new BasicStroke(1.8f));

            for (int i = 0; i < bins; i++) {
                double val = result.profiles.Hqmax[i];
                int px = x0 + (int) Math.round((i + 0.5) * binW);
                int py = boxY + boxH - (int) Math.round(val / maxHqmax * boxH);

                if (val > 0) {
                    if (prevX >= 0) {
                        g.drawLine(prevX, prevY, px, py);
                    }
                    g.fillOval(px - 2, py - 2, 5, 5);
                    prevX = px;
                    prevY = py;
                } else {
                    if (prevX >= 0) {
                        g.drawLine(prevX, prevY, px, boxY + boxH);
                    }
                    prevX = px;
                    prevY = boxY + boxH;
                }
            }
        }

        private void drawChartBox(Graphics2D g, int x0, int boxY, int w, int boxH,
                                  double maxY, boolean isBottomChart) {
            // Background box
            g.setColor(new Color(18, 20, 23));
            g.fillRect(x0, boxY, w, boxH);

            // Shading for positive and negative half cycles
            int midX = x0 + w / 2;
            g.setColor(new Color(0, 150, 255, 12));
            g.fillRect(x0, boxY, w / 2, boxH);
            g.setColor(new Color(180, 80, 255, 12));
            g.fillRect(midX, boxY, w / 2, boxH);

            // Reference Sine Wave in background (50 Hz)
            g.setColor(new Color(255, 195, 60, 60));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
            int prevSineX = x0;
            int prevSineY = boxY + boxH / 2;
            for (int step = 0; step <= w; step += 3) {
                double phaseRad = (double) step / w * 2.0 * Math.PI;
                int sy = boxY + boxH / 2 - (int) Math.round(Math.sin(phaseRad) * (boxH * 0.44));
                int sx = x0 + step;
                if (step > 0) g.drawLine(prevSineX, prevSineY, sx, sy);
                prevSineX = sx;
                prevSineY = sy;
            }

            // Grid lines
            g.setColor(new Color(40, 44, 50));
            g.setStroke(new BasicStroke(1.0f));
            for (int deg = 45; deg < 360; deg += 45) {
                int gx = x0 + (int) Math.round((double) deg / 360.0 * w);
                g.drawLine(gx, boxY, gx, boxY + boxH);
            }

            // Zero crossing divider line at 180°
            g.setColor(new Color(120, 125, 135, 150));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 3}, 0));
            g.drawLine(midX, boxY, midX, boxY + boxH);

            // Outer border
            g.setColor(new Color(55, 60, 68));
            g.setStroke(new BasicStroke(1.0f));
            g.drawRect(x0, boxY, w, boxH);

            // Y-axis labels
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.setColor(new Color(160, 165, 175));
            String topLabel = (maxY >= 10.0) ? String.format(Locale.US, "%.0f", maxY) : String.format(Locale.US, "%.2f", maxY);
            g.drawString(topLabel, x0 - 35, boxY + 11);
            g.drawString("0", x0 - 18, boxY + boxH);

            // X-axis phase labels (only for bottom chart)
            if (isBottomChart) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g.setColor(new Color(180, 185, 195));
                for (int deg = 0; deg <= 360; deg += 45) {
                    int tx = x0 + (int) Math.round((double) deg / 360.0 * w);
                    String lbl = deg + "°";
                    int tw = g.getFontMetrics().stringWidth(lbl);
                    g.drawString(lbl, tx - tw / 2, boxY + boxH + 18);
                    g.setColor(new Color(75, 80, 90));
                    g.drawLine(tx, boxY + boxH, tx, boxY + boxH + 4);
                    g.setColor(new Color(180, 185, 195));
                }

                // Sub-labels: Positive and Negative half-cycles
                g.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g.setColor(new Color(0, 180, 240));
                g.drawString("POSITIVE HALF-CYCLE (+)", x0 + w / 4 - 60, boxY + boxH + 34);
                g.setColor(new Color(195, 120, 255));
                g.drawString("NEGATIVE HALF-CYCLE (-)", midX + w / 4 - 60, boxY + boxH + 34);
            }
        }
    }
}
