package pipeline;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Locale;

public class DynamicPRPDHistogram implements PRPDHistogram {

    private int width, height;
    private final int left = 70, right = 25, top = 30, bottom = 55;

    public int[] padding() {
        return new int[]{left + right, top + bottom};
    }

    private int plotW;
    private int plotH;
    private final DynamicPRPDHistogramData data;

    private Font smallFont = new Font("Arial", Font.PLAIN, 12);
    private Font font = new Font("Arial", Font.PLAIN, 13);
    private Font bigFont = new Font("Arial", Font.BOLD, 16);

    private double zoomY = 1.0;
    private double offsetY = 0.0;
    private double zoomX = 1.0;
    private double offsetX = 0.0;
    
    private double displayThreshold = 0.0;
    private boolean showRawData = false;
    
    private int[] screenHist;

    public double getZoomY() { return zoomY; }
    public void setZoomY(double zoomY) { this.zoomY = zoomY; redraw(); }

    public double getOffsetY() { return offsetY; }
    public void setOffsetY(double offsetY) { this.offsetY = offsetY; redraw(); }

    public double getZoomX() { return zoomX; }
    public void setZoomX(double zoomX) { this.zoomX = zoomX; redraw(); }

    public double getOffsetX() { return offsetX; }
    public void setOffsetX(double offsetX) { this.offsetX = offsetX; redraw(); }
    
    private boolean fastRendering = true;
    public boolean isFastRendering() { return fastRendering; }
    public void setFastRendering(boolean fastRendering) { this.fastRendering = fastRendering; redraw(); }
    
    public void setDisplayThreshold(double displayThreshold) { this.displayThreshold = displayThreshold; redraw(); }
    public void setShowRawData(boolean showRawData) { this.showRawData = showRawData; redraw(); }

    private boolean addF0;

    private ArrayList<Label> labels = new ArrayList<>();

    private BufferedImage image;

    public DynamicPRPDHistogram(
            int width,
            int height,
            int binsPhase,
            int binsAmp,
            double ampMin,
            double ampMax,
            boolean bipolar
    ) {
        this.width = width;
        this.height = height;
        this.data = new DynamicPRPDHistogramData(binsPhase, binsAmp, ampMin, ampMax, bipolar);

        this.plotW = width - left - right;
        this.plotH = height - top - bottom;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image would be too small");
        }

        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        this.screenHist = new int[plotW * plotH];

