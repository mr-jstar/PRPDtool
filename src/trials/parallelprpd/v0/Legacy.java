package prpdtool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jstar
 */
public class Legacy {
    public static double[][] extractPulses(
            List<Sample> stream,
            double f0,
            double threshold,
            double deadUs,
            double smoothUs
    ) {
        if (stream == null || stream.size() < 3 || stream.get(0).s.length != 2) {
            return new double[0][3];
        }

        int n = stream.size();

        double[] t = new double[n];
        double[] u = new double[n];

        for (int i = 0; i < n; i++) {
            t[i] = stream.get(i).s[0];
            u[i] = stream.get(i).s[1];
        }

        double fs = estimateFs(t);

        int smoothN = Math.max(3, (int) Math.round(smoothUs * 1e-6 * fs));
        int deadN = Math.max(1, (int) Math.round(deadUs * 1e-6 * fs));

        double[] background = movingAverageCentered(u, smoothN);
        double[] x = new double[n];

        for (int i = 0; i < n; i++) {
            x[i] = u[i] - background[i];
        }

        List<double[]> pulses = new ArrayList<>();

        int i = 1;

        while (i < n - 1) {
            if (Math.abs(x[i]) >= threshold) {
                int j0 = i;
                int j1 = Math.min(n, i + deadN);

                int best = j0;
                double bestAbs = Math.abs(x[j0]);

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(x[j]);
                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double amp = x[best];
                double phase = normalizePhase(360.0 * f0 * t[best]);

                pulses.add(new double[]{t[best], phase, amp});

                i = j1;
            } else {
                i++;
            }
        }

        double[][] out = new double[pulses.size()][3];

        for (int k = 0; k < pulses.size(); k++) {
            out[k] = pulses.get(k);
        }

        return out;
    }

    private static double normalizePhase(double phase) {
        phase %= 360.0;
        if (phase < 0.0) {
            phase += 360.0;
        }
        return phase;
    }

    private static double[] movingAverageCentered(double[] x, int win) {
        int n = x.length;
        double[] y = new double[n];

        if (win <= 1) {
            System.arraycopy(x, 0, y, 0, n);
            return y;
        }

        // prefix sums: ps[i] = suma x[0..i-1]
        double[] ps = new double[n + 1];
        ps[0] = 0.0;

        for (int i = 0; i < n; i++) {
            ps[i + 1] = ps[i] + x[i];
        }

        int half = win / 2;

        for (int i = 0; i < n; i++) {
            int left = i - half;
            int right = i + half;

            // przy parzystym oknie: lekko asymetryczne (standardowe podejście)
            if (win % 2 == 0) {
                right -= 1;
            }

            if (left < 0) {
                left = 0;
            }
            if (right >= n) {
                right = n - 1;
            }

            int count = right - left + 1;

            double sum = ps[right + 1] - ps[left];
            y[i] = sum / count;
        }

        return y;
    }

    private static double estimateFs(double[] t) {
        if (t.length < 2) {
            throw new IllegalArgumentException("Too few samples");
        }

        double sum = 0.0;
        int count = 0;

        for (int i = 1; i < t.length; i++) {
            double dt = t[i] - t[i - 1];
            if (dt > 0.0) {
                sum += dt;
                count++;
            }
        }

        if (count == 0) {
            throw new IllegalArgumentException("Cannot estimate sampling frequency");
        }

        return 1.0 / (sum / count);
    }

    public static java.util.List<Sample> readCsv(String filename) throws IOException {
        java.util.List<Sample> samples = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            String[] parts = line.trim().split("[,;\\s]+");
            int lgt = parts.length;

            if (lgt < 2 || lgt > 3) {
                throw new IOException(" bad file content -- the number of columns should be 2 or 3");
            }

            while ((line = br.readLine()) != null) {
                parts = line.trim().split("[,;\\s]+");

                if (parts.length != lgt) {
                    continue;
                }

                try {
                    double[] s = new double[lgt];
                    for (int i = 0; i < s.length; i++) {
                        s[i] = Double.parseDouble(parts[i]);
                    }

                    samples.add(new Sample(s));
                } catch (NumberFormatException e) {
                    continue;
                }
            }

            return samples;
        }
    }

    public static void readCsvParallel(String filename, List<Sample> samples) throws IOException {
        //? samples.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            String[] parts = line.trim().split("[,;\\s]+");
            int lgt = parts.length;

            if (lgt < 2 || lgt > 3) {
                throw new IOException(" bad file content -- the number of columns should be 2 or 3");
            }

            while ((line = br.readLine()) != null) {
                parts = line.trim().split("[,;\\s]+");

                if (parts.length != lgt) {
                    continue;
                }

                try {
                    double[] s = new double[lgt];
                    for (int i = 0; i < s.length; i++) {
                        s[i] = Double.parseDouble(parts[i]);
                    }

                    samples.add(new Sample(s));
                } catch (NumberFormatException e) {
                    continue;
                }
            }
        }
    }
}
