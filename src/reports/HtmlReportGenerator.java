package reports;

import pipeline.PulseWaveform;
import pipeline.StatisticalAnalyzer.HalfCycleStats;
import pipeline.StatisticalAnalyzer.StatisticalResult;
import prpdtool.ChargeCalibrationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Generates an executive, standalone HTML5 Diagnostic Report for Partial Discharge (WNZ)
 * compliant with IEC 60270, IEC 62478, and CIGRE WG 21.03 / D1.33.
 * All charts are embedded as inline Base64 PNGs. Print-ready for instant PDF export.
 */
public class HtmlReportGenerator {

    public static File generateReport(DiagnosticReportData data, File outputFile) throws IOException {
        String html = buildHtml(data);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        try (FileWriter fw = new FileWriter(outputFile, java.nio.charset.StandardCharsets.UTF_8)) {
            fw.write(html);
        }
        return outputFile;
    }

    public static String buildHtml(DiagnosticReportData d) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String dateStr = sdf.format(d.timestamp);

        ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
        String u = d.isCalibrated ? d.chargeUnit : "mV";

        StatisticalResult sr = d.statisticalResult;
        String riskLevel = d.getDiagnosticRiskLevel();
        String riskColor = switch (riskLevel) {
            case "KRYTYCZNY" -> "#ef4444";
            case "OSTRZEGAWCZY" -> "#f59e0b";
            default -> "#10b981";
        };
        String riskBg = switch (riskLevel) {
            case "KRYTYCZNY" -> "#fef2f2";
            case "OSTRZEGAWCZY" -> "#fffbeb";
            default -> "#ecfdf5";
        };

        sb.append("<!DOCTYPE html>\n<html lang=\"pl\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>Raport Diagnostyczny WNZ — ").append(escape(d.objectName)).append("</title>\n");
        sb.append("<style>\n");
        sb.append(getCss());
        sb.append("</style>\n</head>\n<body>\n");

        // TOP ACTION BAR (Hidden in print)
        sb.append("<div class=\"action-bar no-print\">\n");
        sb.append("  <div class=\"action-left\">\n");
        sb.append("    <strong>Raport Diagnostyczny WNZ (IEC 60270 / CIGRE)</strong> — ").append(escape(d.reportId)).append("\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"action-right\">\n");
        sb.append("    <button class=\"btn btn-secondary\" onclick=\"copySummary()\">Kopiuj podsumowanie</button>\n");
        sb.append("    <button class=\"btn btn-primary\" onclick=\"window.print()\">Drukuj / Zapisz jako PDF</button>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"report-container\">\n");

        // REPORT HEADER
        sb.append("<header class=\"report-header\">\n");
        sb.append("  <div class=\"header-titles\">\n");
        sb.append("    <h1>RAPORT DIAGNOSTYCZNY WYŁADOWAŃ NIEZUPEŁNYCH</h1>\n");
        sb.append("    <div class=\"subtitle\">Diagnostyka wysokonapięciowa układów izolacyjnych wg PN-EN / IEC 60270 oraz CIGRE WG 21.03</div>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"header-badge\">\n");
        sb.append("    <div class=\"badge-title\">STATUS DIAGNOSTYCZNY</div>\n");
        sb.append("    <div class=\"risk-pill\" style=\"background:").append(riskBg).append("; color:").append(riskColor).append("; border: 1.5px solid ").append(riskColor).append(";\">");
        sb.append(riskLevel).append("</div>\n");
        sb.append("  </div>\n");
        sb.append("</header>\n");

        // METADATA & CALIBRATION DUAL CARD
        sb.append("<div class=\"grid-2\">\n");

