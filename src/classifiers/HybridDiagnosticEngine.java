package classifiers;

import pipeline.StatisticalAnalyzer.StatisticalResult;
import prpdtool.PRPDConstants;

import java.util.*;

/**
 * Hybrid Diagnostic Fusion Engine (Ensemble) for Partial Discharges (WNZ).
 * Merges deep vision detections (YOLO / RT-DETR object detection on PRPD image)
 * with analytical statistical fingerprint evaluation (IEC 60270 / CIGRE WG 21.03).
 * Provides Explainable AI (XAI) rationale and actionable engineering recommendations.
 */
public class HybridDiagnosticEngine {

    public static class HybridResult {
        public final String primaryClass;
        public final float primaryConfidence;
        public final String polishName;
        public final String riskLevel;       // NORMALNY, OSTRZEGAWCZY, KRYTYCZNY
        public final String modelAgreement;  // PEŁNA ZGODNOŚĆ, CZĘŚCIOWA ZGODNOŚĆ, ROZBIEŻNOŚĆ

        public final Map<String, Float> hybridProbabilities = new LinkedHashMap<>();
        public final Map<String, Float> visionProbabilities = new LinkedHashMap<>();
        public final Map<String, Float> statisticalProbabilities = new LinkedHashMap<>();

        public final Prediction[] visionDetections;
        public final List<String> xaiExplanations = new ArrayList<>();
        public final String recommendedAction;

        public HybridResult(String primaryClass, float primaryConfidence, String polishName,
                            String riskLevel, String modelAgreement,
                            Map<String, Float> hybridProbs,
                            Map<String, Float> visionProbs,
                            Map<String, Float> statProbs,
                            Prediction[] detections,
                            List<String> explanations,
                            String recommendedAction) {
            this.primaryClass = primaryClass;
            this.primaryConfidence = primaryConfidence;
            this.polishName = polishName;
            this.riskLevel = riskLevel;
            this.modelAgreement = modelAgreement;
            this.hybridProbabilities.putAll(hybridProbs);
            this.visionProbabilities.putAll(visionProbs);
            this.statisticalProbabilities.putAll(statProbs);
            this.visionDetections = detections != null ? detections : new Prediction[0];
            this.xaiExplanations.addAll(explanations);
            this.recommendedAction = recommendedAction;
        }
    }

    public static HybridResult analyze(Prediction[] detections, StatisticalResult sr) {
        // 1. Process Vision Predictions
        Map<String, Float> visionProbs = new LinkedHashMap<>();
        for (String c : PRPDConstants.CLASSES) {
            visionProbs.put(c, 0.05f); // prior
        }

        String bestVisionClass = "noise";
        float bestVisionConf = 0.0f;

        if (detections != null && detections.length > 0) {
            for (Prediction p : detections) {
                if (p.classId != -1 && p.className != null) {
                    float current = visionProbs.getOrDefault(p.className, 0.0f);
                    visionProbs.put(p.className, Math.max(current, p.confidence));
                    if (p.confidence > bestVisionConf) {
                        bestVisionConf = p.confidence;
                        bestVisionClass = p.className;
                    }
                }
            }
        }
        normalizeMap(visionProbs);

        // 2. Process Statistical Fingerprint Evaluation
        StatisticalFingerprintClassifier.Evaluation statEval = StatisticalFingerprintClassifier.evaluate(sr);
        Map<String, Float> statProbs = statEval.classProbabilities;
        String bestStatClass = statEval.dominantClass;
        float bestStatConf = statEval.dominantConfidence;

        // 3. Dynamic Weighted Fusion (Ensemble)
        float wVision = (bestVisionConf > 0.40f) ? 0.60f : 0.35f;
        float wStat = 1.0f - wVision;

        Map<String, Float> hybridProbs = new LinkedHashMap<>();
        String primaryClass = "noise";
        float primaryConf = -1.0f;

        for (String c : PRPDConstants.CLASSES) {
            float vP = visionProbs.getOrDefault(c, 0.0f);
            float sP = statProbs.getOrDefault(c, 0.0f);
            float hP = wVision * vP + wStat * sP;
            hybridProbs.put(c, hP);
            if (hP > primaryConf) {
                primaryConf = hP;
                primaryClass = c;
            }
        }
        normalizeMap(hybridProbs);
        primaryConf = hybridProbs.getOrDefault(primaryClass, primaryConf);

        // 4. Model Agreement Assessment
        String agreement;
        if (bestVisionClass.equalsIgnoreCase(bestStatClass)) {
            agreement = "Pełna zgoda wizualno-statystyczna (Wysoka pewność diagnozy)";
        } else if (primaryClass.equalsIgnoreCase(bestVisionClass) || primaryClass.equalsIgnoreCase(bestStatClass)) {
            agreement = "Częściowa zgodność modeli (Ważona decyzja hybrydowa)";
        } else {
            agreement = "Rozbieżność detekcji wizualnej z regułami fizycznymi";
        }

        // 5. Compile Explainable AI (XAI) Explanations
        List<String> xai = new ArrayList<>();
        if (bestVisionConf > 0.35f && !bestVisionClass.equals("noise")) {
            xai.add(String.format(Locale.US,
                    "Sieć neuronowa (YOLO) wykryła wzorzec '%s' z pewnością %.1f%% w obrazie fazowym PRPD.",
                    bestVisionClass, bestVisionConf * 100.0f));
        } else {
            xai.add("Detektor wizualny nie zidentyfikował silnie dominującego wzorca geometrycznego (poziom tła).");
        }

        xai.addAll(statEval.physicalRationales);

        if (bestVisionClass.equalsIgnoreCase(bestStatClass) && !primaryClass.equals("noise")) {
            xai.add(String.format(Locale.US,
                    "Potwierdzenie krzyżowe: cechy fizykalne IEC 60270 są w pełni zgodne z analizą splotową (%s).",
                    primaryClass));
        } else if (!bestVisionClass.equalsIgnoreCase(bestStatClass) && !bestVisionClass.equals("noise") && !bestStatClass.equals("noise")) {
            xai.add(String.format(Locale.US,
                    "Uwaga: Detektor obrazowy wskazuje '%s', podczas gdy rozkład fazowo-ładunkowy sugeruje '%s'. Wynik hybrydowy wypośrodkowano.",
                    bestVisionClass, bestStatClass));
        }

        // 6. Polish Metadata & Risk & Recommendations
        String polishName = getPolishName(primaryClass);
        String riskLevel = determineRisk(primaryClass, sr);
        String action = getRecommendedAction(primaryClass, riskLevel);

        return new HybridResult(primaryClass, primaryConf, polishName, riskLevel, agreement,
                hybridProbs, visionProbs, statProbs, detections, xai, action);
    }

