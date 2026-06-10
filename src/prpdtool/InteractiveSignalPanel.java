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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import pipeline.Buffer;

public class InteractiveSignalPanel extends JPanel {

    private static final int MAX_POINTS = PRPDConstants.SIGNAL_MAX_POINTS;
    private static final int TARGET_POINTS_PER_BUFFER = PRPDConstants.SIGNAL_TARGET_POINTS_PER_BUFFER;
    private static final int LEFT = 110;
    private static final int RIGHT = 18;
    private static final int TOP = 28;
    private static final int BOTTOM = 45;
    private static final int GAP = 45;

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

    // Cursors
    private boolean showCursors = false;
    private double cursorT1 = 0.0;
    private double cursorT2 = 0.0;
    private final double[] cursorY1 = new double[]{0.0, 0.0};
    private final double[] cursorY2 = new double[]{0.0, 0.0};
    private int draggingCursor = 0; // 0=none, 1=T1, 2=T2, 3=Y1_0, 4=Y2_0, 5=Y1_1, 6=Y2_1

    public void setShowCursors(boolean show) {
        this.showCursors = show;
        if (show) {
            cursorT1 = viewTMin + 0.25 * (viewTMax - viewTMin);
            cursorT2 = viewTMin + 0.75 * (viewTMax - viewTMin);
            for (int i = 0; i < 2; i++) {
                cursorY1[i] = viewYMin[i] + 0.25 * (viewYMax[i] - viewYMin[i]);
                cursorY2[i] = viewYMin[i] + 0.75 * (viewYMax[i] - viewYMin[i]);
            }
        }
        repaint();
    }

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
                
                draggingCursor = 0;
                if (showCursors) {
                    Rectangle plot0 = plotBounds(0);
                    Rectangle plot1 = plotBounds(1);
                    int x = e.getX();
                    int y = e.getY();
                    int t1x = timeToX(cursorT1, plot0);
                    int t2x = timeToX(cursorT2, plot0);
                    int distT1 = Math.abs(x - t1x);
                    int distT2 = Math.abs(x - t2x);
                    
                    int plotIdx = plotAt(e.getPoint());
                    if (plotIdx >= 0) {
                        Rectangle plot = plotIdx == 0 ? plot0 : plot1;
                        int y1yRaw = valueToY(cursorY1[plotIdx], plot, plotIdx);
                        int y2yRaw = valueToY(cursorY2[plotIdx], plot, plotIdx);
                        int y1y = Math.max(plot.y, Math.min(plot.y + plot.height, y1yRaw));
                        int y2y = Math.max(plot.y, Math.min(plot.y + plot.height, y2yRaw));
                        int distY1 = Math.abs(y - y1y);
                        int distY2 = Math.abs(y - y2y);
                        
                        if (distT1 < 8 && distT1 <= distT2 && distT1 <= distY1 && distT1 <= distY2) draggingCursor = 1;
                        else if (distT2 < 8 && distT2 <= distY1 && distT2 <= distY2) draggingCursor = 2;
                        else if (distY1 < 8 && distY1 <= distY2) draggingCursor = 3 + plotIdx * 2;
                        else if (distY2 < 8) draggingCursor = 4 + plotIdx * 2;
                    } else {
                        if (distT1 < 8 && distT1 <= distT2) draggingCursor = 1;
                        else if (distT2 < 8) draggingCursor = 2;
                    }
                }
                