        // Card 1: Object & Test Setup
        sb.append("  <div class=\"card\">\n");
        sb.append("    <div class=\"card-title\">Dane Obiektu i Parametry Pomiaru</div>\n");
        sb.append("    <table class=\"table-kv\">\n");
        sb.append("      <tr><td>Obiekt badany:</td><td><strong>").append(escape(d.objectName)).append("</strong></td></tr>\n");
        sb.append("      <tr><td>Pole / Faza:</td><td>").append(escape(d.bayOrPhase)).append("</td></tr>\n");
        sb.append("      <tr><td>Lokalizacja:</td><td>").append(escape(d.location)).append("</td></tr>\n");
        sb.append("      <tr><td>Napięcie probiercze U_test:</td><td><strong>").append(escape(d.testVoltageKv)).append(" kV</strong> (f0 = ").append(escape(d.lineFrequencyHz)).append(" Hz)</td></tr>\n");
        sb.append("      <tr><td>Tor pomiarowy:</td><td>").append(escape(d.sensorType)).append("</td></tr>\n");
        sb.append("      <tr><td>Przetwornik / Akwizycja:</td><td>").append(escape(d.samplingInfo)).append("</td></tr>\n");
        sb.append("      <tr><td>Data wykonania raportu:</td><td>").append(dateStr).append("</td></tr>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n");

        // Card 2: Calibration & Executive Summary
        sb.append("  <div class=\"card\">\n");
        sb.append("    <div class=\"card-title\">Kalibracja Ładunku Pozornego (IEC 60270)</div>\n");
        sb.append("    <table class=\"table-kv\">\n");
        sb.append("      <tr><td>Status kalibracji:</td><td>");
        if (d.isCalibrated) {
            sb.append("<span class=\"tag tag-success\">✓ SKALIBROWANY</span></td></tr>\n");
            sb.append("      <tr><td>Współczynnik k_cal:</td><td><strong>").append(String.format(Locale.US, "%.4f", d.calibrationFactor)).append(" pC/mV</strong></td></tr>\n");
            sb.append("      <tr><td>Wzorzec kalibratora:</td><td>").append(escape(d.calibratorName)).append(" (").append(String.format(Locale.US, "%.1f pC", d.targetCharge)).append(")</td></tr>\n");
            sb.append("      <tr><td>Data kalibracji toru:</td><td>").append(escape(d.calibrationDate)).append("</td></tr>\n");
            sb.append("      <tr><td>Wybrana jednostka:</td><td><strong>").append(escape(d.chargeUnit)).append("</strong></td></tr>\n");
        } else {
            sb.append("<span class=\"tag tag-warning\">Nieskalibrowany (tryb surowy mV)</span></td></tr>\n");
            sb.append("      <tr><td>Jednostka ładunku:</td><td>mV (napięcie szczytowe na wejściu ADC)</td></tr>\n");
            sb.append("      <tr><td>Współczynnik k_cal:</td><td>1.0000 (pomiar względny)</td></tr>\n");
            sb.append("      <tr><td>Wzorzec kalibratora:</td><td>").append(escape(d.calibratorName)).append(" (niewykonano kalibracji w bieżącej sesji)</td></tr>\n");
        }
        sb.append("      <tr><td>Uwagi diagnostyczne:</td><td><em>").append(escape(d.notes)).append("</em></td></tr>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // KPI METRICS RIBBON
        if (sr != null) {
            double qMax = Math.max(sr.pos.qMax, sr.neg.qMax);
            double qMaxDisplay = d.isCalibrated ? cal.toDisplay(qMax) : qMax;
            double qMeanDisplay = d.isCalibrated ? cal.toDisplay(Math.max(sr.pos.qMean, sr.neg.qMean)) : Math.max(sr.pos.qMean, sr.neg.qMean);

            sb.append("<div class=\"kpi-ribbon\">\n");
            sb.append("  <div class=\"kpi-card\">\n");
            sb.append("    <div class=\"kpi-label\">Ładunek szczytowy Qmax</div>\n");
            sb.append("    <div class=\"kpi-val\">").append(String.format(Locale.US, "%.2f", qMaxDisplay)).append(" <span class=\"kpi-unit\">").append(u).append("</span></div>\n");
            sb.append("    <div class=\"kpi-sub\">Q+ = ").append(String.format(Locale.US, "%.1f", d.isCalibrated ? cal.toDisplay(sr.pos.qMax) : sr.pos.qMax))
                    .append(" | Q- = ").append(String.format(Locale.US, "%.1f", d.isCalibrated ? cal.toDisplay(sr.neg.qMax) : sr.neg.qMax)).append("</div>\n");
            sb.append("  </div>\n");

            sb.append("  <div class=\"kpi-card\">\n");
            sb.append("    <div class=\"kpi-label\">Częstość impulsów n</div>\n");
            sb.append("    <div class=\"kpi-val\">").append(String.format(Locale.US, "%.1f", sr.repetitionRate)).append(" <span class=\"kpi-unit\">imp/s</span></div>\n");
            sb.append("    <div class=\"kpi-sub\">Suma: ").append(sr.totalPulses).append(" impulsów (t=").append(String.format(Locale.US, "%.2f s", sr.durationSec)).append(")</div>\n");
            sb.append("  </div>\n");

            sb.append("  <div class=\"kpi-card\">\n");
            sb.append("    <div class=\"kpi-label\">Ładunek średni μ_Q</div>\n");
            sb.append("    <div class=\"kpi-val\">").append(String.format(Locale.US, "%.2f", qMeanDisplay)).append(" <span class=\"kpi-unit\">").append(u).append("</span></div>\n");
            sb.append("    <div class=\"kpi-sub\">Asymetria ładunku: Q+/Q- = ").append(String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ)).append("</div>\n");
            sb.append("  </div>\n");

            sb.append("  <div class=\"kpi-card\">\n");
            sb.append("    <div class=\"kpi-label\">Korelacja krzyżowa CC</div>\n");
            sb.append("    <div class=\"kpi-val\">").append(String.format(Locale.US, "%.3f", sr.crossCorrelationCC)).append("</div>\n");
            sb.append("    <div class=\"kpi-sub\">Asymetria zliczeń: N+/N- = ").append(String.format(Locale.US, "%.3f", sr.countAsymmetryN)).append("</div>\n");
            sb.append("  </div>\n");
            sb.append("</div>\n");
        }

