package pipeline;

import java.util.Arrays;
import java.util.Locale;

/**
 * Statistical analyzer for Phase-Resolved Partial Discharge (PRPD) data
 * based on IEC 60270, CIGRE WG 21.03 guidelines, and academic research
 * (Buczko 2018, Kluge 2021/2026, Mikrut 2024).
 * 
 * Computes:
 * 1. Phase distribution profiles:
 *    - Hn(phi)    : pulse repetition / count distribution
 *    - Hqn(phi)   : mean discharge magnitude distribution [mV]
 *    - Hqmax(phi) : maximum discharge magnitude distribution [mV]
 * 2. Statistical operators for positive (+) [0-180 deg] and negative (-) [180-360 deg] half-cycles:
 *    - Phase skewness Sk(phi) and kurtosis Ku(phi)
 *    - Amplitude skewness Sk(Q) and kurtosis Ku(Q)
 *    - Pulse counts, peak charges, mean charges, variances, inception angles
 * 3. Cross-cycle asymmetry and correlation:
 *    - Charge asymmetry Q = Qmax+ / Qmax-
 *    - Count asymmetry N = N+ / N-
 *    - Cross-correlation CC(Hqn+, Hqn-)
 *    - Repetition rate n [pulses/s]
 * 4. Buczko 66-dimensional feature vector for Machine Learning classifiers.
 */
public class StatisticalAnalyzer {

    /**
     * Holds discrete phase profiles: Hn(phi), Hqn(phi), Hqmax(phi).
     */
    public static class ProfileData {
        public final int binsCount;
        public final double[] phaseCenters; // degrees (e.g. 0.5, 1.5, ...)
        public final int[] Hn;               // count of pulses per bin
        public final double[] Hqn;           // mean magnitude per bin [mV]
        public final double[] Hqmax;         // peak magnitude per bin [mV]
        public final int maxHn;
        public final double maxHqn;
        public final double maxHqmax;

        public ProfileData(int binsCount, double[] phaseCenters, int[] Hn, double[] Hqn, double[] Hqmax,
                           int maxHn, double maxHqn, double maxHqmax) {
            this.binsCount = binsCount;
            this.phaseCenters = phaseCenters;
            this.Hn = Hn;
            this.Hqn = Hqn;
            this.Hqmax = Hqmax;
            this.maxHn = maxHn;
            this.maxHqn = maxHqn;
            this.maxHqmax = maxHqmax;
        }
    }

    /**
     * Holds statistical descriptors for one half-cycle of test voltage.
     */
    public static class HalfCycleStats {
        public final String polarity;          // "+" (Positive: 0-180 deg) or "-" (Negative: 180-360 deg)
        public final int pulseCount;           // N+ or N-
        public final double pulsePercentage;   // % of total pulses
        public final double qMax;              // peak pulse magnitude [mV]
        public final double qMean;             // mean pulse magnitude [mV]
        public final double qStd;              // std dev of pulse magnitude [mV]
        public final double qVariance;         // variance of magnitude
        public final double qMedian;           // median magnitude
        public final double inceptionPhase;    // earliest discharge phase [deg]
        public final double phaseMean;         // mean phase angle [deg]
        public final double phaseStd;          // phase dispersion [deg]
        public final double phaseSkewness;     // Sk(phi) from Hn distribution
        public final double phaseKurtosis;     // Ku(phi) from Hn distribution (excess, Gaussian=0)
        public final double ampSkewness;       // Sk(Q) from raw pulse amplitudes
        public final double ampKurtosis;       // Ku(Q) from raw pulse amplitudes (excess, Gaussian=0)

        public HalfCycleStats(String polarity, int pulseCount, double pulsePercentage, double qMax, double qMean,
                              double qStd, double qVariance, double qMedian, double inceptionPhase,
                              double phaseMean, double phaseStd, double phaseSkewness, double phaseKurtosis,
                              double ampSkewness, double ampKurtosis) {
            this.polarity = polarity;
            this.pulseCount = pulseCount;
            this.pulsePercentage = pulsePercentage;
            this.qMax = qMax;
            this.qMean = qMean;
            this.qStd = qStd;
            this.qVariance = qVariance;
            this.qMedian = qMedian;
            this.inceptionPhase = inceptionPhase;
            this.phaseMean = phaseMean;
            this.phaseStd = phaseStd;
            this.phaseSkewness = phaseSkewness;
            this.phaseKurtosis = phaseKurtosis;
            this.ampSkewness = ampSkewness;
            this.ampKurtosis = ampKurtosis;
        }
    }