                if (draggingCursor == 0) {
                    dragStart = e.getPoint();
                    dragPlot = plotAt(e.getPoint());
                    dragY = e.isShiftDown() || SwingUtilities.isRightMouseButton(e);
                    dragTMin = viewTMin;
                    dragTMax = viewTMax;
                    dragYMin[0] = viewYMin[0];
                    dragYMin[1] = viewYMin[1];
                    dragYMax[0] = viewYMax[0];
                    dragYMax[1] = viewYMax[1];
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingCursor > 0) {
                    Rectangle plot0 = plotBounds(0);
                    if (draggingCursor == 1) cursorT1 = xToTime(e.getX(), plot0);
                    else if (draggingCursor == 2) cursorT2 = xToTime(e.getX(), plot0);
                    else {
                        int pIdx = (draggingCursor - 3) / 2;
                        boolean isY1 = (draggingCursor - 3) % 2 == 0;
                        Rectangle plot = plotBounds(pIdx);
                        double val = yToValue(e.getY(), plot, pIdx);
                        if (isY1) cursorY1[pIdx] = val;
                        else cursorY2[pIdx] = val;
                    }
                    repaint();
                    return;
                }

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

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem saveItem = new JMenuItem("Save Signal as PNG...");
        saveItem.addActionListener(e -> saveAsPng());
        popupMenu.add(saveItem);
        setComponentPopupMenu(popupMenu);
    }

    private void saveAsPng() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            JOptionPane.showMessageDialog(this, "Panel is not visible.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Signal as PNG");
        fc.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }
            try {
                BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = img.createGraphics();
                paint(g2d);
                g2d.dispose();
                ImageIO.write(img, "PNG", file);
                JOptionPane.showMessageDialog(this, "Signal image saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save image: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
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
            int add = (stride == 1) ? buffer.used : ((buffer.used + stride - 1) / stride) * 2;
            ensureCapacity(size + add);
            for (int i = 0; i < buffer.used; i += stride) {
                int end = Math.min(i + stride, buffer.used);
                if (stride == 1) {
                    t[size] = buffer.t[i];
                    upper[size] = upperValues[i];
                    lower[size] = lowerValues[i];
                    size++;
                } else {
                    double uMin = upperValues[i], uMax = upperValues[i];
                    double lMin = lowerValues[i], lMax = lowerValues[i];
                    for (int j = i + 1; j < end; j++) {
                        if (upperValues[j] < uMin) uMin = upperValues[j];
                        if (upperValues[j] > uMax) uMax = upperValues[j];
                        if (lowerValues[j] < lMin) lMin = lowerValues[j];
                        if (lowerValues[j] > lMax) lMax = lowerValues[j];
                    }
                    t[size] = buffer.t[i];
                    upper[size] = uMin;
                    lower[size] = lMin;
                    size++;
                    
                    t[size] = buffer.t[end - 1];
                    upper[size] = uMax;
                    lower[size] = lMax;
                    size++;
                }
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
        if (min > 0.0) {
            min = 0.0;
        }
        if (max < 0.0) {
            max = 0.0;
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
        setBackground(PRPDConstants.isDarkTheme() ? new Color(40, 44, 52) : Color.WHITE);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean isDark = PRPDConstants.isDarkTheme();
        drawPlot(g, 0, upperTitle, upper, isDark ? Color.CYAN : new Color(30, 90, 210));
        drawPlot(g, 1, lowerTitle, lower, isDark ? Color.GREEN : new Color(0, 150, 70));
        drawInteractionHint(g);
        if (showCursors) {
            drawCursors(g);
        }
        g.dispose();
    }

    private void drawCursors(Graphics2D g) {
        boolean isDark = PRPDConstants.isDarkTheme();
        Rectangle plot0 = plotBounds(0);
        Rectangle plot1 = plotBounds(1);
        int topY = plot0.y;
        int bottomY = plot1.y + plot1.height;
        
        int x1 = timeToX(cursorT1, plot0);
        int x2 = timeToX(cursorT2, plot0);
        
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f));
        
        // Draw T1, T2
        g.setColor(new Color(255, 165, 0)); // Orange
        if (x1 >= LEFT && x1 <= getWidth() - RIGHT) g.drawLine(x1, topY, x1, bottomY);
        g.setColor(new Color(255, 215, 0)); // Gold
        if (x2 >= LEFT && x2 <= getWidth() - RIGHT) g.drawLine(x2, topY, x2, bottomY);
        
        // Draw Y1, Y2
        for (int p = 0; p < 2; p++) {
            Rectangle plot = p == 0 ? plot0 : plot1;
            int y1Raw = valueToY(cursorY1[p], plot, p);
            int y2Raw = valueToY(cursorY2[p], plot, p);
            
            int y1 = Math.max(plot.y, Math.min(plot.y + plot.height, y1Raw));
            int y2 = Math.max(plot.y, Math.min(plot.y + plot.height, y2Raw));
            
            g.setColor(new Color(255, 100, 100)); // Red-ish
            g.drawLine(LEFT, y1, getWidth() - RIGHT, y1);
            g.setColor(new Color(255, 150, 150)); // Light red
            g.drawLine(LEFT, y2, getWidth() - RIGHT, y2);
        }
        
        // Draw info box
        g.setStroke(new BasicStroke(1.0f));
        int boxW = 160;
        int boxH = 205;
        int boxX = getWidth() - RIGHT - boxW - 10;
        int boxY = TOP + 10;
        
        g.setColor(isDark ? new Color(30, 30, 30, 200) : new Color(255, 255, 255, 200));
        g.fillRect(boxX, boxY, boxW, boxH);
        g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
        g.drawRect(boxX, boxY, boxW, boxH);
        
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        double dt = Math.abs(cursorT2 - cursorT1);
        double freq = dt > 0 ? 1.0 / dt : 0;
        
        int ty = boxY + 15;
        g.drawString(String.format("T1: %.6f s", cursorT1), boxX + 10, ty); ty += 15;
        g.drawString(String.format("T2: %.6f s", cursorT2), boxX + 10, ty); ty += 15;
        g.drawString(String.format("ΔT: %.6f s", dt), boxX + 10, ty); ty += 15;
        g.drawString(String.format("f : %.1f Hz", freq), boxX + 10, ty); ty += 25;
        
        double dy0 = Math.abs(cursorY2[0] - cursorY1[0]);
        double dy1 = Math.abs(cursorY2[1] - cursorY1[1]);
        g.drawString("Upper Plot:", boxX + 10, ty); ty += 15;
        g.drawString(" Y1: " + formatTick(cursorY1[0]), boxX + 10, ty); ty += 15;
        g.drawString(" Y2: " + formatTick(cursorY2[0]), boxX + 10, ty); ty += 15;
        g.drawString(" ΔY: " + formatTick(dy0), boxX + 10, ty); ty += 20;
        
        g.drawString("Lower Plot:", boxX + 10, ty); ty += 15;
        g.drawString(" Y1: " + formatTick(cursorY1[1]), boxX + 10, ty); ty += 15;
        g.drawString(" Y2: " + formatTick(cursorY2[1]), boxX + 10, ty); ty += 15;
        g.drawString(" ΔY: " + formatTick(dy1), boxX + 10, ty);
    }

    private void drawPlot(Graphics2D g, int plotIndex, String title, double[] values, Color color) {
        boolean isDark = PRPDConstants.isDarkTheme();
        Rectangle plot = plotBounds(plotIndex);
        g.setColor(isDark ? new Color(30, 30, 30) : Color.WHITE);
        g.fillRect(plot.x, plot.y, plot.width, plot.height);
        drawGrid(g, plot, plotIndex);
        g.setColor(isDark ? Color.GRAY : Color.BLACK);
        g.drawRect(plot.x, plot.y, plot.width, plot.height);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
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
        boolean isDark = PRPDConstants.isDarkTheme();
        FontMetrics fm = g.getFontMetrics();
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        for (int i = 0; i <= 5; i++) {
            double r = i / 5.0;
            int x = plot.x + (int) Math.round(r * plot.width);
            double value = viewTMin + r * (viewTMax - viewTMin);
            g.setColor(isDark ? new Color(60, 60, 60) : new Color(230, 230, 230));
            g.drawLine(x, plot.y, x, plot.y + plot.height);
            g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
            String text = formatTick(value);
            g.drawString(text, x - fm.stringWidth(text) / 2, plot.y + plot.height + 17);
        }
        for (int i = 0; i <= 4; i++) {
            double r = i / 4.0;
            int y = plot.y + plot.height - (int) Math.round(r * plot.height);
            double value = viewYMin[plotIndex] + r * (viewYMax[plotIndex] - viewYMin[plotIndex]);
            g.setColor(isDark ? new Color(60, 60, 60) : new Color(230, 230, 230));
            g.drawLine(plot.x, y, plot.x + plot.width, y);
            
            boolean skipLabel = false;
            if (viewYMin[plotIndex] < 0 && viewYMax[plotIndex] > 0) {
                double range = viewYMax[plotIndex] - viewYMin[plotIndex];
                if (Math.abs(value) < range * 0.05) {
                    skipLabel = true;
                }
            }
            if (!skipLabel) {
                g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
                String text = formatTick(value);
                g.drawString(text, plot.x - fm.stringWidth(text) - 8, y + 4);
            }
        }
        
        if (viewYMin[plotIndex] <= 0.0 && viewYMax[plotIndex] >= 0.0) {
            int y0 = plot.y + plot.height - (int) Math.round((0.0 - viewYMin[plotIndex]) / (viewYMax[plotIndex] - viewYMin[plotIndex]) * plot.height);
            g.setColor(new Color(255, 140, 0));
            g.drawLine(plot.x, y0, plot.x + plot.width, y0);
            g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
            String text = "0";
            g.drawString(text, plot.x - fm.stringWidth(text) - 8, y0 + 4);
        }
    }

    private void drawAxisLabels(Graphics2D g, Rectangle plot, int plotIndex) {
        boolean isDark = PRPDConstants.isDarkTheme();
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        String xLabel = "Time [s]";
        g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
        g.drawString(xLabel, plot.x + plot.width / 2 - fm.stringWidth(xLabel) / 2, plot.y + plot.height + 34);
        String yLabel = "Amplitude [ADC]";
        Graphics2D copy = (Graphics2D) g.create();
        copy.rotate(-Math.PI / 2);
        copy.drawString(yLabel, -(plot.y + plot.height / 2 + fm.stringWidth(yLabel) / 2), 22);
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
        if (Math.abs(value) < 1e-12) return "0";
        String s = String.format(java.util.Locale.US, "%.4g", value);
        if (s.contains("e") || s.contains("E")) return s;
        if (s.indexOf('.') > 0) {
            s = s.replaceAll("0*$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private static final class SwingUtilitiesCompat {

        static boolean isRightButton(MouseEvent e) {
            return (e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0 || e.getButton() == MouseEvent.BUTTON3;
        }
    }
}
