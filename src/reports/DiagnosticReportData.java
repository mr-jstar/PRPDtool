package reports;

import pipeline.PulseWaveform;
import pipeline.StatisticalAnalyzer.StatisticalResult;

import java.awt.image.BufferedImage;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data container encapsulating all measurement, diagnostic, statistical,
 * calibration, and visual data required to compile an IEC 60270 / CIGRE
 * Partial Discharge Diagnostic Report.
 */
public class DiagnosticReportData {

    // Document & Measurement Metadata
    public String reportId = "WNZ-REPORT-001";
    public Date timestamp = new Date();
    public String objectName = "Rozdzielnica SN w izolacji powietrznej (AIS) 15 kV";
    public String bayOrPhase = "Pole probiercze / Faza L1";
    public String location = "Hala Wysokich Napięć WE PW, Warszawa";
    public String testVoltageKv = "12.0";
    public String lineFrequencyHz = "50.0";
    public String operatorName = "";
    public String sensorType = "Sprzęgacz pojemnościowy TEV / HFCT (IEC 62478)";
    public String samplingInfo = "125 MS/s, 14-bit (Red Pitaya STEMlab 125-14)";
    public String notes = "Stanowisko badawcze WNZ na Hali Wysokich Napięć WE PW. Transformator probierczy 100 kV z regulatorem indukcyjnym i rezystorem wodnym Ro = 40 kΩ. Akwizycja sygnału kartą Red Pitaya STEMlab 125-14.";

    // IEC 60270 Apparent Charge Calibration Details
    public boolean isCalibrated = false;
    public double calibrationFactor = 1.0;
    public String chargeUnit = "mV";
    public String calibratorName = "Omicron CAL 542";
    public String calibrationDate = "-";
    public double targetCharge = 0.0;
    public double measuredVoltage = 0.0;

    // Statistical Fingerprint Analysis Result
    public StatisticalResult statisticalResult;

    // Representative Qmax Pulse Waveform & FFT
    public PulseWaveform qmaxPulse;

    // AI Classification Results (Classifier Name -> Diagnostic Class & Confidence)
    public Map<String, String> aiClassifications = new LinkedHashMap<>();

    // High-Resolution Chart Images
    public BufferedImage prpdImage;
    public BufferedImage profilesImage;
    public BufferedImage pulseScopeImage;

    // Section Inclusion Toggles
    public boolean includePrpd = true;
    public boolean includeProfiles = true;
    public boolean includePulseScope = true;
    public boolean includeStatisticalTable = true;
    public boolean includeAiDiagnosis = true;

    /**
     * Determines overall diagnostic risk level based on CIGRE / IEC thresholds.
     * @return "NORMALNY" (Low), "OSTRZEGAWCZY" (Medium), or "KRYTYCZNY" (High)
     */
    public String getDiagnosticRiskLevel() {
        if (statisticalResult == null) return "NIEZNANY";

        double qMax = Math.max(statisticalResult.pos.qMax, statisticalResult.neg.qMax);
        if (isCalibrated && "pC".equalsIgnoreCase(chargeUnit)) {
            if (qMax > 2500.0) return "KRYTYCZNY";
            if (qMax > 500.0) return "OSTRZEGAWCZY";
            return "NORMALNY";
        } else if (isCalibrated && "nC".equalsIgnoreCase(chargeUnit)) {
            if (qMax > 2.5) return "KRYTYCZNY";
            if (qMax > 0.5) return "OSTRZEGAWCZY";
            return "NORMALNY";
        } else {
            // Raw mV thresholds
            if (qMax > 250.0) return "KRYTYCZNY";
            if (qMax > 50.0) return "OSTRZEGAWCZY";
            return "NORMALNY";
        }
    }

    public String getRiskDescription() {
        String level = getDiagnosticRiskLevel();
        return switch (level) {
            case "KRYTYCZNY" -> "Wysoka intensywność wyładowań niezupełnych stwarzająca ryzyko degradacji dielektryka. Zalecana pilna weryfikacja i skrócenie czasu do kolejnego badania.";
            case "OSTRZEGAWCZY" -> "Umiarkowana aktywność WNZ przekraczająca poziom tła. Zalecane monitorowanie trendu parametrów i inspekcja osprzętu.";
            default -> "Poziom wyładowań niezupełnych w granicach normy eksploatacyjnej. Brak bezpośredniego zagrożenia uszkodzeniem izolacji.";
        };
    }
}
