package reports;

import pipeline.PulseWaveform;
import pipeline.StatisticalAnalyzer.StatisticalResult;
import prpdtool.ChargeCalibrationManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Executive Portrait Graphic PNG Diagnostic Report Generator.
 * Generates an elegant, print-ready document image (1200 px wide, portrait A4-proportioned)
 * compliant with IEC 60270, IEC 62478, and CIGRE WG 21.03 / D1.33.
 */
public class PngReportGenerator {

    public static File generateReport(DiagnosticReportData data, File outputFile) throws IOException {
        int w = 1200;
        int h = calculateHeight(data);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Document Background
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, w, h);

        int curY = 0;

        // 1. TOP HEADER BANNER
        int headerH = 80;
        g.setColor(new Color(15, 23, 42));
        g.fillRect(0, 0, w, headerH);

        g.setColor(new Color(2, 132, 199));
        g.fillRect(0, headerH - 4, w, 4);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 19));
        g.drawString("RAPORT DIAGNOSTYCZNY WYŁADOWAŃ NIEZUPEŁNYCH", 30, 36);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(148, 163, 184));
        g.drawString("Diagnostyka wysokonapięciowa wg PN-EN / IEC 60270 oraz CIGRE WG 21.03 | Identyfikator: " + data.reportId, 30, 58);

        // Diagnostic Risk Badge
        String risk = data.getDiagnosticRiskLevel();
        Color riskCol = "KRYTYCZNY".equals(risk) ? new Color(239, 68, 68) : ("OSTRZEGAWCZY".equals(risk) ? new Color(245, 158, 11) : new Color(16, 185, 129));
        int badgeW = 170;
        int badgeH = 34;
        int badgeX = w - badgeW - 30;
        int badgeY = 23;
        g.setColor(riskCol);
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        int rTxtW = g.getFontMetrics().stringWidth(risk);
        g.drawString(risk, badgeX + (badgeW - rTxtW) / 2, badgeY + 22);

        curY = headerH + 18;

        // 2. METADATA & CALIBRATION DUAL CARDS
        int cardMargin = 30;
        int cardGap = 16;
        int cardW = (w - 2 * cardMargin - cardGap) / 2;
        int metaCardH = 160;

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(data.timestamp);

        // Left card: Object & Test Parameters
        drawCard(g, cardMargin, curY, cardW, metaCardH, "Dane Obiektu i Parametry Pomiaru");
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(51, 65, 85));
        int my = curY + 46;
        drawKv(g, cardMargin + 16, my, "Obiekt badany:", data.objectName, 150);
        drawKv(g, cardMargin + 16, my + 22, "Pole / Faza:", data.bayOrPhase, 150);
        drawKv(g, cardMargin + 16, my + 44, "Lokalizacja:", data.location, 150);
        drawKv(g, cardMargin + 16, my + 66, "Napięcie U_test:", data.testVoltageKv + " kV (" + data.lineFrequencyHz + " Hz)", 150);
        drawKv(g, cardMargin + 16, my + 88, "Data wykonania raportu:", dateStr, 150);

        // Right card: Calibration
        int rightCardX = cardMargin + cardW + cardGap;
        drawCard(g, rightCardX, curY, cardW, metaCardH, "Kalibracja Ładunku Pozornego (IEC 60270)");
        int cy = curY + 46;
        drawKv(g, rightCardX + 16, cy, "Status kalibracji:", data.isCalibrated ? "SKALIBROWANY (k = " + String.format(Locale.US, "%.4f pC/mV)", data.calibrationFactor) : "Nieskalibrowany (tryb surowy mV)", 150);
        drawKv(g, rightCardX + 16, cy + 22, "Jednostka ładunku:", data.isCalibrated ? data.chargeUnit : "mV (napięcie ADC)", 150);
        drawKv(g, rightCardX + 16, cy + 44, "Wzorzec kalibratora:", data.calibratorName + (data.isCalibrated ? " (" + String.format(Locale.US, "%.1f pC", data.targetCharge) + ")" : ""), 150);
        drawKv(g, rightCardX + 16, cy + 66, "Tor pomiarowy:", data.sensorType, 150);
        drawKv(g, rightCardX + 16, cy + 88, "Akwizycja / Sprzęt:", data.samplingInfo, 150);

        curY += metaCardH + 16;

        // 3. KPI METRIC RIBBON
        StatisticalResult sr = data.statisticalResult;
        if (sr != null) {
            int kpiH = 74;
            int kpiW = (w - 2 * cardMargin - 3 * 12) / 4;
            ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
            String u = data.isCalibrated ? data.chargeUnit : "mV";

            double qMax = Math.max(sr.pos.qMax, sr.neg.qMax);
            double qMaxDisp = data.isCalibrated ? cal.toDisplay(qMax) : qMax;

            drawKpi(g, cardMargin, curY, kpiW, kpiH, "Ładunek szczytowy Qmax",
                    String.format(Locale.US, "%.1f %s", qMaxDisp, u),
                    "Q+ = " + String.format(Locale.US, "%.1f", data.isCalibrated ? cal.toDisplay(sr.pos.qMax) : sr.pos.qMax) + " | Q- = " + String.format(Locale.US, "%.1f", data.isCalibrated ? cal.toDisplay(sr.neg.qMax) : sr.neg.qMax));

            drawKpi(g, cardMargin + kpiW + 12, curY, kpiW, kpiH, "Częstość impulsów n",
                    String.format(Locale.US, "%.1f imp/s", sr.repetitionRate),
                    "Suma: " + sr.totalPulses + " impulsów");

            drawKpi(g, cardMargin + 2 * (kpiW + 12), curY, kpiW, kpiH, "Asymetria ładunku Q+/Q-",
                    String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ),
                    "Asymetria zliczeń N+/N- = " + String.format(Locale.US, "%.3f", sr.countAsymmetryN));

            drawKpi(g, cardMargin + 3 * (kpiW + 12), curY, kpiW, kpiH, "Korelacja krzyżowa CC",
                    String.format(Locale.US, "%.3f", sr.crossCorrelationCC),
                    "Ocena CIGRE: " + risk);

            curY += kpiH + 16;
        }

        int fullCardW = w - 2 * cardMargin;

        // 4. PRPD CHART (Un-squashed, dynamic height)
        if (data.includePrpd && data.prpdImage != null) {
            int imgH = data.prpdImage.getHeight();
            int chartBoxH = imgH + 46;
            drawCard(g, cardMargin, curY, fullCardW, chartBoxH, "Obraz Fazowo-Rozdzielczy PRPD (φ-q-n)");
            g.drawImage(data.prpdImage, cardMargin + 12, curY + 36, fullCardW - 24, imgH, null);
            curY += chartBoxH + 16;
        }

        // 5. PHASE PROFILES CHART (Un-squashed, dynamic height)
        if (data.includeProfiles && data.profilesImage != null) {
            int imgH = data.profilesImage.getHeight();
            int chartBoxH = imgH + 46;
            drawCard(g, cardMargin, curY, fullCardW, chartBoxH, "Profile Statystyczne Rozkładu Fazowego Hn(φ), Hqn(φ), Hqmax(φ)");
            g.drawImage(data.profilesImage, cardMargin + 12, curY + 36, fullCardW - 24, imgH, null);
            curY += chartBoxH + 16;
        }

        // 6. PULSE & FFT SCOPE (Un-squashed, dynamic height)
        if (data.includePulseScope && data.pulseScopeImage != null) {
            int imgH = data.pulseScopeImage.getHeight();
            int chartBoxH = imgH + 46;
            drawCard(g, cardMargin, curY, fullCardW, chartBoxH, "Analiza Czasowo-Częstotliwościowa Impulsu Szczytowego Qmax (Time Domain & FFT)");
            g.drawImage(data.pulseScopeImage, cardMargin + 12, curY + 36, fullCardW - 24, imgH, null);
            curY += chartBoxH + 16;
        }

        // 7. STATISTICAL TABLE
        if (data.includeStatisticalTable && sr != null) {
            int tableH = 195;
            drawCard(g, cardMargin, curY, fullCardW, tableH, "Kluczowe Wskaźniki Odcisku Statystycznego CIGRE");

            ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
            String u = data.isCalibrated ? data.chargeUnit : "mV";

            int ty = curY + 42;
            g.setColor(new Color(241, 245, 249));
            g.fillRect(cardMargin + 12, ty, fullCardW - 24, 24);

            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g.setColor(new Color(30, 41, 59));
            g.drawString("Deskryptor Statystyczny (CIGRE / IEC)", cardMargin + 24, ty + 16);
            g.drawString("Półokres Dodatni (+)", cardMargin + 340, ty + 16);
            g.drawString("Półokres Ujemny (-)", cardMargin + 600, ty + 16);
            g.drawString("Asymetria / Relacja", cardMargin + 860, ty + 16);

            ty += 28;
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            drawTableRow(g, cardMargin + 24, ty, "Liczba impulsów (N)",
                    String.valueOf(sr.pos.pulseCount) + " (" + String.format(Locale.US, "%.1f%%", sr.pos.pulsePercentage) + ")",
                    String.valueOf(sr.neg.pulseCount) + " (" + String.format(Locale.US, "%.1f%%", sr.neg.pulsePercentage) + ")",
                    "N+/N- = " + String.format(Locale.US, "%.3f", sr.countAsymmetryN));

            ty += 22;
            drawTableRow(g, cardMargin + 24, ty, "Ładunek szczytowy (Qmax)",
                    String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.pos.qMax) : sr.pos.qMax, u),
                    String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.neg.qMax) : sr.neg.qMax, u),
                    "Q+/Q- = " + String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ));

            ty += 22;
            drawTableRow(g, cardMargin + 24, ty, "Średnia amplituda (μ_Q)",
                    String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.pos.qMean) : sr.pos.qMean, u),
                    String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.neg.qMean) : sr.neg.qMean, u),
                    "μ+/μ- = " + String.format(Locale.US, "%.3f", sr.meanChargeAsymmetry));

            ty += 22;
            drawTableRow(g, cardMargin + 24, ty, "Kąt zapłonu (φ_inc)",
                    String.format(Locale.US, "%.1f°", sr.pos.inceptionPhase),
                    String.format(Locale.US, "%.1f°", sr.neg.inceptionPhase),
                    "Δφ_inc = " + String.format(Locale.US, "%.1f°", Math.abs(sr.neg.inceptionPhase - sr.pos.inceptionPhase)));

            ty += 22;
            drawTableRow(g, cardMargin + 24, ty, "Skośność fazowa (Sk_φ)",
                    String.format(Locale.US, "%+.3f", sr.pos.phaseSkewness),
                    String.format(Locale.US, "%+.3f", sr.neg.phaseSkewness),
                    "ΔSk = " + String.format(Locale.US, "%.3f", Math.abs(sr.pos.phaseSkewness - sr.neg.phaseSkewness)));

            curY += tableH + 16;
        }

        // 8. FOOTER NOTE (NO SIGNATURES)
        g.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        g.setColor(new Color(100, 116, 139));
        g.drawString("Raport wygenerowany automatycznie przez system PRPD Tool (Red Pitaya STEMlab 125-14 WNZ Monitor). Zgodność z IEC 60270 / IEC 62478.", cardMargin + 6, curY + 20);

        g.dispose();

        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        ImageIO.write(img, "png", outputFile);
        return outputFile;
    }

    private static int calculateHeight(DiagnosticReportData d) {
        int h = 80 + 18 + 160 + 16; // Header + Meta
        if (d.statisticalResult != null) h += 74 + 16; // KPI
        if (d.includePrpd && d.prpdImage != null) h += d.prpdImage.getHeight() + 46 + 16;
        if (d.includeProfiles && d.profilesImage != null) h += d.profilesImage.getHeight() + 46 + 16;
        if (d.includePulseScope && d.pulseScopeImage != null) h += d.pulseScopeImage.getHeight() + 46 + 16;
        if (d.includeStatisticalTable && d.statisticalResult != null) h += 195 + 16;
        h += 40; // Footer + pad
        return h;
    }

    private static void drawCard(Graphics2D g, int x, int y, int w, int h, String title) {
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(new Color(226, 232, 240));
        g.drawRoundRect(x, y, w, h, 8, 8);

        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(30, 41, 59));
        g.drawString(title, x + 16, y + 24);

        g.setColor(new Color(226, 232, 240));
        g.drawLine(x, y + 32, x + w, y + 32);
    }

    private static void drawKv(Graphics2D g, int x, int y, String k, String v, int kWidth) {
        g.setColor(new Color(100, 116, 139));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.drawString(k, x, y);
        g.setColor(new Color(15, 23, 42));
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g.drawString(v != null ? v : "-", x + kWidth, y);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
    }

    private static void drawKpi(Graphics2D g, int x, int y, int w, int h, String label, String val, String sub) {
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(new Color(226, 232, 240));
        g.drawRoundRect(x, y, w, h, 8, 8);

        g.setColor(new Color(2, 132, 199));
        g.fillRect(x, y, w, 3);

        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.setColor(new Color(100, 116, 139));
        g.drawString(label.toUpperCase(), x + 12, y + 18);

        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(new Color(15, 23, 42));
        g.drawString(val, x + 12, y + 42);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g.setColor(new Color(100, 116, 139));
        g.drawString(sub, x + 12, y + 62);
    }

    private static void drawTableRow(Graphics2D g, int x, int y, String c1, String c2, String c3, String c4) {
        g.setColor(new Color(15, 23, 42));
        g.drawString(c1, x, y);
        g.drawString(c2, x + 316, y);
        g.drawString(c3, x + 576, y);
        g.drawString(c4, x + 836, y);
    }
}
