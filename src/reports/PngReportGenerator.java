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
 * Generates an executive, high-resolution composite PNG Diagnostic Report image
 * containing metadata, KPI metrics, embedded charts, and statistical tables.
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

        // Background
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
        g.setFont(new Font("Segoe UI", Font.BOLD, 20));
        g.drawString("RAPORT DIAGNOSTYCZNY WYŁADOWAŃ NIEZUPEŁNYCH", 30, 36);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.setColor(new Color(148, 163, 184));
        g.drawString("Zgodny z IEC 60270, IEC 62478 oraz CIGRE WG 21.03 | " + data.reportId, 30, 58);

        // Risk Badge in header
        String risk = data.getDiagnosticRiskLevel();
        Color riskCol = "KRYTYCZNY".equals(risk) ? new Color(239, 68, 68) : ("OSTRZEGAWCZY".equals(risk) ? new Color(245, 158, 11) : new Color(16, 185, 129));
        g.setColor(riskCol);
        g.fillRoundRect(w - 200, 24, 170, 32, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        int rTxtW = g.getFontMetrics().stringWidth(risk);
        g.drawString(risk, w - 200 + (170 - rTxtW) / 2, 45);

        curY = headerH + 20;

        // 2. METADATA & CALIBRATION DUAL CARDS
        int cardMargin = 30;
        int cardGap = 20;
        int cardW = (w - 2 * cardMargin - cardGap) / 2;
        int metaCardH = 160;

        drawCard(g, cardMargin, curY, cardW, metaCardH, "Dane Obiektu i Badania");
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(51, 65, 85));
        int my = curY + 45;
        drawKv(g, cardMargin + 15, my, "Obiekt badany:", data.objectName, 120);
        drawKv(g, cardMargin + 15, my + 20, "Pole / Faza:", data.bayOrPhase, 120);
        drawKv(g, cardMargin + 15, my + 40, "Napięcie U_test:", data.testVoltageKv + " kV (" + data.lineFrequencyHz + " Hz)", 120);
        drawKv(g, cardMargin + 15, my + 60, "Diagnosta:", data.operatorName, 120);
        drawKv(g, cardMargin + 15, my + 80, "Data badania:", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(data.timestamp), 120);

        int rightCardX = cardMargin + cardW + cardGap;
        drawCard(g, rightCardX, curY, cardW, metaCardH, "Kalibracja IEC 60270");
        int cy = curY + 45;
        drawKv(g, rightCardX + 15, cy, "Status:", data.isCalibrated ? "SKALIBROWANY (k=" + String.format(Locale.US, "%.3f pC/mV)", data.calibrationFactor) : "Nieskalibrowany (mV)", 120);
        drawKv(g, rightCardX + 15, cy + 20, "Jednostka:", data.isCalibrated ? data.chargeUnit : "mV", 120);
        drawKv(g, rightCardX + 15, cy + 40, "Wzorzec ładunku:", data.calibratorName + (data.isCalibrated ? " (" + data.targetCharge + " pC)" : ""), 120);
        drawKv(g, rightCardX + 15, cy + 60, "Akwizycja:", data.samplingInfo, 120);
        drawKv(g, rightCardX + 15, cy + 80, "Uwagi:", data.notes, 120);

        curY += metaCardH + 18;

        // 3. KPI METRIC RIBBON
        StatisticalResult sr = data.statisticalResult;
        if (sr != null) {
            int kpiH = 75;
            int kpiW = (w - 2 * cardMargin - 3 * 15) / 4;
            ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
            String u = data.isCalibrated ? data.chargeUnit : "mV";

            double qMax = Math.max(sr.pos.qMax, sr.neg.qMax);
            double qMaxDisp = data.isCalibrated ? cal.toDisplay(qMax) : qMax;

            drawKpi(g, cardMargin, curY, kpiW, kpiH, "Ładunek szczytowy Qmax", String.format(Locale.US, "%.1f %s", qMaxDisp, u), "Q+ = " + String.format(Locale.US, "%.1f", data.isCalibrated ? cal.toDisplay(sr.pos.qMax) : sr.pos.qMax));
            drawKpi(g, cardMargin + kpiW + 15, curY, kpiW, kpiH, "Częstość impulsów n", String.format(Locale.US, "%.1f imp/s", sr.repetitionRate), "Suma: " + sr.totalPulses + " imp.");
            drawKpi(g, cardMargin + 2 * (kpiW + 15), curY, kpiW, kpiH, "Asymetria ładunku Q+/Q-", String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ), "Asym. N+/N- = " + String.format(Locale.US, "%.3f", sr.countAsymmetryN));
            drawKpi(g, cardMargin + 3 * (kpiW + 15), curY, kpiW, kpiH, "Korelacja krzyżowa CC", String.format(Locale.US, "%.3f", sr.crossCorrelationCC), "Ocena: " + risk);

            curY += kpiH + 20;
        }

        // 4. PRPD CHART
        if (data.includePrpd && data.prpdImage != null) {
            int chartBoxW = w - 2 * cardMargin;
            int chartBoxH = 340;
            drawCard(g, cardMargin, curY, chartBoxW, chartBoxH, "Obraz Fazowo-Rozdzielczy PRPD (φ-q-n)");
            g.drawImage(data.prpdImage, cardMargin + 12, curY + 36, chartBoxW - 24, chartBoxH - 48, null);
            curY += chartBoxH + 16;
        }

        // 5. PHASE PROFILES CHART
        if (data.includeProfiles && data.profilesImage != null) {
            int chartBoxW = w - 2 * cardMargin;
            int chartBoxH = 360;
            drawCard(g, cardMargin, curY, chartBoxW, chartBoxH, "Profile Statystyczne Rozkładu Fazowego Hn(φ), Hqn(φ), Hqmax(φ)");
            g.drawImage(data.profilesImage, cardMargin + 12, curY + 36, chartBoxW - 24, chartBoxH - 48, null);
            curY += chartBoxH + 16;
        }

        // 6. PULSE & FFT SCOPE
        if (data.includePulseScope && data.pulseScopeImage != null) {
            int chartBoxW = w - 2 * cardMargin;
            int chartBoxH = 320;
            drawCard(g, cardMargin, curY, chartBoxW, chartBoxH, "Analiza Czasowo-Częstotliwościowa Impulsu Szczytowego Qmax (Time Domain & FFT)");
            g.drawImage(data.pulseScopeImage, cardMargin + 12, curY + 36, chartBoxW - 24, chartBoxH - 48, null);
            curY += chartBoxH + 16;
        }

        // 7. STATISTICAL TABLE
        if (data.includeStatisticalTable && sr != null) {
            int tableW = w - 2 * cardMargin;
            int tableH = 180;
            drawCard(g, cardMargin, curY, tableW, tableH, "Kluczowe Wskaźniki Odcisku Statystycznego CIGRE");

            ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
            String u = data.isCalibrated ? data.chargeUnit : "mV";

            int ty = curY + 45;
            g.setColor(new Color(241, 245, 249));
            g.fillRect(cardMargin + 10, ty, tableW - 20, 24);

            g.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g.setColor(new Color(30, 41, 59));
            g.drawString("Parametr", cardMargin + 20, ty + 16);
            g.drawString("Półokres Dodatni (+)", cardMargin + 320, ty + 16);
            g.drawString("Półokres Ujemny (-)", cardMargin + 580, ty + 16);
            g.drawString("Asymetria / Relacja", cardMargin + 840, ty + 16);

            ty += 28;
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            drawTableRow(g, cardMargin + 20, ty, "Liczba impulsów (N)", String.valueOf(sr.pos.pulseCount), String.valueOf(sr.neg.pulseCount), "N+/N- = " + String.format(Locale.US, "%.3f", sr.countAsymmetryN));
            ty += 22;
            drawTableRow(g, cardMargin + 20, ty, "Ładunek szczytowy (Qmax)", String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.pos.qMax) : sr.pos.qMax, u), String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.neg.qMax) : sr.neg.qMax, u), "Q+/Q- = " + String.format(Locale.US, "%.3f", sr.chargeAsymmetryQ));
            ty += 22;
            drawTableRow(g, cardMargin + 20, ty, "Średnia amplituda (μ_Q)", String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.pos.qMean) : sr.pos.qMean, u), String.format(Locale.US, "%.2f %s", data.isCalibrated ? cal.toDisplay(sr.neg.qMean) : sr.neg.qMean, u), "μ+/μ- = " + String.format(Locale.US, "%.3f", sr.meanChargeAsymmetry));
            ty += 22;
            drawTableRow(g, cardMargin + 20, ty, "Kąt zapłonu (φ_inc)", String.format(Locale.US, "%.1f°", sr.pos.inceptionPhase), String.format(Locale.US, "%.1f°", sr.neg.inceptionPhase), "Δφ = " + String.format(Locale.US, "%.1f°", Math.abs(sr.neg.inceptionPhase - sr.pos.inceptionPhase)));
            ty += 22;
            drawTableRow(g, cardMargin + 20, ty, "Skośność fazowa (Sk_φ)", String.format(Locale.US, "%+.3f", sr.pos.phaseSkewness), String.format(Locale.US, "%+.3f", sr.neg.phaseSkewness), "ΔSk = " + String.format(Locale.US, "%.3f", Math.abs(sr.pos.phaseSkewness - sr.neg.phaseSkewness)));

            curY += tableH + 16;
        }

        // 8. SIGNATURES & FOOTER
        int sigH = 80;
        int sigW = w - 2 * cardMargin;
        drawCard(g, cardMargin, curY, sigW, sigH, "Zatwierdzenie Raportu");
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(71, 85, 105));
        g.drawString("Diagnosta: " + data.operatorName + "  ___________________________", cardMargin + 30, curY + 52);
        g.drawString("Zatwierdzający: Inżynier Prowadzący  ___________________________", cardMargin + sigW / 2 + 30, curY + 52);

        g.dispose();

        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        ImageIO.write(img, "png", outputFile);
        return outputFile;
    }

    private static int calculateHeight(DiagnosticReportData d) {
        int h = 80 + 20 + 160 + 18 + 75 + 20; // Banner + Meta + KPI
        if (d.includePrpd && d.prpdImage != null) h += 340 + 16;
        if (d.includeProfiles && d.profilesImage != null) h += 360 + 16;
        if (d.includePulseScope && d.pulseScopeImage != null) h += 320 + 16;
        if (d.includeStatisticalTable && d.statisticalResult != null) h += 180 + 16;
        h += 80 + 30; // Signatures + Bottom pad
        return h;
    }

    private static void drawCard(Graphics2D g, int x, int y, int w, int h, String title) {
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(new Color(226, 232, 240));
        g.drawRoundRect(x, y, w, h, 8, 8);

        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(30, 41, 59));
        g.drawString(title, x + 15, y + 22);

        g.setColor(new Color(226, 232, 240));
        g.drawLine(x, y + 30, x + w, y + 30);
    }

    private static void drawKv(Graphics2D g, int x, int y, String k, String v, int kWidth) {
        g.setColor(new Color(100, 116, 139));
        g.drawString(k, x, y);
        g.setColor(new Color(15, 23, 42));
        g.drawString(v != null ? v : "-", x + kWidth, y);
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
        g.drawString(c2, x + 300, y);
        g.drawString(c3, x + 560, y);
        g.drawString(c4, x + 820, y);
    }
}