        // CHARTS: PRPD & PROFILES
        if (d.includePrpd && d.prpdImage != null) {
            sb.append("<div class=\"card page-break-inside-avoid\">\n");
            sb.append("  <div class=\"card-title\">Obraz Fazowo-Rozdzielczy Wyładowań Niezupełnych (PRPD Pattern — φ-q-n)</div>\n");
            sb.append("  <div class=\"chart-box\">\n");
            sb.append("    <img class=\"report-img\" src=\"data:image/png;base64,").append(toBase64(d.prpdImage)).append("\" alt=\"Wykres PRPD\">\n");
            sb.append("  </div>\n");
            sb.append("  <div class=\"chart-caption\">Rysunek 1: Rozkład fazowy wyładowań niezupełnych PRPD (0–360°) z naniesioną sinusoidą napięcia odniesienia 50 Hz i skalą intensywności wyładowań.</div>\n");
            sb.append("</div>\n");
        }

        if (d.includeProfiles && d.profilesImage != null) {
            sb.append("<div class=\"card page-break-inside-avoid\">\n");
            sb.append("  <div class=\"card-title\">Profile Statystyczne Rozkładu Fazowego Hn(φ), Hqn(φ), Hqmax(φ)</div>\n");
            sb.append("  <div class=\"chart-box\">\n");
            sb.append("    <img class=\"report-img\" src=\"data:image/png;base64,").append(toBase64(d.profilesImage)).append("\" alt=\"Profile statystyczne\">\n");
            sb.append("  </div>\n");
            sb.append("  <div class=\"chart-caption\">Rysunek 2: Trzy skorelowane profile fazowe wg CIGRE WG 21.03: rozkład zliczeń impulsów Hn(φ), średni ładunek pozorny Hqn(φ) oraz obwiednia ładunku maksymalnego Hqmax(φ).</div>\n");
            sb.append("</div>\n");
        }