    private static void normalizeMap(Map<String, Float> map) {
        float sum = 0.0f;
        for (float v : map.values()) sum += v;
        if (sum <= 0) sum = 1.0f;
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            entry.setValue(entry.getValue() / sum);
        }
    }

    public static String getPolishName(String className) {
        return switch (className.toLowerCase()) {
            case "corona -" -> "Ulot w półokresie ujemnym (Corona -)";
            case "corona +" -> "Ulot w półokresie dodatnim (Corona +)";
            case "surface" -> "Wyładowania powierzchniowe (Surface Tracking)";
            case "void" -> "Wyładowania w pustce wewnętrznej (Internal Void)";
            case "floating" -> "Potencjał pływający (Floating Element / Particle)";
            case "noise" -> "Szum pomiarowy / Brak aktywnego defektu (Noise)";
            default -> className;
        };
    }

    private static String determineRisk(String className, StatisticalResult sr) {
        if ("noise".equalsIgnoreCase(className)) return "NORMALNY";
        if ("void".equalsIgnoreCase(className)) {
            // Wyładowania w pustce wewnątrz stałego dielektryka to wysokie ryzyko przebicia
            return "KRYTYCZNY";
        }
        if ("surface".equalsIgnoreCase(className)) {
            return "OSTRZEGAWCZY";
        }
        if ("floating".equalsIgnoreCase(className)) {
            return "OSTRZEGAWCZY";
        }
        if (className.startsWith("corona")) {
            // Ulot w gazie/powietrzu jest często nieszkodliwy dla izolacji stałej
            return (sr != null && sr.repetitionRate > 500.0) ? "OSTRZEGAWCZY" : "NORMALNY";
        }
        return "NORMALNY";
    }

    private static String getRecommendedAction(String className, String riskLevel) {
        return switch (className.toLowerCase()) {
            case "void" -> "Krytyczne zagrożenie przebiciem drzewkowym dielektryka stałego. Zalecane natychmiastowe obniżenie obciążenia, weryfikacja chromatografii gazowej (DGA) lub badanie lokalizacyjne (UHF/akustyczne).";
            case "surface" -> "Ryzyko degradacji powierzchniowej i karbonizacji ścieżki (tracking). Zalecana inspekcja czystości izolatorów przepustowych oraz wilgotności otoczenia.";
            case "floating" -> "Obecność luźnego elementu metalicznego lub nieuziemionego ekranu. Zalecana inspekcja połączeń śrubowych, ekranów oraz osprzętu kadzi.";
            case "corona -", "corona +" -> "Wyładowania ulotowe w ośrodku gazowym (ostrza, nierówności osprzętu). Brak bezpośredniego ryzyka przebicia; zalecane wyrównanie potencjału lub montaż pierścieni sterujących.";
            default -> "Stan normalny. Brak wskazań do natychmiastowej interwencji eksploatacyjnej. Kontynuować planowy monitoring.";
        };
    }
}
