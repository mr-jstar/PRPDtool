package prpdtool;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SpinnerNumberModel;
import redpitaya.RedPitayaAcquisition;
import redpitaya.RedPitayaCapture;
import redpitaya.RedPitayaConfig;

public class RedPitayaTriggerDialog extends JDialog {

    private final RedPitayaConfig baseConfig;
    private final int channel;
    private RedPitayaCapture reference;
    private RedPitayaCapture defect;
    private double[] referenceValues;
    private double[] defectValues;
    private boolean accepted;

    private final JButton referenceButton = new JButton("Start: reference");
    private final JButton defectButton = new JButton("Start: defect");
    private final JButton okButton = new JButton("OK");
    private final JButton cancelButton = new JButton("Cancel");
    private final JComboBox<String> mode = new JComboBox<>(new String[]{"ABS | abs(IN1)", "+/- | IN1"});
    private final JComboBox<String> theme = new JComboBox<>(new String[]{"Light", "Dark"});
    private final JSpinner triggerValue = new JSpinner(new SpinnerNumberModel(0.0, -20.0, 20.0, 0.001));
    private final JLabel status = new JLabel("Collect reference without defect first.");
    private final JLabel stats = new JLabel(" ");
    private final PlotPanel referencePlot = new PlotPanel(Color.BLUE);
    private final PlotPanel defectPlot = new PlotPanel(new Color(190, 40, 40));