        // CHARTS: PULSE WAVEFORM & FFT SCOPE
        if (d.includePulseScope && d.pulseScopeImage != null && d.qmaxPulse != null) {
            PulseWaveform p = d.qmaxPulse;
            sb.append("<div class=\"card page-break-inside-avoid\">\n");
            sb.append("  <div class=\"card-title\">Analiza Czasowo-Częstotliwościowa Impulsu Szczytowego Qmax (Time Domain & FFT Spectrum)</div>\n");
            sb.append("  <div class=\"chart-box\">\n");
            sb.append("    <img class=\"report-img\" src=\"data:image/png;base64,").append(toBase64(d.pulseScopeImage)).append("\" alt=\"Analiza oscyloskopowa i FFT\">\n");
            sb.append("  </div>\n");
            sb.append("  <div class=\"chart-caption\">Rysunek 3: Przebieg czasowy impulsu o najwyższej energii (8.19 μs / 1024 próbki @ 125 MS/s) oraz jego transformata Fouriera FFT.</div>\n");

            // Pulse Metric Mini-Table
            sb.append("  <table class=\"table-grid\">\n");
            sb.append("    <thead><tr><th>Czas narastania t_r</th><th>Szerokość FWHM t_w</th><th>Czas opadania t_f</th><th>Czas równoważny T_eq</th><th>Częstotliwość piku f_peak</th><th>Częstotliwość centroidu</th><th>Energia impulsu E</th><th>Polaryzacja</th></tr></thead>\n");
            sb.append("    <tbody><tr>\n");
            sb.append("      <td><strong>").append(String.format(Locale.US, "%.1f ns", p.riseTimeNs)).append("</strong></td>\n");
            sb.append("      <td>").append(String.format(Locale.US, "%.1f ns", p.pulseWidthNs)).append("</td>\n");
            sb.append("      <td>").append(String.format(Locale.US, "%.1f ns", p.fallTimeNs)).append("</td>\n");
            sb.append("      <td>").append(String.format(Locale.US, "%.1f ns", p.equivalentTimeNs)).append("</td>\n");
            dsp.FastFourierTransform.SpectrumResult spec = p.getSpectrum(dsp.FastFourierTransform.WindowType.HANNING, true);
            double peakFreq = (spec != null) ? spec.peakFrequency : 0.0;
            double centFreq = (spec != null) ? spec.spectralCentroid : 0.0;
            sb.append("      <td><strong>").append(String.format(Locale.US, "%.2f MHz", peakFreq / 1e6)).append("</strong></td>\n");
            sb.append("      <td>").append(String.format(Locale.US, "%.2f MHz", centFreq / 1e6)).append("</td>\n");
            sb.append("      <td>").append(String.format(Locale.US, "%.2f mV²·μs", p.energy)).append("</td>\n");
            sb.append("      <td>").append(p.polarity).append(" (φ = ").append(String.format(Locale.US, "%.1f°", p.phase)).append(")</td>\n");
            sb.append("    </tr></tbody>\n");
            sb.append("  </table>\n");
            sb.append("</div>\n");
        }

        // STATISTICAL FINGERPRINT TABLE
        if (d.includeStatisticalTable && sr != null) {
            HalfCycleStats pos = sr.pos;
            HalfCycleStats neg = sr.neg;

            sb.append("<div class=\"card page-break-inside-avoid\">\n");
            sb.append("  <div class=\"card-title\">Kompletny Odcisk Statystyczny WNZ (CIGRE Statistical Fingerprint)</div>\n");
            sb.append("  <table class=\"table-grid\">\n");
            sb.append("    <thead>\n");
            sb.append("      <tr>\n");
            sb.append("        <th>Deskryptor Statystyczny (CIGRE / IEC)</th>\n");
            sb.append("        <th>Półokres Dodatni (+)<br><small>0° – 180°</small></th>\n");
            sb.append("        <th>Półokres Ujemny (-)<br><small>180° – 360°</small></th>\n");
            sb.append("        <th>Relacja Międzypołówkowa / Asymetria</th>\n");
            sb.append("      </tr>\n");
            sb.append("    </thead>\n");
            sb.append("    <tbody>\n");

            // Row: Liczba impulsów N
            sb.append("      <tr>\n");
            sb.append("        <td><strong>Liczba impulsów WNZ (N)</strong></td>\n");
            sb.append("        <td>").append(pos.pulseCount).append(" (").append(String.format(Locale.US, "%.1f%%", pos.pulsePercentage)).append(")</td>\n");
            sb.append("        <td>").append(neg.pulseCount).append(" (").append(String.format(Locale.US, "%.1f%%", neg.pulsePercentage)).append(")</td>\n");
            sb.append("        <td><strong>N+ / N- = ").append(String.format(Locale.US, "%.3f", sr.countAsymmetryN)).append("</strong></td>\n");
            sb.append("      </tr>\n");

            // Row: Qmax
            sb.append("      <tr>\n");
            sb.append("        <td><strong>Ładunek szczytowy (Qmax) [").append(u).append("]</strong></td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.2f %s", d.isCalibrated ? cal.toDisplay(pos.qMax) : pos.qMax, u)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.2f %s", d.isCalibrated ? cal.toDisplay(neg.qMax) : neg.qMax, u)).append("</td>\n");
            sb.append("        <td><strong>Q+ / Q- = ").append(String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ)).append("</strong></td>\n");
            sb.append("      </tr>\n");

