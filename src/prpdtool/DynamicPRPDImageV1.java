package prpdtool;

/**
 *
 * @author jstar
 */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

public class DynamicPRPDImageV1 {

    private final int width;
    private final int height;
    private final double f0;

    private final int marginLeft = 70;
    private final int marginRight = 20;
    private final int marginTop = 30;
    private final int marginBottom = 55;

    private final int plotW;
    private final int plotH;

    private final int phaseBins;
    private final int ampBins;

    private final int[][] hist;
    private int maxCount = 0;

    private final double uMin;
    private final double uMax;

    private BufferedImage image;

    public DynamicPRPDImageV1(List<Sample> samples, int width, int height, double f0) {
        this.width = width;
        this.height = height;
        this.f0 = f0;

        this.plotW = width - marginLeft - marginRight;
        this.plotH = height - marginTop - marginBottom;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image too small.");
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (PRPDTools.Sample s : samples) {
            min = Math.min(min, s.u);
            max = Math.max(max, s.u);
        }

        if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
            min = -1.0;
            max = 1.0;
        }

        if (min == max) {
            min -= 1.0;
            max += 1.0;
        }

        this.uMin = min;
        this.uMax = max;

        this.phaseBins = plotW;
        this.ampBins = plotH;

        this.hist = new int[phaseBins][ampBins];
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        drawStaticLayout();
        addSamples(samples);
    }

    public BufferedImage getImage(int width, int height) {
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        return image;
    }

    public BufferedImage getImage() {
        return image;
    }

    public void addSamples(List<PRPDTools.Sample> samples) {
        boolean changed = false;

        for (Sample s : samples) {
            int x = phaseBin(s.t);
            int y = ampBin(s.u);

            if (x < 0 || x >= phaseBins || y < 0 || y >= ampBins) {
                continue;
            }

            hist[x][y]++;

            if (hist[x][y] > maxCount) {
                maxCount = hist[x][y];
            }

            changed = true;
        }

        if (changed) {
            redrawHistogramArea();
            drawAxesAndLabels();
        }
    }
    
    public void addSamples(int [][] samples) {
        boolean changed = false;

        for (int i= 0; i < samples.length; i++) {
            int x = samples[i][0];
            int y = samples[i][0];

            if (x < 0 || x >= phaseBins || y < 0 || y >= ampBins) {
                continue;
            }

            hist[x][y]++;

            if (hist[x][y] > maxCount) {
                maxCount = hist[x][y];
            }

            changed = true;
        }

        if (changed) {
            redrawHistogramArea();
            drawAxesAndLabels();
        }
    }

    public void addSample(double t, double u) {
        int x = phaseBin(t);
        int y = ampBin(u);

        if (x < 0 || x >= phaseBins || y < 0 || y >= ampBins) {
            return;
        }

        hist[x][y]++;

        if (hist[x][y] > maxCount) {
            maxCount = hist[x][y];
            redrawHistogramArea();
        } else {
            drawSingleBin(x, y);
        }

        drawAxesAndLabels();
    }

    private int phaseBin(double t) {
        double phase = (360.0 * f0 * t) % 360.0;
        if (phase < 0.0) {
            phase += 360.0;
        }

        int x = (int) Math.floor(phase / 360.0 * phaseBins);

        if (x < 0) {
            x = 0;
        }
        if (x >= phaseBins) {
            x = phaseBins - 1;
        }

        return x;
    }

    private int ampBin(double u) {
        int y = (int) Math.floor((u - uMin) / (uMax - uMin) * ampBins);

        if (y < 0) {
            y = 0;
        }
        if (y >= ampBins) {
            y = ampBins - 1;
        }

        return y;
    }

    private void drawStaticLayout() {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        g.setColor(Color.BLACK);
        g.fillRect(marginLeft, marginTop, plotW, plotH);

        g.dispose();

        drawAxesAndLabels();
    }

    private void redrawHistogramArea() {
        for (int x = 0; x < phaseBins; x++) {
            for (int y = 0; y < ampBins; y++) {
                drawSingleBin(x, y);
            }
        }
    }

    private void drawSingleBin(int x, int y) {
        int c = hist[x][y];

        int px = marginLeft + x;
        int py = marginTop + plotH - 1 - y;

        if (c == 0 || maxCount <= 0) {
            image.setRGB(px, py, Color.BLACK.getRGB());
            return;
        }

        double v = Math.log1p(c) / Math.log1p(maxCount);
        image.setRGB(px, py, heatColor(v).getRGB());
    }

    private void drawAxesAndLabels() {
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        // wyczyść marginesy, ale nie ruszaj pola histogramu
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, marginTop);
        g.fillRect(0, marginTop + plotH, width, height - marginTop - plotH);
        g.fillRect(0, marginTop, marginLeft, plotH);
        g.fillRect(marginLeft + plotW + 1, marginTop, marginRight, plotH);

        g.setColor(Color.BLACK);
        g.drawRect(marginLeft, marginTop, plotW, plotH);

        g.setFont(new Font("Arial", Font.PLAIN, 13));

        for (int deg = 0; deg <= 360; deg += 60) {
            int x = marginLeft + (int) Math.round(deg / 360.0 * plotW);
            g.drawLine(x, marginTop + plotH, x, marginTop + plotH + 5);
            g.drawString(Integer.toString(deg), x - 10, marginTop + plotH + 22);
        }

        for (int i = 0; i <= 4; i++) {
            double val = uMin + i * (uMax - uMin) / 4.0;
            int y = marginTop + plotH - (int) Math.round(i / 4.0 * plotH);

            g.drawLine(marginLeft - 5, y, marginLeft, y);
            g.drawString(String.format(Locale.US, "%.3f", val), 5, y + 5);
        }

        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("PRPD", marginLeft, 20);

        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString("Phase [deg]", marginLeft + plotW / 2 - 40, height - 15);

        g.rotate(-Math.PI / 2);
        g.drawString("Amplitude", -marginTop - plotH / 2 - 30, 18);
        g.rotate(Math.PI / 2);

        g.dispose();
    }

    private static Color heatColor(double v) {
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

        return new Color(r, g, b);
    }
}