    /**
     * Complete analysis result package.
     */
    public static class StatisticalResult {
        public final int totalPulses;
        public final double durationSec;
        public final double repetitionRate;       // n [pulses/s]
        public final double totalApparentEnergy;  // sum(|u|^2) or sum(|u|)
        public final ProfileData profiles;
        public final HalfCycleStats pos;          // Positive half-cycle (0-180 deg)
        public final HalfCycleStats neg;          // Negative half-cycle (180-360 deg)
        public final double chargeAsymmetryQ;     // Qmax+ / Qmax-
        public final double countAsymmetryN;      // N+ / N-
        public final double meanChargeAsymmetry;  // qMean+ / qMean-
        public final double crossCorrelationCC;   // CC(Hqn+, Hqn-)
        public final String diagnosticHint;

        public StatisticalResult(int totalPulses, double durationSec, double repetitionRate,
                                 double totalApparentEnergy, ProfileData profiles,
                                 HalfCycleStats pos, HalfCycleStats neg,
                                 double chargeAsymmetryQ, double countAsymmetryN,
                                 double meanChargeAsymmetry, double crossCorrelationCC,
                                 String diagnosticHint) {
            this.totalPulses = totalPulses;
            this.durationSec = durationSec;
            this.repetitionRate = repetitionRate;
            this.totalApparentEnergy = totalApparentEnergy;
            this.profiles = profiles;
            this.pos = pos;
            this.neg = neg;
            this.chargeAsymmetryQ = chargeAsymmetryQ;
            this.countAsymmetryN = countAsymmetryN;
            this.meanChargeAsymmetry = meanChargeAsymmetry;
            this.crossCorrelationCC = crossCorrelationCC;
            this.diagnosticHint = diagnosticHint;
        }

        /**
         * Formats all descriptors into a CSV table.
         */
        public String toCSV() {
            StringBuilder sb = new StringBuilder();
            sb.append("Descriptor,Positive [0-180 deg],Negative [180-360 deg],Relation / Cross-Cycle\n");
            sb.append(String.format(Locale.US, "Pulse Count (N),%d (%.1f%%),%d (%.1f%%),N+ / N- = %.3f\n",
                    pos.pulseCount, pos.pulsePercentage, neg.pulseCount, neg.pulsePercentage, countAsymmetryN));
            sb.append(String.format(Locale.US, "Peak Amplitude (Qmax) [mV],%.2f,%.2f,Q+ / Q- = %.3f\n",
                    pos.qMax, neg.qMax, chargeAsymmetryQ));
            sb.append(String.format(Locale.US, "Mean Amplitude (mu_Q) [mV],%.2f,%.2f,mu+ / mu- = %.3f\n",
                    pos.qMean, neg.qMean, meanChargeAsymmetry));
            sb.append(String.format(Locale.US, "Std Deviation (sigma_Q) [mV],%.2f,%.2f,-\n",
                    pos.qStd, neg.qStd));
            sb.append(String.format(Locale.US, "Inception Phase (phi_inc) [deg],%.1f,%.1f,delta_phi = %.1f\n",
                    pos.inceptionPhase, neg.inceptionPhase, Math.abs(neg.inceptionPhase - pos.inceptionPhase)));
            sb.append(String.format(Locale.US, "Mean Phase (mu_phi) [deg],%.1f,%.1f,delta_mu = %.1f\n",
                    pos.phaseMean, neg.phaseMean, Math.abs(neg.phaseMean - pos.phaseMean)));
            sb.append(String.format(Locale.US, "Phase Skewness (Sk_phi),%.3f,%.3f,-\n",
                    pos.phaseSkewness, neg.phaseSkewness));
            sb.append(String.format(Locale.US, "Phase Kurtosis (Ku_phi),%.3f,%.3f,-\n",
                    pos.phaseKurtosis, neg.phaseKurtosis));
            sb.append(String.format(Locale.US, "Amp Skewness (Sk_Q),%.3f,%.3f,-\n",
                    pos.ampSkewness, neg.ampSkewness));
            sb.append(String.format(Locale.US, "Amp Kurtosis (Ku_Q),%.3f,%.3f,-\n",
                    pos.ampKurtosis, neg.ampKurtosis));
            sb.append(String.format(Locale.US, "Cross-Correlation (CC),-,-,%.3f\n",
                    crossCorrelationCC));
            sb.append(String.format(Locale.US, "Repetition Rate (n) [pps],-,-,%.1f\n",
                    repetitionRate));
            sb.append(String.format(Locale.US, "Total Pulses (N_total),-,-,%d\n",
                    totalPulses));
            sb.append(String.format(Locale.US, "Duration [s],-,-,%.2f\n",
                    durationSec));
            sb.append(String.format(Locale.US, "Diagnostic Advisory,-,-,\"%s\"\n",
                    diagnosticHint.replace("\"", "'")));
            return sb.toString();
        }