            // Row: Mean Q
            sb.append("      <tr>\n");
            sb.append("        <td><strong>Średnia amplituda (μ_Q) [").append(u).append("]</strong></td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.2f %s", d.isCalibrated ? cal.toDisplay(pos.qMean) : pos.qMean, u)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.2f %s", d.isCalibrated ? cal.toDisplay(neg.qMean) : neg.qMean, u)).append("</td>\n");
            sb.append("        <td>μ+ / μ- = ").append(String.format(Locale.US, "%.3f", sr.meanChargeAsymmetry)).append("</td>\n");
            sb.append("      </tr>\n");

            // Row: Std Dev Q
            sb.append("      <tr>\n");
            sb.append("        <td>Odchylenie standardowe (σ_Q) [").append(u).append("]</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.2f %s", d.isCalibrated ? cal.toDisplay(pos.qStd) : pos.qStd, u)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.2f %s", d.isCalibrated ? cal.toDisplay(neg.qStd) : neg.qStd, u)).append("</td>\n");
            sb.append("        <td>—</td>\n");
            sb.append("      </tr>\n");

            // Row: Inception phase
            sb.append("      <tr>\n");
            sb.append("        <td>Kąt fazowy zapłonu (φ_inc) [°]</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.1f°", pos.inceptionPhase)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.1f°", neg.inceptionPhase)).append("</td>\n");
            sb.append("        <td>Δφ_inc = ").append(String.format(Locale.US, "%.1f°", Math.abs(neg.inceptionPhase - pos.inceptionPhase))).append("</td>\n");
            sb.append("      </tr>\n");

            // Row: Phase Mean
            sb.append("      <tr>\n");
            sb.append("        <td>Średni kąt fazowy (μ_φ) [°]</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.1f°", pos.phaseMean)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%.1f°", neg.phaseMean)).append("</td>\n");
            sb.append("        <td>Δμ_φ = ").append(String.format(Locale.US, "%.1f°", Math.abs(neg.phaseMean - pos.phaseMean))).append("</td>\n");
            sb.append("      </tr>\n");

            // Row: Phase Skewness
            sb.append("      <tr>\n");
            sb.append("        <td>Skośność fazowa rozkładu Hn (Sk_φ)</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", pos.phaseSkewness)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", neg.phaseSkewness)).append("</td>\n");
            sb.append("        <td>ΔSk_φ = ").append(String.format(Locale.US, "%.3f", Math.abs(pos.phaseSkewness - neg.phaseSkewness))).append("</td>\n");
            sb.append("      </tr>\n");

            // Row: Phase Kurtosis
            sb.append("      <tr>\n");
            sb.append("        <td>Kurtoza fazowa rozkładu Hn (Ku_φ)</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", pos.phaseKurtosis)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", neg.phaseKurtosis)).append("</td>\n");
            sb.append("        <td>ΔKu_φ = ").append(String.format(Locale.US, "%.3f", Math.abs(pos.phaseKurtosis - neg.phaseKurtosis))).append("</td>\n");
            sb.append("      </tr>\n");

            // Row: Charge Skewness
            sb.append("      <tr>\n");
            sb.append("        <td>Skośność ładunkowa (Sk_Q)</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", pos.ampSkewness)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", neg.ampSkewness)).append("</td>\n");
            sb.append("        <td>—</td>\n");
            sb.append("      </tr>\n");