        drawEmpty();
    }

    public void autoscale() {
        double[] amps = data.getAmps();
        int size = data.getSize();
        if (size == 0) {
            zoomY = 1.0;
            offsetY = 0.0;
            zoomX = 1.0;
            offsetX = 0.0;
            redraw();
            return;
        }

        boolean bipolarHistogram = data.getMin() < 0;
        
        int step = 1;
        if (size > 100000) {
            step = (int) Math.ceil((double) size / 100000.0);
        }
        
        double[] validAmps = new double[size / step + 1];
        int vCount = 0;
        
        for (int i = 0; i < size; i += step) {
            if (!showRawData && Math.abs(amps[i]) < displayThreshold) continue;
            validAmps[vCount++] = amps[i];
        }
        
        if (vCount == 0) {
            zoomY = 1.0;
            offsetY = 0.0;
            zoomX = 1.0;
            offsetX = 0.0;
            redraw();
            return;
        }
        
        java.util.Arrays.sort(validAmps, 0, vCount);
        
        double A_low, A_high;
        if (bipolarHistogram) {
            double maxAbs = 0;
            for (int i = 0; i < vCount; i++) {
                if (Math.abs(validAmps[i]) > maxAbs) maxAbs = Math.abs(validAmps[i]);
            }
            A_low = -maxAbs;
            A_high = maxAbs;
        } else {
            A_low = validAmps[(int)(vCount * 0.005)];
            A_high = validAmps[vCount - 1];
            if (A_low < 0) A_low = 0;
        }
        
        if (A_high <= A_low) {
            A_high = A_low + 1.0;
        }
        
        double baseRange = data.getMax() - data.getMin();
        zoomY = baseRange / (A_high - A_low);
        offsetY = (A_low - data.getMin()) / baseRange * plotH * zoomY;
        
        zoomX = 1.0;
        offsetX = 0.0;
        
        redraw();
    }

    @Override
    public void reset() {
        data.reset();
        zoomX = 1.0;
        zoomY = 1.0;
        offsetX = 0.0;
        offsetY = 0.0;
        drawEmpty();
    }

    @Override
    public void addLabel(double xc, double yc, double w, double h, String label, Color color) {
        labels.add(new Label(xc, yc, w, h, label, color));
    }

    @Override
    public boolean removeLabel(String label) {
        int removed = 0;
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equals(label)) {
                labels.remove(i);
                removed++;
            }
        }
        return removed > 0;
    }

    @Override
    public void clearLabels() {
        labels.clear();
    }

    public BufferedImage getImage() {
        redraw();
        return image;
    }

    @Override
    public BufferedImage getPRPD(int w, int h) {
        BufferedImage prpd = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prpd.createGraphics();
        
        int[][] prpdHist = new int[w][h];
        int maxC = 0;
        double minA = data.getMin();
        double maxA = data.getMax();
        double[] phases = data.getPhases();
        double[] amps = data.getAmps();
        
        for (int i = 0; i < data.getSize(); i++) {
            if (!showRawData && Math.abs(amps[i]) < displayThreshold) continue;
            
            int x = (int) (phases[i] / 360.0 * w);
            int y = (int) (h - (amps[i] - minA) / (maxA - minA) * h);
            if (y == h) y = h - 1;
            if (x == w) x = w - 1;
            
            if (x >= 0 && x < w && y >= 0 && y < h) {
                prpdHist[x][y]++;
                if (prpdHist[x][y] > maxC) maxC = prpdHist[x][y];
            }
        }
        
        if (maxC > 0) {
            double logMaxC = Math.log1p(maxC);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int c = prpdHist[x][y];
                    if (c > 0) {
                        double v = Math.log1p(c) / logMaxC;
                        prpd.setRGB(x, y, heatColorRGB(v));
                    }
                }
            }
        }
        
        g.dispose();
        return prpd;
    }

    @Override
    public double getMin() {
        return data.getMin();
    }

    @Override
    public double getMax() {
        return data.getMax();
    }

    @Override
    public double getDataMin() {
        return data.getDataMin();
    }

    @Override
    public double getDataMax() {
        return data.getDataMax();
    }

    @Override
    public int[][] getHistogram() {
        return data.getHistogram();
    }

    public double[] getPhases() {
        return data.getPhases();
    }

    public double[] getAmps() {
        return data.getAmps();
    }

    public double[] getTimes() {
        return data.getTimes();
    }

    public int getSize() {
        return data.getSize();
    }

    @Override
    public void drawF0(boolean doIt) {
        addF0 = doIt;
        redraw();
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.plotW = width - left - right;
        this.plotH = height - top - bottom;
        if (plotW > 0 && plotH > 0) {
            if (this.image == null || this.image.getWidth() != width || this.image.getHeight() != height) {
                this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
            this.screenHist = new int[plotW * plotH];
            redraw();
        }
    }

    @Override
    public void addPulses(Pulses p) {
        data.addPulses(p);
    }
    
    public void forceRedraw() {
        redraw();
    }

    private void drawEmpty() {
        Graphics2D g = image.createGraphics();
        boolean isDark = prpdtool.PRPDConstants.isDarkTheme();

        g.setColor(isDark ? new Color(40, 44, 52) : Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(isDark ? new Color(30, 30, 30) : Color.BLACK);
        g.fillRect(left, top, plotW, plotH);

        g.dispose();

        drawAxes();
    }

    private void drawImg() {
        Graphics2D g = image.createGraphics();
        boolean isDark = prpdtool.PRPDConstants.isDarkTheme();

        g.setColor(isDark ? new Color(30, 30, 30) : Color.BLACK);
        g.fillRect(left, top, plotW, plotH);
        
        g.setClip(left, top, plotW, plotH);

        if (addF0) {
            Integer last_y = null;
            for (int x = left; x <= left + plotW; x++) {
                double bx = (x - left - offsetX) / zoomX;
                if (bx >= 0 && bx <= plotW) {
                    double ph = 2 * Math.PI * bx / plotW;
                    int y = top + plotH / 2 - (int) (plotH / 3.0 * Math.sin(ph));
                    if (last_y != null) {
                        g.setColor(isDark ? new Color(160, 160, 160) : Color.gray);
                        g.drawLine(x - 1, last_y, x, y);
                    }
                    last_y = y;
                } else {
                    last_y = null;
                }
            }
        }

        java.util.Arrays.fill(screenHist, 0);
        int maxC = 0;
        double minA = data.getMin();
        double maxA = data.getMax();
        double[] phases = data.getPhases();
        double[] amps = data.getAmps();
        int size = data.getSize();
        
        double mulX = (plotW / 360.0) * zoomX;
        double mulY = (maxA > minA) ? ((plotH / (maxA - minA)) * zoomY) : 0;
        double offY = plotH + offsetY + minA * mulY;
        
        if (fastRendering) {
            int step = 1;
            int maxPoints = 250000;
            if (size > maxPoints) {
                step = (int) Math.ceil((double) size / maxPoints);
            }
            
            for (int i = 0; i < size; i += step) {
                double amp = amps[i];
                if (!showRawData && Math.abs(amp) < displayThreshold) continue;
                
                int screenX = (int) (phases[i] * mulX + offsetX);
                if (screenX == plotW) screenX = plotW - 1;
                if (screenX < 0 || screenX >= plotW) continue;
                
                int screenY = (int) (offY - amp * mulY);
                if (screenY == plotH) screenY = plotH - 1;
                if (screenY < 0 || screenY >= plotH) continue;
                
                int idx = screenY * plotW + screenX;
                screenHist[idx]++;
                if (screenHist[idx] > maxC) maxC = screenHist[idx];
            }
        } else {
            for (int i = 0; i < size; i++) {
                double amp = amps[i];
                if (!showRawData && Math.abs(amp) < displayThreshold) continue;
                
                int screenX = (int) (phases[i] * mulX + offsetX);
                if (screenX == plotW) screenX = plotW - 1;
                if (screenX < 0 || screenX >= plotW) continue;
                
                int screenY = (int) (offY - amp * mulY);
                if (screenY == plotH) screenY = plotH - 1;
                if (screenY < 0 || screenY >= plotH) continue;
                
                int idx = screenY * plotW + screenX;
                screenHist[idx]++;
                if (screenHist[idx] > maxC) maxC = screenHist[idx];
            }
        }
        
        if (maxC > 0) {
            int[] colorMap = new int[maxC + 1];
            double logMaxC = Math.log1p(maxC);
            for (int c = 1; c <= maxC; c++) {
                double v = Math.log1p(c) / logMaxC;
                colorMap[c] = heatColorRGB(v);
            }
            
            java.awt.image.DataBuffer db = image.getRaster().getDataBuffer();
            if (db instanceof java.awt.image.DataBufferInt) {
                int[] pixels = ((java.awt.image.DataBufferInt) db).getData();
                for (int y = 0; y < plotH; y++) {
                    int rowOffset = y * plotW;
                    int imgY = top + y;
                    int imgRowOffset = imgY * width;
                    for (int x = 0; x < plotW; x++) {
                        int c = screenHist[rowOffset + x];
                        if (c > 0) {
                            pixels[imgRowOffset + left + x] = colorMap[c];
                        }
                    }
                }
            } else {
                for (int y = 0; y < plotH; y++) {
                    int rowOffset = y * plotW;
                    int imgY = top + y;
                    for (int x = 0; x < plotW; x++) {
                        int c = screenHist[rowOffset + x];
                        if (c > 0) {
                            image.setRGB(left + x, imgY, colorMap[c]);
                        }
                    }
                }
            }
        }

        g.setClip(null);

        for (Label l : labels) {
            g.setColor(l.getColor());
            g.drawRect(left + l.leftx(plotW), top + l.boty(plotH), l.getW(plotW), l.getH(plotH));
            g.setFont(font);
            Rectangle2D rect = g.getFontMetrics(font).getStringBounds(l.getLabel(), g);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g.fillRect(left + l.leftx(plotW), top + l.boty(plotH), (int) rect.getWidth() + 1, (int) rect.getHeight() + 3);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(Color.WHITE);
            g.drawString(l.getLabel(), left + l.leftx(plotW), top + l.boty(plotH) + (int) rect.getHeight());
        }

        g.dispose();
    }

    private void redraw() {
        drawImg();
        drawAxes();
    }

    private void drawAxes() {
        Graphics2D g = image.createGraphics();
        boolean isDark = prpdtool.PRPDConstants.isDarkTheme();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // czyść marginesy
        g.setColor(isDark ? new Color(40, 44, 52) : Color.WHITE);
        g.fillRect(0, 0, width, top);
        g.fillRect(0, top + plotH, width, height - top - plotH);
        g.fillRect(0, top, left, plotH);
        g.fillRect(left + plotW + 1, top, right, plotH);

        g.setColor(isDark ? Color.GRAY : Color.BLACK);
        g.drawRect(left, top, plotW, plotH);

        g.setFont(font);
        g.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);

        // Osie X
        for (int i = 0; i <= 6; i++) {
            int x = left + (int) Math.round(i / 6.0 * plotW);
            double x_old = left + (x - left - offsetX) / zoomX;
            double val = (x_old - left) / (double)plotW * 360.0;
            g.drawLine(x, top + plotH, x, top + plotH + 5);
            g.drawString(String.format(Locale.US, "%.1f", val), x - 15, top + plotH + 22);
        }

        double delta = data.getMax() - data.getMin();
        double min = data.getMin();
        for (int i = 0; i <= 4; i++) {
            int y = top + plotH - (int) Math.round(i / 4.0 * plotH);
            
            double y_old = (top + plotH) + (y - (top + plotH) - offsetY) / zoomY;
            double val = min + ((top + plotH) - y_old) / (double)plotH * delta;

            g.drawLine(left - 5, y, left, y);
            g.drawString(String.format(Locale.US, "%.3f", val), 25, y + 5);
        }

        // Opis
        g.setFont(bigFont);
        g.drawString("PRPD", left, 20);

        g.setFont(smallFont);
        g.drawString("Phase [deg]", left + plotW / 2 - 40, height - 13);

        g.rotate(-Math.PI / 2);
        boolean isBipolar = data.getMin() < 0;
        if (isBipolar) {
            g.drawString("Amplitude [ADC]", -top - plotH / 2 - 45, 18);
        } else {
            g.drawString("|Amplitude| [ADC]", -top - plotH / 2 - 55, 18);
        }
        g.rotate(Math.PI / 2);

        g.dispose();
    }

    private static int heatColorRGB(double v) {
        v = Math.max(0.0, Math.min(1.0, v));

        int r, g, b;

        if (v < 0.25) {
            double k = v / 0.25;
            r = 0;
            g = 0;
            b = (int) (80 + 175 * k);
        } else if (v < 0.50) {
            double k = (v - 0.25) / 0.25;
            r = 0;
            g = (int) (255 * k);
            b = 255;
        } else if (v < 0.75) {
            double k = (v - 0.50) / 0.25;
            r = (int) (255 * k);
            g = 255;
            b = (int) (255 * (1.0 - k));
        } else {
            double k = (v - 0.75) / 0.25;
            r = 255;
            g = (int) (255 * (1.0 - k));
            b = 0;
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public double getPhaseAt(int x, int panelWidth) {
        if (panelWidth <= 0 || width <= 0) return 0;
        int imgX = (int) ((double) x / panelWidth * width);
        double bx = (imgX - left - offsetX) / zoomX;
        return bx / plotW * 360.0;
    }

    @Override
    public double getAmpAt(int y, int panelHeight) {
        if (panelHeight <= 0 || height <= 0) return 0;
        int imgY = (int) ((double) y / panelHeight * height);
        double y_old = (top + plotH) + (imgY - (top + plotH) - offsetY) / zoomY;
        return data.getMin() + ((top + plotH) - y_old) / (double)plotH * (data.getMax() - data.getMin());
    }

    @Override
    public int getXForPhase(double phase, int panelWidth) {
        if (panelWidth <= 0 || width <= 0) return 0;
        double bx = phase / 360.0 * plotW;
        int imgX = (int) Math.round(bx * zoomX + left + offsetX);
        return (int) Math.round((double) imgX / width * panelWidth);
    }

    @Override
    public int getYForAmp(double amp, int panelHeight) {
        if (panelHeight <= 0 || height <= 0) return 0;
        double delta = data.getMax() - data.getMin();
        if (delta == 0) return 0;
        double y_old = (top + plotH) - (amp - data.getMin()) / delta * plotH;
        int imgY = (int) Math.round((y_old - (top + plotH)) * zoomY + offsetY + (top + plotH));
        return (int) Math.round((double) imgY / height * panelHeight);
    }
}
