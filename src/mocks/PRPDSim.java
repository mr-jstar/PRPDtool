package mocks;

/**
 *
 * @author jstar
 */
import java.util.Random;

public class PRPDSim {

    public enum Defect {
        VOID,
        SURFACE,
        CORONA_POSITIVE,
        CORONA_NEGATIVE,
        FLOATING,
        NOISE
    }

    private static class Cluster {

        double centerDeg;
        double sigmaDeg;
        double ratePerCycle;
        double ampMean;
        double ampSigma;
        SignMode sign;

        Cluster(double centerDeg, double sigmaDeg, double ratePerCycle,
                double ampMean, double ampSigma, SignMode sign) {
            this.centerDeg = centerDeg;
            this.sigmaDeg = sigmaDeg;
            this.ratePerCycle = ratePerCycle;
            this.ampMean = ampMean;
            this.ampSigma = ampSigma;
            this.sign = sign;
        }
    }

    private enum SignMode {
        POSITIVE,
        NEGATIVE,
        BOTH
    }

    /**
     * Generuje sygnał z defektami WNZ
     *
     * @param defect rodzaj defektu
     * @param duration czas "rejestracji"
     * @param fs f próbkowania
     * @param f0 f sygnału
     * @param seed inicjacja generatora psudolosowego
     * @param noiseStd standardowe odchylenie dla szumu Gaussowskiego
     * @param baselineAmp amplituda podstawowa
     * @param pulseUs  szerokość impulsów 5-20 (10-30)  [us]
     * @param tauUs stała czasowa zaniku impulsu (2-5) [us]
     * @param backgroundRate liczba losowych impulsów tła / cykl (20-60-100+)
     * @param backgroundAmp amplituda tła 0.004-0.01
     * @return
     */
    public static double[][] generate(
            Defect defect, // 
            double duration, // 
            double fs, // 
            double f0, // 
            long seed,
            double noiseStd,
            double baselineAmp,
            double pulseUs,
            double tauUs,
            double backgroundRate,
            double backgroundAmp
    ) {
        Random rng = new Random(seed);

        int n = (int) Math.round(duration * fs);
        double[][] data = new double[n][2];

        double[] u = new double[n];

        for (int i = 0; i < n; i++) {
            double t = i / fs;
            data[i][0] = t;

            u[i] = baselineAmp * Math.sin(2.0 * Math.PI * f0 * t);
            u[i] += noiseStd * rng.nextGaussian();
        }

        double period = 1.0 / f0;
        int cycles = (int) Math.floor(duration * f0);

        Cluster[] clusters = getClusters(defect);

        for (int c = 0; c < cycles; c++) {

            int bgCount = poisson(rng, backgroundRate);

            for (int k = 0; k < bgCount; k++) {
                double phase = rng.nextDouble() * 360.0;
                double ti = c * period + phase / 360.0 * period;

                if (ti >= duration) {
                    continue;
                }

                double amp = Math.abs(backgroundAmp + backgroundAmp * 0.35 * rng.nextGaussian());
                amp *= chooseSign(rng, SignMode.BOTH);

                addPulse(
                        u,
                        (int) Math.round(ti * fs),
                        amp,
                        fs,
                        pulseUs * 0.5,
                        tauUs * 0.7
                );
            }

            for (Cluster cl : clusters) {
                int count = poisson(rng, cl.ratePerCycle);

                for (int k = 0; k < count; k++) {
                    double phase = normalizePhase(cl.centerDeg + cl.sigmaDeg * rng.nextGaussian());
                    double ti = c * period + phase / 360.0 * period;

                    if (ti >= duration) {
                        continue;
                    }

                    double amp = Math.abs(cl.ampMean + cl.ampSigma * rng.nextGaussian());
                    amp *= chooseSign(rng, cl.sign);

                    addPulse(
                            u,
                            (int) Math.round(ti * fs),
                            amp,
                            fs,
                            pulseUs,
                            tauUs
                    );
                }
            }
        }

        for (int i = 0; i < n; i++) {
            data[i][1] = u[i];
        }

        return data;
    }

    private static Cluster[] getClusters(Defect defect) {
        switch (defect) {
            case VOID:
                return new Cluster[]{
                    new Cluster(55, 18, 35, 0.035, 0.015, SignMode.BOTH),
                    new Cluster(235, 18, 35, 0.035, 0.015, SignMode.BOTH)
                };

            case SURFACE:
                return new Cluster[]{
                    new Cluster(50, 35, 45, 0.030, 0.020, SignMode.POSITIVE),
                    new Cluster(230, 55, 80, 0.050, 0.030, SignMode.NEGATIVE)
                };

            case CORONA_POSITIVE:
                return new Cluster[]{
                    new Cluster(70, 16, 90, 0.040, 0.015, SignMode.POSITIVE)
                };

            case CORONA_NEGATIVE:
                return new Cluster[]{
                    new Cluster(250, 16, 90, 0.040, 0.015, SignMode.NEGATIVE)
                };

            case FLOATING:
                return new Cluster[]{
                    new Cluster(40, 12, 30, 0.030, 0.015, SignMode.BOTH),
                    new Cluster(90, 5, 20, 0.060, 0.020, SignMode.BOTH),
                    new Cluster(210, 10, 70, 0.070, 0.025, SignMode.BOTH),
                    new Cluster(235, 6, 40, 0.080, 0.020, SignMode.BOTH)
                };

            case NOISE:
                return new Cluster[]{
                    new Cluster(180, 180, 120, 0.010, 0.005, SignMode.BOTH)
                };

            default:
                throw new IllegalArgumentException("Unknown defect: " + defect);
        }
    }

    private static void addPulse(
            double[] u,
            int idx,
            double amp,
            double fs,
            double pulseUs,
            double tauUs
    ) {
        if (idx < 0 || idx >= u.length) {
            return;
        }

        int length = Math.max(1, (int) Math.round(pulseUs * 1e-6 * fs));
        double tau = Math.max(1.0, tauUs * 1e-6 * fs);

        int end = Math.min(u.length, idx + length);

        for (int i = idx; i < end; i++) {
            int k = i - idx;
            double shape = Math.exp(-k / tau);
            u[i] += amp * shape;
        }
    }

    private static double chooseSign(Random rng, SignMode mode) {
        switch (mode) {
            case POSITIVE:
                return 1.0;
            case NEGATIVE:
                return -1.0;
            case BOTH:
                return rng.nextDouble() < 0.5 ? 1.0 : -1.0;
            default:
                return 1.0;
        }
    }

    private static double normalizePhase(double phase) {
        phase %= 360.0;
        if (phase < 0.0) {
            phase += 360.0;
        }
        return phase;
    }

    private static int poisson(Random rng, double lambda) {
        if (lambda <= 0.0) {
            return 0;
        }

        if (lambda < 40.0) {
            double l = Math.exp(-lambda);
            int k = 0;
            double p = 1.0;

            do {
                k++;
                p *= rng.nextDouble();
            } while (p > l);

            return k - 1;
        }

        int x = (int) Math.round(lambda + Math.sqrt(lambda) * rng.nextGaussian());
        return Math.max(0, x);
    }
}