    public RedPitayaTriggerDialog(java.awt.Frame owner, RedPitayaConfig config, int channel) {
        super(owner, "IN" + channel + " trigger calibration", true);
        this.channel = channel;
        this.baseConfig = config.copy();
        this.baseConfig.channels = new int[]{channel};
        this.baseConfig.visualChannel = channel;
        this.baseConfig.triggerSource = "NOW";
        this.baseConfig.triggerLevel = 0.0;
        setSize(1100, 800);
        setLocationRelativeTo(owner);

        JPanel toolbar = new JPanel();
        toolbar.add(referenceButton);
        toolbar.add(defectButton);
        toolbar.add(new JLabel("Mode"));
        toolbar.add(mode);
        toolbar.add(new JLabel("Theme"));
        toolbar.add(theme);
        toolbar.add(new JLabel("Trigger [V]"));
        toolbar.add(triggerValue);
        toolbar.add(okButton);
        toolbar.add(cancelButton);

        JPanel plots = new JPanel(new GridLayout(2, 1, 4, 4));
        JPanel refWrap = new JPanel(new BorderLayout());
        refWrap.add(new JLabel("Reference without defect"), BorderLayout.NORTH);
        refWrap.add(referencePlot, BorderLayout.CENTER);
        JPanel defWrap = new JPanel(new BorderLayout());
        defWrap.add(new JLabel("Measurement with defect"), BorderLayout.NORTH);
        defWrap.add(defectPlot, BorderLayout.CENTER);
        plots.add(refWrap);
        plots.add(defWrap);

        JPanel footer = new JPanel(new GridLayout(2, 1));
        footer.add(stats);
        footer.add(status);

        setLayout(new BorderLayout(5, 5));
        add(toolbar, BorderLayout.NORTH);
        add(plots, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        defectButton.setEnabled(false);
        okButton.setEnabled(false);

        referenceButton.addActionListener(e -> startAcquisition(true));
        defectButton.addActionListener(e -> startAcquisition(false));
        okButton.addActionListener(e -> {
            accepted = true;
            setVisible(false);
        });
        cancelButton.addActionListener(e -> setVisible(false));
        mode.addActionListener(e -> recompute());
        theme.addActionListener(e -> updateTheme());
        triggerValue.addChangeListener(e -> updateTriggerLines());
        mode.removeAllItems();
        mode.addItem("ABS | abs(IN" + channel + ")");
        mode.addItem("+/- | IN" + channel);
        updateTheme();
    }

    public boolean accepted() {
        return accepted;
    }

    public double triggerLevel() {
        return ((Number) triggerValue.getValue()).doubleValue();
    }

    private void startAcquisition(boolean referenceKind) {
        setBusy(true);
        status.setText("Acquisition in progress...");
        SwingWorker<RedPitayaCapture, String> worker = new SwingWorker<>() {
            @Override
            protected RedPitayaCapture doInBackground() throws Exception {
                return RedPitayaAcquisition.captureChannel(
                        baseConfig,
                        channel,
                        900_000,
                        text -> publish(text)
                );
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    status.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    RedPitayaCapture capture = get();
                    if (referenceKind) {
                        reference = capture;
                        defectButton.setEnabled(true);
                        status.setText("Reference collected. Now collect defect measurement.");
                    } else {
                        defect = capture;
                        status.setText("Defect measurement collected.");
                    }
                    recompute();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            RedPitayaTriggerDialog.this,
                            ex.getMessage(),
                            "Trigger calibration failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                    status.setText("Error: " + ex.getMessage());
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    private void setBusy(boolean busy) {
        referenceButton.setEnabled(!busy);
        defectButton.setEnabled(!busy && reference != null);
        okButton.setEnabled(!busy && reference != null && defect != null);
    }

    private void recompute() {
        boolean signed = mode.getSelectedIndex() == 1;
        ((SpinnerNumberModel) triggerValue.getModel()).setMinimum(signed ? -20.0 : 0.0);
        if (!signed && triggerLevel() < 0.0) {
            triggerValue.setValue(0.0);
        }

        if (reference != null) {
            referenceValues = valuesForMode(reference.volts(), signed);
            referencePlot.setData(reference.t, referenceValues);
            referencePlot.setLabels("Time [s]", yAxisLabel(signed));
        }
        if (defect != null) {
            defectValues = valuesForMode(defect.volts(), signed);
            defectPlot.setData(defect.t, defectValues);
            defectPlot.setLabels("Time [s]", yAxisLabel(signed));
        }
        if (reference != null && defect != null) {
            TriggerProposal proposal = proposeTrigger(reference.volts(), defect.volts(), signed);
            triggerValue.setValue(proposal.value);
            okButton.setEnabled(true);
            stats.setText(String.format(
                    "proposal=%.6f V | background=%.6f V | defect=%.6f V | polarity=%s",
                    proposal.value,
                    proposal.referenceNoise,
                    proposal.defectLevel,
                    proposal.polarity
            ));
        }
        updateTriggerLines();
    }

    private String yAxisLabel(boolean signed) {
        return (signed ? "IN" + channel : "abs(IN" + channel + ")") + " [V]";
    }

    private void updateTheme() {
        boolean dark = theme.getSelectedIndex() == 1;
        referencePlot.setDarkTheme(dark);
        defectPlot.setDarkTheme(dark);
    }

    private static double[] valuesForMode(double[] input, boolean signed) {
        if (signed) {
            return input;
        }
        double[] out = input.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.abs(out[i]);
        }
        return out;
    }

    private void updateTriggerLines() {
        double value = triggerLevel();
        referencePlot.setTrigger(value);
        defectPlot.setTrigger(value);
    }

    private static TriggerProposal proposeTrigger(double[] referenceRaw, double[] defectRaw, boolean signed) {
        double[] reference = valuesForMode(referenceRaw, signed);
        double[] defect = valuesForMode(defectRaw, signed);
        double refMedian = median(reference);
        double refSigma = madSigma(reference, refMedian);

        if (signed) {
            double refP995 = percentile(reference, 99.5);
            double refP005 = percentile(reference, 0.5);
            double posNoise = Math.max(refP995, refMedian + 6.0 * refSigma);
            double negNoise = Math.min(refP005, refMedian - 6.0 * refSigma);
            double defectP99 = percentile(defect, 99.0);
            double defectP01 = percentile(defect, 1.0);
            double posGap = defectP99 - posNoise;
            double negGap = negNoise - defectP01;

            if (negGap > posGap && negGap > 0.0) {
                return new TriggerProposal(
                        negNoise - 0.35 * negGap,
                        negNoise,
                        defectP01,
                        "negative"
                );
            }
            if (posGap > 0.0) {
                return new TriggerProposal(
                        posNoise + 0.35 * posGap,
                        posNoise,
                        defectP99,
                        "positive"
                );
            }
            double proposed = Math.abs(posNoise) >= Math.abs(negNoise) ? posNoise : negNoise;
            return new TriggerProposal(
                    proposed,
                    proposed,
                    proposed >= 0.0 ? defectP99 : defectP01,
                    "none"
            );
        }

        double refP995 = percentile(reference, 99.5);
        double refNoise = Math.max(refP995, refMedian + 6.0 * refSigma);
        double defectP99 = percentile(defect, 99.0);
        double proposed = defectP99 > refNoise ? refNoise + 0.35 * (defectP99 - refNoise) : refNoise;
        return new TriggerProposal(proposed, refNoise, defectP99, "absolute");
    }

    private static double median(double[] values) {
        return percentile(values, 50.0);
    }

    private static double madSigma(double[] values, double median) {
        double[] dev = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            dev[i] = Math.abs(values[i] - median);
        }
        return 1.4826 * median(dev);
    }

    private static double percentile(double[] values, double pct) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double idx = pct / 100.0 * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted[lo];
        }
        double w = idx - lo;
        return sorted[lo] * (1.0 - w) + sorted[hi] * w;
    }

