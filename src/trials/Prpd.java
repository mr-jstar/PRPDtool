package trials;

/**
 *
 * @author jstar
 */
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class Prpd {

    static class Sample {
        double t, u;
        Sample(double t, double u) {
            this.t = t;
            this.u = u;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage:");
            System.out.println("java PrpdGif input.csv output.gif width height [f0]");
            System.out.println();
            System.out.println("Example:");
            System.out.println("java PrpdGif pd_stream.csv prpd.gif 800 500 50");
            return;
        }

        String input = args[0];
        String output = args[1];
        int width = Integer.parseInt(args[2]);
        int height = Integer.parseInt(args[3]);
        double f0 = args.length >= 5 ? Double.parseDouble(args[4]) : 50.0;

        java.util.List<Sample> samples = readCsv(input);

        if (samples.isEmpty()) {
            throw new RuntimeException("No samples loaded.");
        }

        BufferedImage img = createPrpdImage(samples, width, height, f0);

        ImageIO.write(img, "gif", new File(output));

        System.out.println("Saved: " + output);
        System.out.println("Samples: " + samples.size());
    }

    static java.util.List<Sample> readCsv(String filename) throws IOException {
        java.util.List<Sample> samples = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty())
                    continue;

                if (first) {
                    first = false;
                    if (line.toLowerCase().contains("t") && line.toLowerCase().contains("u")) {
                        continue;
                    }
                }

                String[] parts = line.split("[,;\\s]+");

                if (parts.length < 2)
                    continue;

                double t = Double.parseDouble(parts[0]);
                double u = Double.parseDouble(parts[1]);

                samples.add(new Sample(t, u));
            }
        }

        return samples;
    }

    static BufferedImage createPrpdImage(
            java.util.List<Sample> samples,
            int width,
            int height,
            double f0
    ) {
        int marginLeft = 70;
        int marginRight = 20;
        int marginTop = 30;
        int marginBottom = 55;

        int plotW = width - marginLeft - marginRight;
        int plotH = height - marginTop - marginBottom;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image too small.");
        }

        double uMin = Double.POSITIVE_INFINITY;
        double uMax = Double.NEGATIVE_INFINITY;

        for (Sample s : samples) {
            uMin = Math.min(uMin, s.u);
            uMax = Math.max(uMax, s.u);
        }

        if (uMin == uMax) {
            uMin -= 1.0;
            uMax += 1.0;
        }

        int phaseBins = plotW;
        int ampBins = plotH;

        int[][] hist = new int[phaseBins][ampBins];
        int maxCount = 0;

        for (Sample s : samples) {
            double phase = (360.0 * f0 * s.t) % 360.0;
            if (phase < 0) phase += 360.0;

            int x = (int) Math.floor(phase / 360.0 * phaseBins);
            int y = (int) Math.floor((s.u - uMin) / (uMax - uMin) * ampBins);

            if (x < 0) x = 0;
            if (x >= phaseBins) x = phaseBins - 1;

            if (y < 0) y = 0;
            if (y >= ampBins) y = ampBins - 1;

            hist[x][y]++;
            maxCount = Math.max(maxCount, hist[x][y]);
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // tło wykresu
        g.setColor(Color.BLACK);
        g.fillRect(marginLeft, marginTop, plotW, plotH);

        // histogram PRPD
        for (int x = 0; x < phaseBins; x++) {
            for (int y = 0; y < ampBins; y++) {
                int c = hist[x][y];

                if (c == 0)
                    continue;

                double v = Math.log1p(c) / Math.log1p(maxCount);

                Color color = heatColor(v);

                int px = marginLeft + x;
                int py = marginTop + plotH - 1 - y;

                img.setRGB(px, py, color.getRGB());
            }
        }

        // osie
        g.setColor(Color.BLACK);
        g.drawRect(marginLeft, marginTop, plotW, plotH);

        g.setFont(new Font("Arial", Font.PLAIN, 13));

        // oś X: faza
        for (int deg = 0; deg <= 360; deg += 60) {
            int x = marginLeft + (int) Math.round(deg / 360.0 * plotW);
            g.drawLine(x, marginTop + plotH, x, marginTop + plotH + 5);
            g.drawString(Integer.toString(deg), x - 10, marginTop + plotH + 22);
        }

        // oś Y: amplituda
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

        return img;
    }

    static Color heatColor(double v) {
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