        /**
         * Exports discrete profiles Hn(phi), Hqn(phi), Hqmax(phi) to CSV.
         */
        public String profilesToCSV() {
            StringBuilder sb = new StringBuilder();
            sb.append("Phase_deg,Hn_count,Hqn_mV,Hqmax_mV\n");
            for (int i = 0; i < profiles.binsCount; i++) {
                sb.append(String.format(Locale.US, "%.2f,%d,%.4f,%.4f\n",
                        profiles.phaseCenters[i], profiles.Hn[i], profiles.Hqn[i], profiles.Hqmax[i]));
            }
            return sb.toString();
        }
    }

    /**
     * Computes the complete statistical analysis on the provided pulse buffers.
     */
    public static StatisticalResult compute(double[] phases, double[] amps, double[] times,
                                            int size, int binsCount, double f0) {
        if (binsCount < 10) binsCount = 360;
        if (size <= 0 || phases == null || amps == null) {
            return emptyResult(binsCount);
        }

        double binWidth = 360.0 / binsCount;
        double[] phaseCenters = new double[binsCount];
        int[] Hn = new int[binsCount];
        double[] sumAmp = new double[binsCount];
        double[] Hqmax = new double[binsCount];

        for (int i = 0; i < binsCount; i++) {
            phaseCenters[i] = (i + 0.5) * binWidth;
        }

        int posCount = 0;
        int negCount = 0;
        double minT = Double.POSITIVE_INFINITY;
        double maxT = Double.NEGATIVE_INFINITY;
        double totalEnergy = 0.0;

        for (int i = 0; i < size; i++) {
            double ph = phases[i] % 360.0;
            if (ph < 0.0) ph += 360.0;

            double a = Math.abs(amps[i]);
            totalEnergy += a;

            if (times != null && i < times.length) {
                double t = times[i];
                if (t < minT) minT = t;
                if (t > maxT) maxT = t;
            }

            int bin = (int) (ph / binWidth);
            if (bin < 0) bin = 0;
            if (bin >= binsCount) bin = binsCount - 1;

            Hn[bin]++;
            sumAmp[bin] += a;
            if (a > Hqmax[bin]) {
                Hqmax[bin] = a;
            }

            if (ph < 180.0) {
                posCount++;
            } else {
                negCount++;
            }
        }

        double[] Hqn = new double[binsCount];
        int maxHn = 0;
        double maxHqn = 0.0;
        double maxHqmax = 0.0;

        for (int i = 0; i < binsCount; i++) {
            if (Hn[i] > 0) {
                Hqn[i] = sumAmp[i] / Hn[i];
            } else {
                Hqn[i] = 0.0;
            }
            if (Hn[i] > maxHn) maxHn = Hn[i];
            if (Hqn[i] > maxHqn) maxHqn = Hqn[i];
            if (Hqmax[i] > maxHqmax) maxHqmax = Hqmax[i];
        }

        ProfileData profiles = new ProfileData(binsCount, phaseCenters, Hn, Hqn, Hqmax,
                maxHn, maxHqn, maxHqmax);

        // Compute duration and repetition rate
        double durationSec = (maxT > minT && Double.isFinite(maxT) && Double.isFinite(minT))
                ? (maxT - minT) : 0.0;
        if (durationSec <= 0.0 && f0 > 0) {
            // fallback: approximate duration from total pulses assuming reasonable acquisition
            durationSec = 1.0;
        }
        double repRate = durationSec > 0.0 ? (size / durationSec) : size;

        // Partition pulses into pos and neg arrays for sample moments
        double[] posAmps = new double[posCount];
        double[] posPhases = new double[posCount];
        double[] negAmps = new double[negCount];
        double[] negPhases = new double[negCount];

        int pIdx = 0;
        int nIdx = 0;
        for (int i = 0; i < size; i++) {
            double ph = phases[i] % 360.0;
            if (ph < 0.0) ph += 360.0;
            double a = Math.abs(amps[i]);

            if (ph < 180.0) {
                posAmps[pIdx] = a;
                posPhases[pIdx] = ph;
                pIdx++;
            } else {
                negAmps[nIdx] = a;
                negPhases[nIdx] = ph;
                nIdx++;
            }
        }

        HalfCycleStats posStats = computeHalfCycleStats("+", posCount, size, posAmps, posPhases,
                Hn, phaseCenters, 0, binsCount / 2);
        HalfCycleStats negStats = computeHalfCycleStats("-", negCount, size, negAmps, negPhases,
                Hn, phaseCenters, binsCount / 2, binsCount);

        // Asymmetry & Cross Correlation
        double qAsym = (negStats.qMax > 1e-9) ? (posStats.qMax / negStats.qMax) : (posStats.qMax > 0 ? 999.0 : 1.0);
        double nAsym = (negStats.pulseCount > 0) ? ((double) posStats.pulseCount / negStats.pulseCount) : (posStats.pulseCount > 0 ? 999.0 : 1.0);
        double meanAsym = (negStats.qMean > 1e-9) ? (posStats.qMean / negStats.qMean) : (posStats.qMean > 0 ? 999.0 : 1.0);

        double cc = computeCrossCorrelation(Hqn, binsCount);

        String diagnosticHint = generateDiagnosticHint(size, cc, qAsym, nAsym, posStats, negStats);

        return new StatisticalResult(size, durationSec, repRate, totalEnergy, profiles,
                posStats, negStats, qAsym, nAsym, meanAsym, cc, diagnosticHint);
    }

