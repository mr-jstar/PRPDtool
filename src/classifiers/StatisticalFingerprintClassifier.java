package classifiers;

import pipeline.StatisticalAnalyzer.HalfCycleStats;
import pipeline.StatisticalAnalyzer.StatisticalResult;

import java.util.*;

/**
 * Physical & Statistical Classifier for Partial Discharges (WNZ)
 * based on IEC 60270, IEC 62478, and CIGRE WG 21.03 / D1.33 diagnostic criteria.
 * Computes probability distributions and physical explanations (XAI)
 * across the 6 standard defect classes:
 * - corona -
 * - corona +
 * - surface
 * - void
 * - floating
 * - noise
 */
public class StatisticalFingerprintClassifier {

    public static class Evaluation {
        public final Map<String, Float> classProbabilities = new LinkedHashMap<>();
        public final String dominantClass;
        public final float dominantConfidence;
        public final List<String> physicalRationales = new ArrayList<>();

        public Evaluation(Map<String, Float> probs, String dominantClass, float confidence, List<String> rationales) {
            this.classProbabilities.putAll(probs);
            this.dominantClass = dominantClass;
            this.dominantConfidence = confidence;
            this.physicalRationales.addAll(rationales);
        }
    }

    public static Evaluation evaluate(StatisticalResult sr) {
        Map<String, Float> rawScores = new LinkedHashMap<>();
        List<String> rationales = new ArrayList<>();

        for (String c : prpdtool.PRPDConstants.CLASSES) {
            rawScores.put(c, 0.05f); // baseline prior
        }

        if (sr == null || sr.totalPulses < 5) {
            rawScores.put("noise", 0.90f);
            rationales.add("Zbyt mała liczba impulsów (N < 5) do wyznaczenia odcisku statystycznego; tło szumowe.");
            return normalize(rawScores, rationales);
        }

        HalfCycleStats pos = sr.pos;
        HalfCycleStats neg = sr.neg;
        double cc = sr.crossCorrelationCC;
        double qAsym = sr.chargeAsymmetryQ; // Qmax+ / Qmax-
        double nAsym = sr.countAsymmetryN;   // N+ / N-
        double meanQAsym = sr.meanChargeAsymmetry;

        double posVarCoeff = pos.qMean > 0 ? (pos.qStd / pos.qMean) : 1.0;
        double negVarCoeff = neg.qMean > 0 ? (neg.qStd / neg.qMean) : 1.0;
        double meanVarCoeff = (posVarCoeff + negVarCoeff) / 2.0;

        // =====================================================================
        // 1. CORONA - (Ulot ujemny)
        // Cechy: Q- >> Q+, N- >> N+, skupienie fazowe wokół 270°, niskie CC
        // =====================================================================
        float coronaNegScore = 0.05f;
        if (neg.pulseCount > 0) {
            if (qAsym < 0.65 || (pos.pulseCount == 0 && neg.pulseCount > 10)) {
                coronaNegScore += 0.40f;
                if (qAsym < 0.35) coronaNegScore += 0.25f;
            }
            if (nAsym < 0.50) coronaNegScore += 0.20f;
            if (neg.phaseMean >= 240.0 && neg.phaseMean <= 300.0) coronaNegScore += 0.15f;
            if (cc < 0.30) coronaNegScore += 0.10f;
        }
        if (coronaNegScore > 0.45) {
            rationales.add(String.format(Locale.US,
                    "Dominacja ładunku w półokresie ujemnym (Q+/Q- = %.2f, N+/N- = %.2f) oraz niska korelacja międzypołówkowa (CC = %.2f) silnie wskazują na ulot ujemny (Corona -).",
                    qAsym, nAsym, cc));
        }
        rawScores.put("corona -", coronaNegScore);

        // =====================================================================
        // 2. CORONA + (Ulot dodatni)
        // Cechy: Q+ >> Q-, N+ >> N-, skupienie fazowe wokół 90°, niskie CC
        // =====================================================================
        float coronaPosScore = 0.05f;
        if (pos.pulseCount > 0) {
            if (qAsym > 1.50 || (neg.pulseCount == 0 && pos.pulseCount > 10)) {
                coronaPosScore += 0.40f;
                if (qAsym > 2.80) coronaPosScore += 0.25f;
            }
            if (nAsym > 2.0) coronaPosScore += 0.20f;
            if (pos.phaseMean >= 60.0 && pos.phaseMean <= 120.0) coronaPosScore += 0.15f;
            if (cc < 0.30) coronaPosScore += 0.10f;
        }
        if (coronaPosScore > 0.45) {
            rationales.add(String.format(Locale.US,
                    "Dominacja ładunku w półokresie dodatnim (Q+/Q- = %.2f, N+/N- = %.2f) wokół szczytu sinusoidy (śr. faza %.1f°) wskazuje na ulot dodatni (Corona +).",
                    qAsym, nAsym, pos.phaseMean));
        }
        rawScores.put("corona +", coronaPosScore);

        // =====================================================================
        // 3. VOID / INTERNAL (Pustka gazowa / wtrącenie w dielektryku stałym)
        // Cechy: Wysoka symetria połówkowa (Q+/Q- ~ 1.0, N+/N- ~ 1.0),
        // wysokie CC (> 0.65), wczesny kąt zapłonu przy przejściu przez zero
        // =====================================================================
        float voidScore = 0.05f;
        boolean qSymmetric = (qAsym >= 0.70 && qAsym <= 1.40);
        boolean nSymmetric = (nAsym >= 0.65 && nAsym <= 1.50);

        if (qSymmetric && nSymmetric) {
            voidScore += 0.35f;
            if (cc >= 0.65) voidScore += 0.30f;
            if (cc >= 0.80) voidScore += 0.15f;
            // Early inception angles near 0 deg and 180 deg
            if (pos.inceptionPhase < 55.0 && neg.inceptionPhase < 235.0 && neg.inceptionPhase >= 180.0) {
                voidScore += 0.15f;
            }
        }
        if (voidScore > 0.45) {
            rationales.add(String.format(Locale.US,
                    "Wysoka symetria międzypołówkowa (Q+/Q- = %.2f, N+/N- = %.2f), silna korelacja profili (CC = %.2f) oraz wczesny zapłon (φ_inc+ = %.1f°, φ_inc- = %.1f°) są typowe dla wyładowań w pustce wewnętrznej (Void).",
                    qAsym, nAsym, cc, pos.inceptionPhase, neg.inceptionPhase));
        }
        rawScores.put("void", voidScore);

        // =====================================================================
        // 4. FLOATING (Cząstka o potencjale pływającym / luźny element)
        // Cechy: Wyjątkowo niska wariancja ładunku (σ_Q / μ_Q < 0.25),
        // identyczna wysokość impulsów, dwa ostre piki przesunięte o 180°
        // =====================================================================
        float floatingScore = 0.05f;
        if (meanVarCoeff < 0.30 && sr.totalPulses > 20) {
            floatingScore += 0.35f;
            if (meanVarCoeff < 0.20) floatingScore += 0.25f;
            double phaseDiff = Math.abs(neg.phaseMean - pos.phaseMean);
            if (Math.abs(phaseDiff - 180.0) < 25.0) {
                floatingScore += 0.25f;
            }
        }
        if (floatingScore > 0.45) {
            rationales.add(String.format(Locale.US,
                    "Niezwykle niski współczynnik zmienności ładunku (σ/μ = %.2f) i regularne odstępy fazowe (~180°) wskazują na potencjał pływający (Floating element/particle).",
                    meanVarCoeff));
        }
        rawScores.put("floating", floatingScore);

        // =====================================================================
        // 5. SURFACE (Wyładowania powierzchniowe / tracking)
        // Cechy: Szeroki rozkład fazowy (duża dyspersja σ_φ), asymetria skośności,
        // zróżnicowana energia, obecność impulsów w obu połówkach z umiarkowaną asymetrią
        // =====================================================================
        float surfaceScore = 0.05f;
        if (pos.phaseStd > 25.0 && neg.phaseStd > 25.0) {
            surfaceScore += 0.25f;
            if (Math.abs(pos.phaseSkewness - neg.phaseSkewness) > 0.40) {
                surfaceScore += 0.25f;
            }
            if (meanVarCoeff > 0.40) surfaceScore += 0.15f;
            if (cc >= 0.20 && cc <= 0.70) surfaceScore += 0.15f;
        }
        if (surfaceScore > 0.45) {
            rationales.add(String.format(Locale.US,
                    "Szeroka dyspersja fazowa (σ_φ+ = %.1f°, σ_φ- = %.1f°) z wyraźną asymetrią skośności (ΔSk = %.2f) jest charakterystyczna dla wyładowań powierzchniowych (Surface).",
                    pos.phaseStd, neg.phaseStd, Math.abs(pos.phaseSkewness - neg.phaseSkewness)));
        }
        rawScores.put("surface", surfaceScore);

        // =====================================================================
        // 6. NOISE (Szum tła / zakłócenia)
        // Cechy: Brak korelacji fazowej, płaski rozkład lub pojedyncze szpilki
        // =====================================================================
        float noiseScore = 0.05f;
        if (sr.repetitionRate < 2.0 || sr.totalPulses < 15) {
            noiseScore += 0.40f;
        }
        if (pos.phaseKurtosis < -1.0 && neg.phaseKurtosis < -1.0) {
            noiseScore += 0.30f; // Płaski (platykurtyczny) rozkład jednostajny
        }
        rawScores.put("noise", noiseScore);

        return normalize(rawScores, rationales);
    }

    private static Evaluation normalize(Map<String, Float> scores, List<String> rationales) {
        float sum = 0.0f;
        for (float v : scores.values()) sum += v;
        if (sum <= 0) sum = 1.0f;

        Map<String, Float> normalized = new LinkedHashMap<>();
        String bestClass = "noise";
        float bestConf = -1.0f;

        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            float p = entry.getValue() / sum;
            normalized.put(entry.getKey(), p);
            if (p > bestConf) {
                bestConf = p;
                bestClass = entry.getKey();
            }
        }

        if (rationales.isEmpty()) {
            rationales.add("Rozkład cech fazowo-ładunkowych wskazuje formę: " + bestClass);
        }

        return new Evaluation(normalized, bestClass, bestConf, rationales);
    }
}
