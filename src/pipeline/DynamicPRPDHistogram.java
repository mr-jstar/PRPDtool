package pipeline;

/**
 *
 * @author jstar
 */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Locale;

public class DynamicPRPDHistogram implements PRPDHistogram {

    private int width, height;
    private final int left = 70, right = 25, top = 30, bottom = 55;

    public int[] padding() {
        return new int[]{left + right, top + bottom};
    }

    private final int plotW;
    private final int plotH;
    private final DynamicPRPDHistogramData data;

    private boolean addF0;

    private ArrayList<Label> labels = new ArrayList<>();

    private final BufferedImage image;

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

        drawEmpty();
    }

    @Override
    public void reset() {
        data.reset();
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
        double maxCount = (double) data.getMaxCount();
        int binsPhase = data.getPhaseBins();
        int binsAmp = data.getAmpBins();
        for (int xb = 0; xb < binsPhase; xb++) {
            for (int yb = 0; yb < binsAmp; yb++) {
                int c = data.getBin(xb, yb);
                if (c == 0) {
                    continue;
                }

                // <0,maxCount> -> log -> <0,1>
                double v = Math.log1p(c) / Math.log1p(maxCount);

                int x0 = (int) Math.floor(xb * w / (double) binsPhase);
                int x1 = (int) Math.floor((xb + 1) * w / (double) binsPhase);

                int y0 = h - (int) Math.floor((yb + 1) * h / (double) binsAmp);
                int y1 = h - (int) Math.floor(yb * h / (double) binsAmp);

                g.setColor(heatColor(v));
                g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
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

    @Override
    public void drawF0(boolean doIt) {
        addF0 = doIt;
        redraw();
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;

        redraw();
    }

    @Override
    public void addPulses(Pulses p) {

        data.addPulses(p);

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

    private void drawImg( ) {
        Graphics2D g = image.createGraphics();

        g.setColor(Color.BLACK);
        g.fillRect(left, top, plotW, plotH);

        if (addF0) {
            int yp = top + plotH / 2;
            for (int x = left + 1; x < plotW + left; x++) {
                double ph = 2 * Math.PI * (x - left) / plotW;
                int y = top + plotH / 2 - (int) (plotH / 4 * Math.sin(ph));
                g.setColor(Color.gray);
                g.drawLine(x - 1, yp, x, y);
                yp = y;
            }
        }

        int binsPhase = data.getPhaseBins();
        int binsAmp = data.getAmpBins();

        for (int xb = 0; xb < binsPhase; xb++) {
            for (int yb = 0; yb < binsAmp; yb++) {
                int c = data.getBin(xb, yb);
                if (c == 0) {
                    continue;
                }

                // <0,maxCount> -> log -> <0,1>
                double v = Math.log1p(c) / Math.log1p((double) data.getMaxCount());

                int x0 = left + (int) Math.floor(xb * plotW / (double) binsPhase);
                int x1 = left + (int) Math.floor((xb + 1) * plotW / (double) binsPhase);

                int y0 = top + plotH - (int) Math.floor((yb + 1) * plotH / (double) binsAmp);
                int y1 = top + plotH - (int) Math.floor(yb * plotH / (double) binsAmp);

                g.setColor(heatColor(v));
                g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
            }
        }

        for (Label l : labels) {
            g.setColor(l.getColor());
            g.drawRect(left+l.leftx(plotW), top+l.boty(plotH), l.getW(plotW), l.getH(plotH));
            g.setFont(new Font("Arial", Font.PLAIN, 13));
            g.drawString(l.getLabel(), left+l.leftx(plotW), top+l.boty(plotH));
        }

        g.dispose();
    }

    private void redraw() {
        drawImg();
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

        // Osie
        for (int deg = 0; deg <= 360; deg += 60) {
            int x = left + (int) Math.round(deg / 360.0 * plotW);
            g.drawLine(x, top + plotH, x, top + plotH + 5);
            g.drawString(Integer.toString(deg), x - 10, top + plotH + 22);
        }

        double delta = data.getMax() - data.getMin();
        double min = data.getMin();
        for (int i = 0; i <= 4; i++) {
            double val = min + i * delta / 4.0;
            int y = top + plotH - (int) Math.round(i / 4.0 * plotH);

            g.drawLine(left - 5, y, left, y);
            g.drawString(String.format(Locale.US, "%.3f", val), 25, y + 5);
        }

        // Opis
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("PRPD", left, 20);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString("Phase [deg]", left + plotW / 2 - 40, height - 13);

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
