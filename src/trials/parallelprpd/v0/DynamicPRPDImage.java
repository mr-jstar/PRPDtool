package prpdtool;


/**
 *
 * @author jstar
 */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class DynamicPRPDImage {

    private final int width, height;
    private final int marginLeft = 70;
    private final int marginRight = 20;
    private final int marginTop = 30;
    private final int marginBottom = 55;

    private final int plotW, plotH;
    private final int phaseBins, ampBins;

    private final double uMin, uMax;

    private final int[][] hist;
    private int maxCount = 0;

    private final BufferedImage image;

    public DynamicPRPDImage(double[][] pulses, int width, int height) {
        this.width = width;
        this.height = height;

        this.plotW = width - marginLeft - marginRight;
        this.plotH = height - marginTop - marginBottom;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image too small.");
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (double[] p : pulses) {
            double a = p[2];              // albo Math.abs(p[2]), jeśli chcesz PRPD modułowe
            min = Math.min(min, a);
            max = Math.max(max, a);
        }

        if (!Double.isFinite(min) || !Double.isFinite(max)) {
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

        drawBase();
        addSamples(pulses);
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getWidth() {
        return image.getWidth();
    }
       
    public int getHeight() {
        return image.getHeight();
    }

    public void addSamples(double[][] pulses) {
        boolean maxChanged = false;

        for (double[] p : pulses) {
            if (p == null || p.length < 3) {
                continue;
            }

            double phase = p[1];
            double amp = p[2];            // albo Math.abs(p[2])

            int x = phaseBin(phase);
            int y = ampBin(amp);

            if (x < 0 || x >= phaseBins || y < 0 || y >= ampBins) {
                continue;
            }

            hist[x][y]++;

            if (hist[x][y] > maxCount) {
                maxCount = hist[x][y];
                maxChanged = true;
            }
        }

        if (maxChanged) {
            redrawHistogram();
        } else {
            redrawHistogram(); // prościej; można optymalizować do zmienionych binów
        }

        drawAxesAndLabels();
    }

    private int phaseBin(double phase) {
        phase %= 360.0;
        if (phase < 0) {
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

    private int ampBin(double amp) {
        int y = (int) Math.floor((amp - uMin) / (uMax - uMin) * ampBins);

        if (y < 0) {
            y = 0;
        }
        if (y >= ampBins) {
            y = ampBins - 1;
        }

        return y;
    }

    private void drawBase() {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.fillRect(marginLeft, marginTop, plotW, plotH);

        g.dispose();
    }

    private void redrawHistogram() {
        for (int x = 0; x < phaseBins; x++) {
            for (int y = 0; y < ampBins; y++) {
                int c = hist[x][y];

                int px = marginLeft + x;
                int py = marginTop + plotH - 1 - y;

                if (c == 0 || maxCount <= 0) {
                    image.setRGB(px, py, Color.BLACK.getRGB());
                    continue;
                }

                double v = Math.log1p(c) / Math.log1p(maxCount);
                image.setRGB(px, py, heatColor(v).getRGB());
            }
        }
    }

    private void drawAxesAndLabels() {
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // czyścimy tylko marginesy
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