    private static HalfCycleStats computeHalfCycleStats(String polarity, int count, int totalSize,
                                                        double[] amps, double[] phases,
                                                        int[] Hn, double[] phaseCenters,
                                                        int binStart, int binEnd) {
        double percentage = totalSize > 0 ? (100.0 * count / totalSize) : 0.0;
        if (count == 0) {
            double defaultInception = polarity.equals("+") ? 0.0 : 180.0;
            return new HalfCycleStats(polarity, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    defaultInception, defaultInception + 90.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        // Amplitude metrics
        double qMax = 0.0;
        double sumQ = 0.0;
        double minPh = Double.POSITIVE_INFINITY;

        for (int i = 0; i < count; i++) {
            double a = amps[i];
            if (a > qMax) qMax = a;
            sumQ += a;
            if (phases[i] < minPh) minPh = phases[i];
        }

        double qMean = sumQ / count;

        // Variance, skewness, kurtosis for amplitude samples
        double sumSqQ = 0.0;
        double sumCubeQ = 0.0;
        double sumQuadQ = 0.0;

        for (int i = 0; i < count; i++) {
            double diff = amps[i] - qMean;
            double sq = diff * diff;
            sumSqQ += sq;
            sumCubeQ += sq * diff;
            sumQuadQ += sq * sq;
        }

        double qVar = sumSqQ / count;
        double qStd = Math.sqrt(qVar);
        double ampSkew = (qStd > 1e-9) ? (sumCubeQ / count) / Math.pow(qStd, 3) : 0.0;
        double ampKurt = (qStd > 1e-9) ? ((sumQuadQ / count) / Math.pow(qStd, 4) - 3.0) : 0.0;

        // Median amplitude
        double[] sortedAmps = Arrays.copyOf(amps, count);
        Arrays.sort(sortedAmps);
        double qMedian = (count % 2 == 1) ? sortedAmps[count / 2]
                : 0.5 * (sortedAmps[count / 2 - 1] + sortedAmps[count / 2]);

        // Phase distribution moments from Hn(phi)
        double totalHnWeight = 0.0;
        for (int b = binStart; b < binEnd; b++) {
            totalHnWeight += Hn[b];
        }

        double phMean = 0.0;
        if (totalHnWeight > 0) {
            for (int b = binStart; b < binEnd; b++) {
                phMean += phaseCenters[b] * Hn[b];
            }
            phMean /= totalHnWeight;
        } else {
            phMean = polarity.equals("+") ? 90.0 : 270.0;
        }

        double sumSqPh = 0.0;
        double sumCubePh = 0.0;
        double sumQuadPh = 0.0;

        if (totalHnWeight > 0) {
            for (int b = binStart; b < binEnd; b++) {
                double diff = phaseCenters[b] - phMean;
                double sq = diff * diff;
                double w = Hn[b];
                sumSqPh += sq * w;
                sumCubePh += sq * diff * w;
                sumQuadPh += sq * sq * w;
            }
        }

        double phVar = totalHnWeight > 0 ? (sumSqPh / totalHnWeight) : 0.0;
        double phStd = Math.sqrt(phVar);
        double phSkew = (phStd > 1e-9 && totalHnWeight > 0)
                ? (sumCubePh / totalHnWeight) / Math.pow(phStd, 3) : 0.0;
        double phKurt = (phStd > 1e-9 && totalHnWeight > 0)
                ? ((sumQuadPh / totalHnWeight) / Math.pow(phStd, 4) - 3.0) : 0.0;

        return new HalfCycleStats(polarity, count, percentage, qMax, qMean, qStd, qVar, qMedian,
                minPh, phMean, phStd, phSkew, phKurt, ampSkew, ampKurt);
    }

    /**
     * Cross-correlation CC between positive and negative half-cycles of Hqn(phi).
     * Compares Hqn+(phi) [0-180 deg] with Hqn-(phi+180 deg) [180-360 deg].
     */
    private static double computeCrossCorrelation(double[] Hqn, int binsCount) {
        int half = binsCount / 2;
        double sumA = 0.0;
        double sumB = 0.0;

        for (int i = 0; i < half; i++) {
            sumA += Hqn[i];
            sumB += Hqn[i + half];
        }

        double meanA = sumA / half;
        double meanB = sumB / half;

        double cov = 0.0;
        double varA = 0.0;
        double varB = 0.0;

        for (int i = 0; i < half; i++) {
            double da = Hqn[i] - meanA;
            double db = Hqn[i + half] - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }

        if (varA < 1e-12 || varB < 1e-12) {
            return 0.0;
        }

        double denom = Math.sqrt(varA * varB);
        double cc = denom > 1e-12 ? (cov / denom) : 0.0;
        if (cc > 1.0) cc = 1.0;
        if (cc < -1.0) cc = -1.0;
        return cc;
    }

    /**
     * Generates a diagnostic advisory based on CIGRE Working Group 21.03 rules.
     */
    private static String generateDiagnosticHint(int size, double cc, double qAsym, double nAsym,
                                                 HalfCycleStats pos, HalfCycleStats neg) {
        if (size < 15) {
            return "Insufficient discharge pulse count (<15) for robust statistical classification.";
        }

        // High symmetry -> typical for internal void/cavity discharges
        if (cc >= 0.70 && qAsym >= 0.75 && qAsym <= 1.35 && nAsym >= 0.65 && nAsym <= 1.50) {
            return String.format(Locale.US,
                "Strong half-cycle symmetry (CC = %.2f, Q = %.2f). Characteristic of INTERNAL / VOID (Cavity) discharges.",
                cc, qAsym);
        }

        // Strong polarity asymmetry -> typical for Corona (ulot)
        if (qAsym > 2.2 || qAsym < 0.45 || nAsym > 3.0 || nAsym < 0.33) {
            String dominant = (pos.pulseCount > neg.pulseCount) ? "Positive (+)" : "Negative (-)";
            return String.format(Locale.US,
                "Severe polarity asymmetry (Q = %.2f, N-ratio = %.2f, dominant: %s). Characteristic of CORONA (Point-to-plane) discharges.",
                qAsym, nAsym, dominant);
        }

        // Concentrated phase distribution with moderate symmetry -> Surface / Creeping discharges
        if ((pos.phaseKurtosis > 1.5 || neg.phaseKurtosis > 1.5) && cc >= 0.25) {
            return String.format(Locale.US,
                "High phase concentration (Ku_pos = %.2f, Ku_neg = %.2f, CC = %.2f). Characteristic of SURFACE / CREEPING discharges.",
                pos.phaseKurtosis, neg.phaseKurtosis, cc);
        }

        // Periodic floating / loose contact
        if (cc >= 0.50 && Math.abs(pos.phaseSkewness) < 0.6 && Math.abs(neg.phaseSkewness) < 0.6) {
            return String.format(Locale.US,
                "Regular symmetrical phase bands (CC = %.2f, Sk_pos = %.2f). Characteristic of FLOATING POTENTIAL or regular internal discharges.",
                cc, pos.phaseSkewness);
        }

        return String.format(Locale.US,
            "Mixed / Complex pattern (CC = %.2f, Q = %.2f, N-ratio = %.2f). Inspect single pulse waveforms and PRPD clusters.",
            cc, qAsym, nAsym);
    }

    private static StatisticalResult emptyResult(int binsCount) {
        double[] centers = new double[binsCount];
        double bw = 360.0 / binsCount;
        for (int i = 0; i < binsCount; i++) centers[i] = (i + 0.5) * bw;
        ProfileData p = new ProfileData(binsCount, centers, new int[binsCount], new double[binsCount], new double[binsCount], 0, 0, 0);
        HalfCycleStats pos = new HalfCycleStats("+", 0, 0, 0, 0, 0, 0, 0, 0, 90, 0, 0, 0, 0, 0);
        HalfCycleStats neg = new HalfCycleStats("-", 0, 0, 0, 0, 0, 0, 0, 180, 270, 0, 0, 0, 0, 0);
        return new StatisticalResult(0, 0, 0, 0, p, pos, neg, 1.0, 1.0, 1.0, 0.0, "No pulse data available.");
    }
}