    private record TriggerProposal(double value, double referenceNoise, double defectLevel, String polarity) {
    }

    private static class PlotPanel extends JPanel {

        private final Color curveColor;
        private double[] t = new double[0];
        private double[] y = new double[0];
        private double trigger;
        private boolean darkTheme;
        private String xLabel = "Time [s]";
        private String yLabel = "Amplitude [V]";

        PlotPanel(Color curveColor) {
            this.curveColor = curveColor;
            setPreferredSize(new Dimension(800, 280));
            setBackground(Color.WHITE);
        }

        void setData(double[] t, double[] y) {
            this.t = t == null ? new double[0] : t;
            this.y = y == null ? new double[0] : y;
            repaint();
        }

        void setTrigger(double trigger) {
            this.trigger = trigger;
            repaint();
        }

        void setDarkTheme(boolean darkTheme) {
            this.darkTheme = darkTheme;
            setBackground(darkTheme ? new Color(18, 18, 18) : Color.WHITE);
            repaint();
        }

        void setLabels(String xLabel, String yLabel) {
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = darkTheme ? new Color(18, 18, 18) : Color.WHITE;
            Color foreground = darkTheme ? new Color(235, 235, 235) : Color.BLACK;
            Color axis = darkTheme ? new Color(150, 150, 150) : Color.GRAY;
            Color grid = darkTheme ? new Color(55, 55, 55) : new Color(225, 225, 225);
            Color labelBackground = darkTheme ? new Color(40, 40, 40) : new Color(255, 255, 230);
            Color triggerColor = new Color(230, 180, 0);

            int left = 78;
            int top = 22;
            int right = 24;
            int bottom = 54;
            int w = Math.max(1, getWidth() - left - right);
            int h = Math.max(1, getHeight() - top - bottom);
            g.setColor(background);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(axis);
            g.drawRect(left, top, w, h);

            if (y.length > 0) {
                double minY = Arrays.stream(y).min().orElse(-1.0);
                double maxY = Arrays.stream(y).max().orElse(1.0);
                minY = Math.min(minY, trigger);
                maxY = Math.max(maxY, trigger);
                if (maxY <= minY) {
                    maxY = minY + 1.0;
                }
                double minT = t.length > 0 ? t[0] : 0.0;
                double maxT = t.length > 0 ? t[t.length - 1] : 1.0;
                if (maxT <= minT) {
                    maxT = minT + 1.0;
                }

                drawTicks(g, left, top, w, h, minT, maxT, minY, maxY, foreground, axis, grid);

                g.setColor(curveColor);
                int lastX = -1;
                int lastY = -1;
                int stride = Math.max(1, y.length / Math.max(1, w * 2));
                for (int i = 0; i < y.length; i += stride) {
                    int x = left + (int) Math.round((t[i] - minT) / (maxT - minT) * w);
                    int yy = top + h - (int) Math.round((y[i] - minY) / (maxY - minY) * h);
                    if (lastX >= 0) {
                        g.drawLine(lastX, lastY, x, yy);
                    }
                    lastX = x;
                    lastY = yy;
                }

                int triggerY = top + h - (int) Math.round((trigger - minY) / (maxY - minY) * h);
                triggerY = Math.max(top, Math.min(top + h, triggerY));
                g.setColor(triggerColor);
                g.drawLine(left, triggerY, left + w, triggerY);
                drawTriggerLabel(g, left, top, w, h, triggerY, trigger, foreground, labelBackground, triggerColor);
            } else {
                drawTicks(g, left, top, w, h, 0.0, 1.0, -1.0, 1.0, foreground, axis, grid);
            }
            drawAxisLabels(g, left, top, w, h, foreground);
            g.dispose();
        }

