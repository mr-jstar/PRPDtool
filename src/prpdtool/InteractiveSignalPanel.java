package prpdtool;

import dsp.Filter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import javax.swing.JPanel;
import pipeline.Buffer;

public class InteractiveSignalPanel extends JPanel {

    private static final int MAX_POINTS = 700_000;
    private static final int TARGET_POINTS_PER_BUFFER = 25_000;
    private static final int LEFT = 72;
    private static final int RIGHT = 18;
    private static final int TOP = 28;
    private static final int BOTTOM = 45;
    private static final int GAP = 18;

    private String upperTitle = "Signal envelope";
    private String lowerTitle = "Filtered signal";
    private Filter upperFilter;
    private Filter lowerFilter;
    private boolean liveMode;

    private double[] t = new double[16_384];
    private double[] upper = new double[16_384];
    private double[] lower = new double[16_384];
    private int size;

    private boolean autoX = true;
    private final boolean[] autoY = {true, true};
    private double viewTMin;
    private double viewTMax = 1.0;
    private final double[] viewYMin = {-1.0, -1.0};
    private final double[] viewYMax = {1.0, 1.0};

    private Point dragStart;
    private double dragTMin;
    private double dragTMax;
    private final double[] dragYMin = new double[2];
    private final double[] dragYMax = new double[2];
    private int dragPlot = -1;
    private boolean dragY;

