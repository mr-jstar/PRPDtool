package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class DynamicPRPDHistogramData implements PRPDHistogram {

    private int binsPhase, binsAmp;

    private final double ampMin;
    private final double ampMax;

    private final int[][] hist;
    private int maxCount = 0;

    private boolean bipolar;

    public DynamicPRPDHistogramData(
            int binsPhase,
            int binsAmp,
            double ampMin,
            double ampMax,
            boolean bipolar
    ) {
        this.binsPhase = binsPhase;
        this.binsAmp = binsAmp;
        this.ampMin = ampMin;
        this.ampMax = ampMax;
        this.bipolar = bipolar;

        this.hist = new int[binsPhase][binsAmp];
    }

    @Override
    public void reset() {
        for (int i = 0; i < binsPhase; i++) {
            Arrays.fill(hist[i], 0);
        }
    }

    @Override
    public BufferedImage getPRPD(int w, int h) {
        BufferedImage prpd = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prpd.createGraphics();
        for (int xb = 0; xb < binsPhase; xb++) {
            for (int yb = 0; yb < binsAmp; yb++) {
                int c = hist[xb][yb];
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
        return ampMin;
    }

    @Override
    public double getMax() {
        return ampMax;
    }

    @Override
    public int[][] getHistogram() {
        return hist;
    }

    @Override
    public void addPulses(Pulses p) {

        for (int i = 0; i < p.n; i++) {

            double phase = p.phase[i];
            double amp = bipolar ? p.amp[i] : Math.abs(p.amp[i]);

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

    public int getMaxCount() {
        return maxCount;
    }

    public int getPhaseBins() {
        return binsPhase;
    }

    public int getAmpBins() {
        return binsAmp;
    }

    public int getBin(int ph, int amp) {
        return hist[ph][amp];
    }
}