        private void drawTicks(
                Graphics2D g,
                int left,
                int top,
                int w,
                int h,
                double minT,
                double maxT,
                double minY,
                double maxY,
                Color foreground,
                Color axis,
                Color grid
        ) {
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i <= 5; i++) {
                double ratio = i / 5.0;
                int x = left + (int) Math.round(ratio * w);
                double value = minT + ratio * (maxT - minT);
                g.setColor(grid);
                g.drawLine(x, top, x, top + h);
                g.setColor(axis);
                g.drawLine(x, top + h, x, top + h + 5);
                String text = formatTick(value);
                g.setColor(foreground);
                g.drawString(text, x - fm.stringWidth(text) / 2, top + h + 20);
            }
            for (int i = 0; i <= 5; i++) {
                double ratio = i / 5.0;
                int yy = top + h - (int) Math.round(ratio * h);
                double value = minY + ratio * (maxY - minY);
                g.setColor(grid);
                g.drawLine(left, yy, left + w, yy);
                g.setColor(axis);
                g.drawLine(left - 5, yy, left, yy);
                String text = formatTick(value);
                g.setColor(foreground);
                g.drawString(text, left - 9 - fm.stringWidth(text), yy + fm.getAscent() / 2 - 2);
            }
            g.setColor(axis);
            g.drawRect(left, top, w, h);
        }

        private void drawAxisLabels(Graphics2D g, int left, int top, int w, int h, Color foreground) {
            FontMetrics fm = g.getFontMetrics();
            g.setColor(foreground);
            g.drawString(xLabel, left + w / 2 - fm.stringWidth(xLabel) / 2, getHeight() - 10);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(foreground);
            g2.rotate(-Math.PI / 2);
            g2.drawString(yLabel, -(top + h / 2 + fm.stringWidth(yLabel) / 2), 16);
            g2.dispose();
        }

        private void drawTriggerLabel(
                Graphics2D g,
                int left,
                int top,
                int w,
                int h,
                int triggerY,
                double trigger,
                Color foreground,
                Color labelBackground,
                Color triggerColor
        ) {
            String text = String.format("trigger %.6f V", trigger);
            FontMetrics fm = g.getFontMetrics();
            int labelW = fm.stringWidth(text) + 12;
            int labelH = fm.getHeight() + 4;
            int x = left + w - labelW - 6;
            int yTop = triggerY - labelH - 4;
            if (yTop < top + 2) {
                yTop = triggerY + 4;
            }
            if (yTop + labelH > top + h - 2) {
                yTop = top + h - labelH - 2;
            }
            g.setColor(labelBackground);
            g.fillRect(x, yTop, labelW, labelH);
            g.setColor(triggerColor);
            g.drawRect(x, yTop, labelW, labelH);
            g.setColor(foreground);
            g.drawString(text, x + 6, yTop + fm.getAscent() + 2);
        }

        private static String formatTick(double value) {
            double abs = Math.abs(value);
            if ((abs > 0.0 && abs < 0.001) || abs >= 10_000.0) {
                return String.format("%.2e", value);
            }
            if (abs < 10.0) {
                return String.format("%.4g", value);
            }
            return String.format("%.0f", value);
        }
    }
}