    public InteractiveSignalPanel() {
        setBackground(Color.WHITE);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    resetView();
                    return;
                }
                dragStart = e.getPoint();
                dragPlot = plotAt(e.getPoint());
                dragY = e.isShiftDown() || SwingUtilitiesCompat.isRightButton(e);
                dragTMin = viewTMin;
                dragTMax = viewTMax;
                dragYMin[0] = viewYMin[0];
                dragYMin[1] = viewYMin[1];
                dragYMax[0] = viewYMax[0];
                dragYMax[1] = viewYMax[1];
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null || size == 0) {
                    return;
                }
                Rectangle plot = plotBounds(Math.max(0, dragPlot));
                double xSpan = dragTMax - dragTMin;
                double dx = (e.getX() - dragStart.x) * xSpan / Math.max(1, plot.width);
                if (!dragY) {
                    viewTMin = dragTMin - dx;
                    viewTMax = dragTMax - dx;
                    clampXView();
                    autoX = false;
                }

                if (dragPlot >= 0) {
                    double span = dragYMax[dragPlot] - dragYMin[dragPlot];
                    double dy = (e.getY() - dragStart.y) * span / Math.max(1, plot.height);
                    viewYMin[dragPlot] = dragYMin[dragPlot] + dy;
                    viewYMax[dragPlot] = dragYMax[dragPlot] + dy;
                    autoY[dragPlot] = false;
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                dragPlot = -1;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (size == 0) {
                    return;
                }
                int plotIndex = plotAt(e.getPoint());
                double factor = Math.pow(1.18, e.getPreciseWheelRotation());
                if (e.isShiftDown() && plotIndex >= 0) {
                    zoomY(plotIndex, e.getY(), factor);
                } else {
                    zoomX(e.getX(), factor);
                }
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void reset(String upperTitle, Filter upperFilter, String lowerTitle, Filter lowerFilter, boolean liveMode) {
        this.upperTitle = upperTitle;
        this.upperFilter = upperFilter;
        this.lowerTitle = lowerTitle;
        this.lowerFilter = lowerFilter;
        this.liveMode = liveMode;
        this.size = 0;
        resetView();
    }

    public void resetView() {
        autoX = true;
        autoY[0] = true;
        autoY[1] = true;
        updateAutoRanges();
        repaint();
    }

    public void addBuffer(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        try {
            if (buffer.used <= 0) {
                return;
            }
            double[] upperValues = upperFilter == null ? copy(buffer.u, buffer.used) : upperFilter.filter(buffer.u, buffer.used);
            double[] lowerValues = lowerFilter == null ? copy(buffer.u, buffer.used) : lowerFilter.filter(buffer.u, buffer.used);
            int stride = Math.max(1, buffer.used / TARGET_POINTS_PER_BUFFER);
            int add = (buffer.used + stride - 1) / stride;
            ensureCapacity(size + add);
            for (int i = 0; i < buffer.used; i += stride) {
                t[size] = buffer.t[i];
                upper[size] = upperValues[i];
                lower[size] = lowerValues[i];
                size++;
            }
            trimIfNeeded();
            updateAutoRanges();
            repaint();
        } finally {
            buffer.release();
            buffer.release();
        }
    }

    private static double[] copy(double[] input, int n) {
        return Arrays.copyOf(input, n);
    }

    private void ensureCapacity(int needed) {
        if (needed <= t.length) {
            return;
        }
        int next = t.length;
        while (next < needed) {
            next *= 2;
        }
        t = Arrays.copyOf(t, next);
        upper = Arrays.copyOf(upper, next);
        lower = Arrays.copyOf(lower, next);
    }

    private void trimIfNeeded() {
        if (size <= MAX_POINTS) {
            return;
        }
        int drop = size - MAX_POINTS;
        System.arraycopy(t, drop, t, 0, MAX_POINTS);
        System.arraycopy(upper, drop, upper, 0, MAX_POINTS);
        System.arraycopy(lower, drop, lower, 0, MAX_POINTS);
        size = MAX_POINTS;
        if (liveMode) {
            clampXView();
        }
    }

    private void updateAutoRanges() {
        if (size == 0) {
            viewTMin = 0.0;
            viewTMax = 1.0;
            viewYMin[0] = -1.0;
            viewYMax[0] = 1.0;
            viewYMin[1] = -1.0;
            viewYMax[1] = 1.0;
            return;
        }
        if (autoX) {
            viewTMin = t[0];
            viewTMax = t[size - 1];
            if (viewTMax <= viewTMin) {
                viewTMax = viewTMin + 1.0;
            }
        }
        if (autoY[0]) {
            fitY(0);
        }
        if (autoY[1]) {
            fitY(1);
        }
    }

    private void fitY(int plot) {
        double[] values = plot == 0 ? upper : lower;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) {
            if (t[i] < viewTMin || t[i] > viewTMax) {
                continue;
            }
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = -1.0;
            max = 1.0;
        }
        if (max <= min) {
            max = min + 1.0;
        }
        double margin = 0.08 * (max - min);
        viewYMin[plot] = min - margin;
        viewYMax[plot] = max + margin;
    }

    private void zoomX(int mouseX, double factor) {
        Rectangle plot = plotBounds(0);
        double anchor = xToTime(mouseX, plot);
        double leftFrac = (anchor - viewTMin) / Math.max(1e-12, viewTMax - viewTMin);
        double newSpan = (viewTMax - viewTMin) * factor;
        double minSpan = Math.max(1e-9, (t[size - 1] - t[0]) / 1_000_000.0);
        newSpan = Math.max(minSpan, newSpan);
        viewTMin = anchor - leftFrac * newSpan;
        viewTMax = viewTMin + newSpan;
        clampXView();
        autoX = false;
        autoY[0] = false;
        autoY[1] = false;
    }

    private void zoomY(int plotIndex, int mouseY, double factor) {
        Rectangle plot = plotBounds(plotIndex);
        double anchor = yToValue(mouseY, plot, plotIndex);
        double bottomFrac = (anchor - viewYMin[plotIndex]) / Math.max(1e-12, viewYMax[plotIndex] - viewYMin[plotIndex]);
        double newSpan = (viewYMax[plotIndex] - viewYMin[plotIndex]) * factor;
        newSpan = Math.max(1e-12, newSpan);
        viewYMin[plotIndex] = anchor - bottomFrac * newSpan;
        viewYMax[plotIndex] = viewYMin[plotIndex] + newSpan;
        autoY[plotIndex] = false;
    }

    private void clampXView() {
        if (size == 0) {
            return;
        }
        double min = t[0];
        double max = t[size - 1];
        double span = viewTMax - viewTMin;
        if (span >= max - min) {
            viewTMin = min;
            viewTMax = max > min ? max : min + 1.0;
            return;
        }
        if (viewTMin < min) {
            viewTMin = min;
            viewTMax = min + span;
        }
        if (viewTMax > max) {
            viewTMax = max;
            viewTMin = max - span;
        }
    }

    private int plotAt(Point p) {
        Rectangle upperPlot = plotBounds(0);
        Rectangle lowerPlot = plotBounds(1);
        if (upperPlot.contains(p)) {
            return 0;
        }
        if (lowerPlot.contains(p)) {
            return 1;
        }
        return p.y < getHeight() / 2 ? 0 : 1;
    }

    private Rectangle plotBounds(int plot) {
        int availableH = Math.max(1, getHeight() - TOP - BOTTOM - GAP);
        int plotH = availableH / 2;
        int y = TOP + (plot == 0 ? 0 : plotH + GAP);
        return new Rectangle(LEFT, y, Math.max(1, getWidth() - LEFT - RIGHT), Math.max(1, plotH));
    }

    private double xToTime(int x, Rectangle plot) {
        double r = (x - plot.x) / (double) Math.max(1, plot.width);
        r = Math.max(0.0, Math.min(1.0, r));
        return viewTMin + r * (viewTMax - viewTMin);
    }

    private int timeToX(double value, Rectangle plot) {
        return plot.x + (int) Math.round((value - viewTMin) / (viewTMax - viewTMin) * plot.width);
    }

    private double yToValue(int y, Rectangle plot, int plotIndex) {
        double r = (plot.y + plot.height - y) / (double) Math.max(1, plot.height);
        r = Math.max(0.0, Math.min(1.0, r));
        return viewYMin[plotIndex] + r * (viewYMax[plotIndex] - viewYMin[plotIndex]);
    }

    private int valueToY(double value, Rectangle plot, int plotIndex) {
        return plot.y + plot.height - (int) Math.round((value - viewYMin[plotIndex]) / (viewYMax[plotIndex] - viewYMin[plotIndex]) * plot.height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawPlot(g, 0, upperTitle, upper, new Color(30, 90, 210));
        drawPlot(g, 1, lowerTitle, lower, new Color(0, 150, 70));
        drawInteractionHint(g);
        g.dispose();
    }

    private void drawPlot(Graphics2D g, int plotIndex, String title, double[] values, Color color) {
        Rectangle plot = plotBounds(plotIndex);
        g.setColor(Color.WHITE);
        g.fillRect(plot.x, plot.y, plot.width, plot.height);
        drawGrid(g, plot, plotIndex);
        g.setColor(Color.BLACK);
        g.drawRect(plot.x, plot.y, plot.width, plot.height);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString(title, plot.x, plot.y - 8);

        if (size > 1) {
            Shape oldClip = g.getClip();
            g.clipRect(plot.x + 1, plot.y + 1, Math.max(1, plot.width - 1), Math.max(1, plot.height - 1));
            g.setColor(color);
            g.setStroke(new BasicStroke(1.2f));
            int lastX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int i = 0; i < size; i++) {
                if (t[i] < viewTMin || t[i] > viewTMax) {
                    continue;
                }
                int x = timeToX(t[i], plot);
                int y = valueToY(values[i], plot, plotIndex);
                if (x == lastX) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                } else {
                    if (lastX != Integer.MIN_VALUE) {
                        g.drawLine(lastX, minY, lastX, maxY);
                    }
                    lastX = x;
                    minY = y;
                    maxY = y;
                }
            }
            if (lastX != Integer.MIN_VALUE) {
                g.drawLine(lastX, minY, lastX, maxY);
            }
            g.setClip(oldClip);
        }
        drawAxisLabels(g, plot, plotIndex);
    }

    private void drawGrid(Graphics2D g, Rectangle plot, int plotIndex) {
        FontMetrics fm = g.getFontMetrics();
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        for (int i = 0; i <= 5; i++) {
            double r = i / 5.0;
            int x = plot.x + (int) Math.round(r * plot.width);
            double value = viewTMin + r * (viewTMax - viewTMin);
            g.setColor(new Color(230, 230, 230));
            g.drawLine(x, plot.y, x, plot.y + plot.height);
            g.setColor(Color.BLACK);
            String text = formatTick(value);
            g.drawString(text, x - fm.stringWidth(text) / 2, plot.y + plot.height + 17);
        }
        for (int i = 0; i <= 4; i++) {
            double r = i / 4.0;
            int y = plot.y + plot.height - (int) Math.round(r * plot.height);
            double value = viewYMin[plotIndex] + r * (viewYMax[plotIndex] - viewYMin[plotIndex]);
            g.setColor(new Color(230, 230, 230));
            g.drawLine(plot.x, y, plot.x + plot.width, y);
            g.setColor(Color.BLACK);
            String text = formatTick(value);
            g.drawString(text, plot.x - fm.stringWidth(text) - 8, y + 4);
        }
    }

    private void drawAxisLabels(Graphics2D g, Rectangle plot, int plotIndex) {
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        String xLabel = "Time [s]";
        g.setColor(Color.BLACK);
        g.drawString(xLabel, plot.x + plot.width / 2 - fm.stringWidth(xLabel) / 2, plot.y + plot.height + 34);
        String yLabel = "Amplitude";
        Graphics2D copy = (Graphics2D) g.create();
        copy.rotate(-Math.PI / 2);
        copy.drawString(yLabel, -(plot.y + plot.height / 2 + fm.stringWidth(yLabel) / 2), 18);
        copy.dispose();
    }

    private void drawInteractionHint(Graphics2D g) {
        String text = "Wheel: zoom time | Drag: pan time + active Y | Shift+wheel: zoom Y | Shift/right drag: pan Y | Double click: reset";
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(80, 80, 80));
        g.drawString(text, Math.max(4, getWidth() - fm.stringWidth(text) - 8), getHeight() - 6);
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

    private static final class SwingUtilitiesCompat {

        static boolean isRightButton(MouseEvent e) {
            return (e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0 || e.getButton() == MouseEvent.BUTTON3;
        }
    }
}