            // Row: Charge Kurtosis
            sb.append("      <tr>\n");
            sb.append("        <td>Kurtoza ładunkowa (Ku_Q)</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", pos.ampKurtosis)).append("</td>\n");
            sb.append("        <td>").append(String.format(Locale.US, "%+.3f", neg.ampKurtosis)).append("</td>\n");
            sb.append("        <td>—</td>\n");
            sb.append("      </tr>\n");

            // Row: Total Charge
            sb.append("      <tr>\n");
            sb.append("        <td>Całkowity ładunek pozorny (Q_total)</td>\n");
            sb.append("        <td>—</td><td>—</td>\n");
            sb.append("        <td><strong>").append(String.format(Locale.US, "%.1f %s·pts", d.isCalibrated ? cal.toDisplay(sr.totalApparentEnergy) : sr.totalApparentEnergy, u)).append("</strong></td>\n");
            sb.append("      </tr>\n");

            sb.append("    </tbody>\n");
            sb.append("  </table>\n");
            sb.append("</div>\n");
        }

        // AI DIAGNOSIS & EXPERT RECOMMENDATIONS
        if (d.includeAiDiagnosis) {
            sb.append("<div class=\"card page-break-inside-avoid\">\n");
            sb.append("  <div class=\"card-title\">Klasyfikacja Automatyczna AI i Rekomendacje Eksploatacyjne</div>\n");

            sb.append("  <div class=\"grid-2\">\n");

            // AI Model Predictions
            sb.append("    <div>\n");
            sb.append("      <h4 style=\"margin-top:0; color:#1e293b;\">Weryfikacja Modeli Klasyfikacyjnych (YOLO / ML)</h4>\n");
            if (d.aiClassifications != null && !d.aiClassifications.isEmpty()) {
                sb.append("      <table class=\"table-kv\">\n");
                for (Map.Entry<String, String> entry : d.aiClassifications.entrySet()) {
                    sb.append("        <tr><td><strong>").append(escape(entry.getKey())).append(":</strong></td>");
                    sb.append("<td><span class=\"tag tag-primary\">").append(escape(entry.getValue())).append("</span></td></tr>\n");
                }
                sb.append("      </table>\n");
            } else {
                sb.append("      <p class=\"text-muted\"><em>Brak wyników z automatycznego klasyfikatora AI dla bieżącego okna akwizycji.</em></p>\n");
            }

            // CIGRE Pattern Advisory
            if (sr != null && sr.diagnosticHint != null && !sr.diagnosticHint.isEmpty()) {
                sb.append("      <div class=\"advisory-box\">\n");
                sb.append("        <strong>Wskazówka CIGRE WG 21.03:</strong> ").append(escape(sr.diagnosticHint)).append("\n");
                sb.append("      </div>\n");
            }
            sb.append("    </div>\n");

            // Diagnostic Recommendations
            sb.append("    <div>\n");
            sb.append("      <h4 style=\"margin-top:0; color:#1e293b;\">Podsumowanie i Rekomendacje Eksploatacyjne</h4>\n");
            sb.append("      <div class=\"risk-box\" style=\"border-left: 4px solid ").append(riskColor).append("; background:").append(riskBg).append(";\">\n");
            sb.append("        <strong>Ocena stanu izolacji: ").append(riskLevel).append("</strong><br>\n");
            sb.append("        <p style=\"margin:6px 0 0 0;\">").append(escape(d.getRiskDescription())).append("</p>\n");
            sb.append("      </div>\n");
            sb.append("      <ul style=\"font-size:12px; color:#475569; margin-top:10px; padding-left:18px;\">\n");
            sb.append("        <li>Porównać zarejestrowany poziom ładunku szczytowego z dopuszczalnymi wartościami gwarantowanymi producenta obiektu.</li>\n");
            sb.append("        <li>Zweryfikować stabilność częstotliwościową piku widma (f_peak) przy kolejnych pomiarach w celu wczesnego wykrycia postępu degradacji.</li>\n");
            sb.append("        <li>Zarchiwizować plik pomiarowy .rppr.bin dla celów analizy trendów historycznych.</li>\n");
            sb.append("      </ul>\n");
            sb.append("    </div>\n");

            sb.append("  </div>\n");
            sb.append("</div>\n");
        }



        sb.append("<footer class=\"report-footer\">\n");
        sb.append("  Raport wygenerowany automatycznie przez system <strong>PRPD Tool</strong> (Red Pitaya STEMlab 125-14 WNZ Monitor). Zgodność z IEC 60270 / IEC 62478.\n");
        sb.append("</footer>\n");

        sb.append("</div>\n"); // End report-container

        // JS Helpers
        sb.append("<script>\n");
        sb.append("function copySummary() {\n");
        sb.append("  const text = `RAPORT WNZ: ").append(escape(d.objectName)).append("\\n` +\n");
        sb.append("    `Data: ").append(dateStr).append("\\n` +\n");
        sb.append("    `Status: ").append(riskLevel).append("\\n` +\n");
        if (sr != null) {
            double qMaxDisplay = d.isCalibrated ? cal.toDisplay(Math.max(sr.pos.qMax, sr.neg.qMax)) : Math.max(sr.pos.qMax, sr.neg.qMax);
            sb.append("    `Qmax: ").append(String.format(Locale.US, "%.2f", qMaxDisplay)).append(" ").append(u).append("\\n` +\n");
            sb.append("    `Częstość impulsów: ").append(String.format(Locale.US, "%.1f", sr.repetitionRate)).append(" imp/s (Suma: ").append(sr.totalPulses).append(")\\n` +\n");
            sb.append("    `Asymetria Q+/Q-: ").append(String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ)).append("\\n` +\n");
        }
        sb.append("    `Diagnoza: ").append(escape(d.getRiskDescription())).append("`;\n");
        sb.append("  navigator.clipboard.writeText(text).then(() => alert('Skopiowano podsumowanie raportu do schowka!'));\n");
        sb.append("}\n");
        sb.append("</script>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static String toBase64(BufferedImage img) {
        if (img == null) return "";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ex) {
            return "";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String getCss() {
        return """
            :root {
                --primary: #0284c7;
                --primary-dark: #0369a1;
                --bg: #f8fafc;
                --card-bg: #ffffff;
                --border: #e2e8f0;
                --text-main: #0f172a;
                --text-muted: #64748b;
            }
            * { box-sizing: border-box; }
            body {
                font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
                background-color: var(--bg);
                color: var(--text-main);
                margin: 0;
                padding: 0;
                font-size: 13px;
                line-height: 1.5;
            }
            .action-bar {
                background: #0f172a;
                color: #f8fafc;
                padding: 10px 24px;
                display: flex;
                justify-content: space-between;
                align-items: center;
                position: sticky;
                top: 0;
                z-index: 1000;
                box-shadow: 0 2px 8px rgba(0,0,0,0.2);
            }
            .btn {
                padding: 7px 15px;
                border-radius: 6px;
                font-size: 12px;
                font-weight: 600;
                cursor: pointer;
                border: none;
                transition: background 0.2s;
            }
            .btn-primary { background: #0284c7; color: white; }
            .btn-primary:hover { background: #0369a1; }
            .btn-secondary { background: #334155; color: white; margin-right: 8px; }
            .btn-secondary:hover { background: #475569; }
            .report-container {
                max-width: 1060px;
                margin: 20px auto;
                padding: 0 20px 40px 20px;
            }
            .report-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                border-bottom: 2px solid var(--primary);
                padding-bottom: 14px;
                margin-bottom: 20px;
            }
            .report-header h1 {
                margin: 0;
                font-size: 22px;
                color: #0f172a;
                letter-spacing: -0.5px;
            }
            .subtitle {
                font-size: 12px;
                color: var(--text-muted);
                margin-top: 3px;
            }
            .header-badge { text-align: right; }
            .badge-title { font-size: 10px; font-weight: bold; color: var(--text-muted); text-transform: uppercase; margin-bottom: 4px; }
            .risk-pill {
                display: inline-block;
                padding: 4px 14px;
                border-radius: 20px;
                font-size: 13px;
                font-weight: bold;
                letter-spacing: 0.5px;
            }
            .grid-2 {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 16px;
                margin-bottom: 16px;
            }
            .card {
                background: var(--card-bg);
                border: 1px solid var(--border);
                border-radius: 8px;
                padding: 16px 18px;
                margin-bottom: 16px;
                box-shadow: 0 1px 3px rgba(0,0,0,0.03);
            }
            .card-title {
                font-size: 13px;
                font-weight: bold;
                color: #1e293b;
                border-bottom: 1px solid var(--border);
                padding-bottom: 8px;
                margin-bottom: 12px;
            }
            .table-kv {
                width: 100%;
                border-collapse: collapse;
            }
            .table-kv td {
                padding: 4px 0;
                font-size: 12px;
            }
            .table-kv td:first-child {
                color: var(--text-muted);
                width: 45%;
            }
            .kpi-ribbon {
                display: grid;
                grid-template-columns: repeat(4, 1fr);
                gap: 14px;
                margin-bottom: 18px;
            }
            .kpi-card {
                background: var(--card-bg);
                border: 1px solid var(--border);
                border-radius: 8px;
                padding: 14px;
                text-align: center;
                border-top: 3px solid var(--primary);
            }
            .kpi-label { font-size: 11px; color: var(--text-muted); font-weight: 600; text-transform: uppercase; }
            .kpi-val { font-size: 22px; font-weight: 800; color: #0f172a; margin: 4px 0; }
            .kpi-unit { font-size: 13px; font-weight: 600; color: var(--primary); }
            .kpi-sub { font-size: 10px; color: var(--text-muted); }
            .chart-box {
                text-align: center;
                background: #111827;
                border-radius: 6px;
                overflow: hidden;
                border: 1px solid #374151;
            }
            .report-img {
                width: 100%;
                height: auto;
                display: block;
            }
            .chart-caption {
                font-size: 11px;
                color: var(--text-muted);
                font-style: italic;
                margin-top: 6px;
                text-align: center;
            }
            .table-grid {
                width: 100%;
                border-collapse: collapse;
                margin-top: 10px;
                font-size: 11.5px;
            }
            .table-grid th, .table-grid td {
                border: 1px solid var(--border);
                padding: 6px 10px;
                text-align: left;
            }
            .table-grid th {
                background: #f1f5f9;
                color: #334155;
                font-weight: 600;
            }
            .table-grid tbody tr:nth-child(even) { background-color: #f8fafc; }
            .table-grid tbody tr:hover { background-color: #f1f5f9; }
            .tag {
                display: inline-block;
                padding: 2px 8px;
                border-radius: 4px;
                font-size: 10.5px;
                font-weight: 600;
            }
            .tag-success { background: #dcfce7; color: #166534; }
            .tag-warning { background: #fef3c7; color: #92400e; }
            .tag-primary { background: #e0f2fe; color: #0369a1; }
            .advisory-box {
                background: #eff6ff;
                border: 1px solid #bfdbfe;
                border-radius: 6px;
                padding: 10px;
                font-size: 11.5px;
                color: #1e40af;
                margin-top: 10px;
            }
            .risk-box {
                border-radius: 6px;
                padding: 10px 14px;
                font-size: 12px;
            }
            .signatures-card {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 40px;
                background: var(--card-bg);
                border: 1px solid var(--border);
                border-radius: 8px;
                padding: 24px;
                margin-top: 20px;
            }
            .signature-box { text-align: center; }
            .sig-line {
                border-bottom: 1px solid #94a3b8;
                height: 45px;
                margin-bottom: 8px;
            }
            .sig-label { font-size: 11px; color: var(--text-muted); }
            .report-footer {
                text-align: center;
                font-size: 10.5px;
                color: var(--text-muted);
                margin-top: 25px;
                padding-top: 15px;
                border-top: 1px solid var(--border);
            }
            @media print {
                .no-print { display: none !important; }
                body { background: white !important; font-size: 11px !important; }
                .report-container { max-width: 100% !important; margin: 0 !important; padding: 0 !important; }
                .card { border: 1px solid #cbd5e1 !important; box-shadow: none !important; margin-bottom: 12px !important; }
                .page-break-inside-avoid { page-break-inside: avoid; }
                * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
            }
        """;
    }
}
