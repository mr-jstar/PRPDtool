package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;

public class DynamicEnvelopeImage {

    private int width, height;

    private final int left = 70;
    private final int right = 20;
    private final int top = 30;
    private final int bottom = 55;

    private final int plotW;
    private final int plotH;

    private final double tMin;
    private final double tMax;

    private double yMin = 0.0;
    private double yMax = 1.0;

    private final double[] pixMin;
    private final double[] pixMax;
    private final boolean[] hasData;

    private final BufferedImage image;

    public DynamicEnvelopeImage(int width, int height, double tMin, double tMax) {
        if (tMax <= tMin) {
            throw new IllegalArgumentException("tMax must be greater than tMin");
        }

        this.width = width;
        this.height = height;
        this.tMin = tMin;
        this.tMax = tMax;

        this.plotW = width - left - right;
        this.plotH = height - top - bottom;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image too small");
        }

        this.pixMin = new double[plotW];
        this.pixMax = new double[plotW];
        this.hasData = new boolean[plotW];

        Arrays.fill(pixMin, Double.POSITIVE_INFINITY);
        Arrays.fill(pixMax, Double.NEGATIVE_INFINITY);

        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        redrawAll();
    }

    public void resize(int w, int h) {
        this.width = w;
        this.height = h;
        redrawAll();
    }

    public BufferedImage getImage() {
        return image;
    }

    public void addBuffer(Buffer b) {
        if (b == null || b.size == 0) {
            return;
        }

        boolean scaleChanged = false;

        for (int i = 0; i < b.size; i++) {
            double t = b.t[i];
            double u = Math.abs(b.u[i]);

            int x = timeToPixel(t);
            if (x < 0 || x >= plotW) {
                continue;
            }

            if (!hasData[x]) {
                pixMin[x] = u;
                pixMax[x] = u;
                hasData[x] = true;
            } else {
                if (u < pixMin[x]) {
                    pixMin[x] = u;
                }
                if (u > pixMax[x]) {
                    pixMax[x] = u;
                }
            }

            if (u < yMin || u > yMax) {
                expandYRange(u);
                scaleChanged = true;
            }
        }

        if (scaleChanged) {
            redrawAll();
        } else {
            redrawPlotAreaOnly();
            drawAxes();
        }
    }

    private int timeToPixel(double t) {
        double r = (t - tMin) / (tMax - tMin);
        return (int) Math.floor(r * plotW);
    }

    private int yToPixel(double y) {
        double r = (y - yMin) / (yMax - yMin);
        return top + plotH - 1 - (int) Math.round(r * (plotH - 1));
    }

    private void expandYRange(double y) {
        if (y < yMin) {
            yMin = y;
        }
        if (y > yMax) {
            yMax = y;
        }

        double span = yMax - yMin;
        if (span <= 0.0) {
            yMin -= 1.0;
            yMax += 1.0;
            return;
        }

        double margin = 0.10 * span;
        yMin -= margin;
        yMax += margin;
    }

    private void redrawAll() {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.dispose();

        redrawPlotAreaOnly();
        drawAxes();
    }

    private void redrawPlotAreaOnly() {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(left, top, plotW, plotH);

        g.setColor(new Color(230, 230, 230));

        for (int i = 0; i <= 10; i++) {
            int x = left + (int) Math.round(i / 10.0 * plotW);
            g.drawLine(x, top, x, top + plotH);
        }

        for (int i = 0; i <= 5; i++) {
            int y = top + (int) Math.round(i / 5.0 * plotH);
            g.drawLine(left, y, left + plotW, y);
        }

        g.setColor(Color.BLUE);

        for (int x = 0; x < plotW; x++) {
            if (!hasData[x]) {
                continue;
            }

            int px = left + x;
            int y1 = yToPixel(pixMin[x]);
            int y2 = yToPixel(pixMax[x]);

            int ya = Math.min(y1, y2);
            int yb = Math.max(y1, y2);

            image.setRGB(px, ya, Color.BLUE.getRGB());

            /*
            if (ya == yb) {
                image.setRGB(px, ya, Color.BLUE.getRGB());
            } else {
                g.drawLine(px, ya, px, yb);
            }
             */
        }

        g.dispose();
    }

    private void drawAxes() {
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, top);
        g.fillRect(0, top + plotH, width, height - top - plotH);
        g.fillRect(0, top, left, plotH);
        g.fillRect(left + plotW + 1, top, right, plotH);

        g.setColor(Color.BLACK);
        g.drawRect(left, top, plotW, plotH);

        g.setFont(new Font("Arial", Font.PLAIN, 13));

        for (int i = 0; i <= 10; i++) {
            double t = tMin + i * (tMax - tMin) / 10.0;
            int x = left + (int) Math.round(i / 10.0 * plotW);

            g.drawLine(x, top + plotH, x, top + plotH + 5);
            g.drawString(String.format(Locale.US, "%.3f", t),
                    x - 20, top + plotH + 22);
        }

        for (int i = 0; i <= 5; i++) {
            double yVal = yMin + i * (yMax - yMin) / 5.0;
            int y = top + plotH - (int) Math.round(i / 5.0 * plotH);

            g.drawLine(left - 5, y, left, y);
            g.drawString(String.format(Locale.US, "%.3f", yVal),
                    5, y + 5);
        }

        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("Signal envelope", left, 20);

        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString("Time [s]", left + plotW / 2 - 30, height - 15);

        g.rotate(-Math.PI / 2);
        g.drawString("Amplitude", -top - plotH / 2 - 30, 18);
        g.rotate(Math.PI / 2);

        g.dispose();
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }

    public double getTMin() {
        return tMin;
    }

    public double getTMax() {
        return tMax;
    }
}
