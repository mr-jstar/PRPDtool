package parallelprpd.v2;

/**
 *
 * @author jstar
 */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class DynamicPRPDHistogram {

    private final int width, height;
    private final int left = 70, right = 25, top = 30, bottom = 55;

    private final int plotW, plotH;
    private final int binsPhase, binsAmp;

    private final double ampMin;
    private final double ampMax;

    private final int[][] hist;
    private int maxCount = 0;

    private final BufferedImage image;

    public DynamicPRPDHistogram(
            int width,
            int height,
            int binsPhase,
            int binsAmp,
            double ampMin,
            double ampMax
    ) {
        this.width = width;
        this.height = height;
        this.binsPhase = binsPhase;
        this.binsAmp = binsAmp;
        this.ampMin = ampMin;
        this.ampMax = ampMax;

        this.plotW = width - left - right;
        this.plotH = height - top - bottom;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image too small");
        }

        this.hist = new int[binsPhase][binsAmp];
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        drawEmpty();
    }

    public BufferedImage getImage() {
        return image;
    }

    public void addPulses(Pulses p) {

        for (int i = 0; i < p.size; i++) {

            double phase = p.phase[i];
            double amp = Math.abs(p.amp[i]);

            int xb = (int) (phase / 360.0 * binsPhase);
            int yb = (int) ((amp - ampMin) / (ampMax - ampMin) * binsAmp);

            if (xb < 0 || xb >= binsPhase) {
                continue;
            }
            if (yb < 0 || yb >= binsAmp) {
                continue;
            }

            hist[xb][yb]++;
            if (hist[xb][yb] > maxCount) {
                maxCount = hist[xb][yb];
            }
        }

        redraw();
    }

    private void drawEmpty() {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.fillRect(left, top, plotW, plotH);

        g.dispose();

        drawAxes();
    }

    private void redraw() {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.BLACK);
        g.fillRect(left, top, plotW, plotH);

        for (int xb = 0; xb < binsPhase; xb++) {
            for (int yb = 0; yb < binsAmp; yb++) {
                int c = hist[xb][yb];
                if (c == 0) {
                    continue;
                }

                double v = Math.log1p(c) / Math.log1p(maxCount);

                int x0 = left + (int) Math.floor(xb * plotW / (double) binsPhase);
                int x1 = left + (int) Math.floor((xb + 1) * plotW / (double) binsPhase);

                int y0 = top + plotH - (int) Math.floor((yb + 1) * plotH / (double) binsAmp);
                int y1 = top + plotH - (int) Math.floor(yb * plotH / (double) binsAmp);

                g.setColor(heatColor(v));
                g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
            }
        }

        g.dispose();
        drawAxes();
    }

    private void drawAxes() {
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // czyść marginesy
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, top);
        g.fillRect(0, top + plotH, width, height - top - plotH);
        g.fillRect(0, top, left, plotH);
        g.fillRect(left + plotW + 1, top, right, plotH);

        g.setColor(Color.BLACK);
        g.drawRect(left, top, plotW, plotH);

        g.setFont(new Font("Arial", Font.PLAIN, 13));

        for (int deg = 0; deg <= 360; deg += 60) {
            int x = left + (int) Math.round(deg / 360.0 * plotW);
            g.drawLine(x, top + plotH, x, top + plotH + 5);
            g.drawString(Integer.toString(deg), x - 10, top + plotH + 22);
        }

        for (int i = 0; i <= 4; i++) {
            double val = ampMin + i * (ampMax - ampMin) / 4.0;
            int y = top + plotH - (int) Math.round(i / 4.0 * plotH);

            g.drawLine(left - 5, y, left, y);
            g.drawString(String.format(Locale.US, "%.3f", val), 5, y + 5);
        }

        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("PRPD", left, 20);

        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString("Phase [deg]", left + plotW / 2 - 40, height - 15);

        g.rotate(-Math.PI / 2);
        g.drawString("|Amplitude|", -top - plotH / 2 - 40, 18);
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
