package reports;

import dsp.FastFourierTransform;
import pipeline.DynamicPRPDHistogram;
import pipeline.PRPDHistogram;
import pipeline.PulseWaveform;
import pipeline.StatisticalAnalyzer.StatisticalResult;
import prpdtool.ChargeCalibrationManager;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * High-quality chart renderer for IEC 60270 / CIGRE Diagnostic Reports.
 * Generates standalone BufferedImage plots for PRPD, Phase Profiles,
 * and Single Pulse Time/Frequency Scope.
 */
public class ReportChartRenderer {

    /**
     * Renders PRPD Histogram snapshot.
     */
    public static BufferedImage renderPRPD(PRPDHistogram hist, int targetW, int targetH) {
        if (hist == null) {
            return createPlaceholderImage(targetW, targetH, "Brak danych PRPD do wygenerowania wykresu");
        }

        BufferedImage base = hist.getImage();
        if (base == null) {
            return createPlaceholderImage(targetW, targetH, "Brak obrazu PRPD");
        }

        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill background
        g2.setColor(new Color(25, 27, 32));
        g2.fillRect(0, 0, targetW, targetH);

        // Draw the PRPD image fitted
        g2.drawImage(base, 0, 0, targetW, targetH, null);

        // Draw outer subtle border
        g2.setColor(new Color(60, 65, 75));
        g2.drawRect(0, 0, targetW - 1, targetH - 1);

        g2.dispose();
        return out;
    }

    /**
     * Renders 3 Phase Profiles: Hn(phi), Hqn(phi), Hqmax(phi) with 50 Hz sine reference.
     */
    public static BufferedImage renderProfiles(StatisticalResult result, int w, int h, boolean darkTheme) {
        if (result == null || result.profiles == null) {
            return createPlaceholderImage(w, h, "Brak profili fazowych (brak zarejestrowanych wyładowań)");
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color bg = darkTheme ? new Color(20, 22, 26) : new Color(250, 252, 255);
        Color boxBg = darkTheme ? new Color(16, 18, 22) : Color.WHITE;
        Color borderCol = darkTheme ? new Color(50, 55, 65) : new Color(210, 215, 225);
        Color textCol = darkTheme ? new Color(220, 225, 235) : new Color(30, 40, 55);
        Color textMuted = darkTheme ? new Color(140, 145, 155) : new Color(110, 120, 135);

        g.setColor(bg);
        g.fillRect(0, 0, w, h);

        int leftMargin = 65;
        int rightMargin = 30;
        int topMargin = 26;
        int bottomMargin = 45;
        int gap = 18;
        int headerH = 18;

        int totalPlotH = h - topMargin - bottomMargin;
        int singleSlotH = (totalPlotH - 2 * gap) / 3;
        int boxH = singleSlotH - headerH;
        int plotW = w - leftMargin - rightMargin;

        if (plotW <= 50 || boxH <= 20) {
            g.dispose();
            return img;
        }

        int slotY1 = topMargin;
        int slotY2 = slotY1 + singleSlotH + gap;
        int slotY3 = slotY2 + singleSlotH + gap;

        ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
        String u = cal.getUnitLabel();

        // 1. Hn
        drawTitleHeader(g, leftMargin, slotY1, "Hn(φ) — Liczba impulsów WNZ / Krok fazowy [Counts]",
                new Color(0, 180, 240), textCol);
        drawChartBox(g, leftMargin, slotY1 + headerH, plotW, boxH, result.profiles.maxHn, false,
                boxBg, borderCol, textMuted, darkTheme);
        drawHnData(g, leftMargin, slotY1 + headerH, plotW, boxH, result.profiles);

        // 2. Hqn
        drawTitleHeader(g, leftMargin, slotY2, "Hqn(φ) — Średnia amplituda ładunku [" + u + "]",
                new Color(30, 200, 125), textCol);
        drawChartBox(g, leftMargin, slotY2 + headerH, plotW, boxH, cal.toDisplay(result.profiles.maxHqn), false,
                boxBg, borderCol, textMuted, darkTheme);
        drawHqnData(g, leftMargin, slotY2 + headerH, plotW, boxH, result.profiles);

        // 3. Hqmax
        drawTitleHeader(g, leftMargin, slotY3, "Hqmax(φ) — Obwiednia ładunku maksymalnego [" + u + "]",
                new Color(245, 80, 60), textCol);
        drawChartBox(g, leftMargin, slotY3 + headerH, plotW, boxH, cal.toDisplay(result.profiles.maxHqmax), true,
                boxBg, borderCol, textMuted, darkTheme);
        drawHqmaxData(g, leftMargin, slotY3 + headerH, plotW, boxH, result.profiles);

        g.dispose();
        return img;
    }

    private static void drawTitleHeader(Graphics2D g, int x0, int slotY, String title, Color dotCol, Color textCol) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(dotCol);
        g.fillOval(x0, slotY + 2, 8, 8);
        g.setColor(textCol);
        g.drawString(title, x0 + 14, slotY + 10);
    }

    private static void drawChartBox(Graphics2D g, int x0, int boxY, int w, int boxH,
                                     double maxY, boolean isBottomChart,
                                     Color boxBg, Color borderCol, Color textMuted, boolean darkTheme) {
        g.setColor(boxBg);
        g.fillRect(x0, boxY, w, boxH);

        int midX = x0 + w / 2;
        // Positive & Negative half tints
        g.setColor(darkTheme ? new Color(0, 150, 255, 14) : new Color(0, 120, 255, 10));
        g.fillRect(x0, boxY, w / 2, boxH);
        g.setColor(darkTheme ? new Color(180, 80, 255, 14) : new Color(160, 60, 255, 10));
        g.fillRect(midX, boxY, w / 2, boxH);

        // 50 Hz Sine reference
        g.setColor(darkTheme ? new Color(255, 195, 60, 70) : new Color(200, 140, 20, 80));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
        int prevSineX = x0;
        int prevSineY = boxY + boxH / 2;
        for (int step = 0; step <= w; step += 3) {
            double phaseRad = (double) step / w * 2.0 * Math.PI;
            int sy = boxY + boxH / 2 - (int) Math.round(Math.sin(phaseRad) * (boxH * 0.44));
            int sx = x0 + step;
            if (step > 0) g.drawLine(prevSineX, prevSineY, sx, sy);
            prevSineX = sx;
            prevSineY = sy;
        }

        // Grid
        g.setColor(darkTheme ? new Color(42, 46, 54) : new Color(230, 235, 242));
        g.setStroke(new BasicStroke(1.0f));
        for (int deg = 45; deg < 360; deg += 45) {
            int gx = x0 + (int) Math.round((double) deg / 360.0 * w);
            g.drawLine(gx, boxY, gx, boxY + boxH);
        }

        // Zero-crossing divider at 180 deg
        g.setColor(darkTheme ? new Color(120, 130, 145, 160) : new Color(150, 160, 175, 180));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 3}, 0));
        g.drawLine(midX, boxY, midX, boxY + boxH);

        // Border
        g.setColor(borderCol);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRect(x0, boxY, w, boxH);

        // Y ticks
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g.setColor(textMuted);
        String topLabel = (maxY >= 10.0) ? String.format(Locale.US, "%.0f", maxY) : String.format(Locale.US, "%.2f", maxY);
        g.drawString(topLabel, x0 - 38, boxY + 11);
        g.drawString("0", x0 - 18, boxY + boxH);

        // X phase labels
        if (isBottomChart) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(textMuted);
            for (int deg = 0; deg <= 360; deg += 45) {
                int tx = x0 + (int) Math.round((double) deg / 360.0 * w);
                String lbl = deg + "°";
                int tw = g.getFontMetrics().stringWidth(lbl);
                g.drawString(lbl, tx - tw / 2, boxY + boxH + 18);
                g.setColor(borderCol);
                g.drawLine(tx, boxY + boxH, tx, boxY + boxH + 4);
                g.setColor(textMuted);
            }
            g.drawString("Faza napięcia odniesienia φ", x0 + w / 2 - 70, boxY + boxH + 34);
        }
    }

    private static void drawHnData(Graphics2D g, int x0, int boxY, int w, int boxH, pipeline.StatisticalAnalyzer.ProfileData profiles) {
        int bins = profiles.binsCount;
        int maxHn = Math.max(1, profiles.maxHn);
        double binW = (double) w / bins;

        for (int i = 0; i < bins; i++) {
            int val = profiles.Hn[i];
            if (val <= 0) continue;
            int barH = (int) Math.round((double) val / maxHn * boxH);
            int bx = x0 + (int) Math.round(i * binW);
            int bw = Math.max(1, (int) Math.ceil(binW));
            int by = boxY + boxH - barH;

            Color col = (i < bins / 2)
                    ? new Color(0, 175, 235, 210)
                    : new Color(175, 95, 245, 210);

            g.setColor(col);
            g.fillRect(bx, by, bw, barH);
        }
    }

    private static void drawHqnData(Graphics2D g, int x0, int boxY, int w, int boxH, pipeline.StatisticalAnalyzer.ProfileData profiles) {
        int bins = profiles.binsCount;
        double maxHqn = Math.max(1e-3, profiles.maxHqn);
        double binW = (double) w / bins;

        int[] xPoints = new int[bins + 2];
        int[] yPoints = new int[bins + 2];

        xPoints[0] = x0;
        yPoints[0] = boxY + boxH;

        for (int i = 0; i < bins; i++) {
            double val = profiles.Hqn[i];
            int py = boxY + boxH - (int) Math.round(val / maxHqn * boxH);
            int px = x0 + (int) Math.round((i + 0.5) * binW);
            xPoints[i + 1] = px;
            yPoints[i + 1] = py;
        }

        xPoints[bins + 1] = x0 + w;
        yPoints[bins + 1] = boxY + boxH;

        g.setColor(new Color(35, 205, 120, 50));
        g.fillPolygon(xPoints, yPoints, bins + 2);

        g.setColor(new Color(30, 190, 110));
        g.setStroke(new BasicStroke(1.8f));
        for (int i = 1; i < bins; i++) {
            g.drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }

    private static void drawHqmaxData(Graphics2D g, int x0, int boxY, int w, int boxH, pipeline.StatisticalAnalyzer.ProfileData profiles) {
        int bins = profiles.binsCount;
        double maxHqmax = Math.max(1e-3, profiles.maxHqmax);
        double binW = (double) w / bins;

        int prevX = -1, prevY = -1;
        g.setColor(new Color(245, 75, 55));
        g.setStroke(new BasicStroke(1.8f));

        for (int i = 0; i < bins; i++) {
            double val = profiles.Hqmax[i];
            int px = x0 + (int) Math.round((i + 0.5) * binW);
            int py = boxY + boxH - (int) Math.round(val / maxHqmax * boxH);

            if (val > 0) {
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, px, py);
                }
                g.fillOval(px - 2, py - 2, 5, 5);
                prevX = px;
                prevY = py;
            } else {
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, px, boxY + boxH);
                }
                prevX = px;
                prevY = boxY + boxH;
            }
        }
    }

    /**
     * Renders synchronized two-panel chart: Time-Domain Scope (left) & Frequency Spectrum FFT (right).
     */
    public static BufferedImage renderPulseAndSpectrum(PulseWaveform p, int w, int h, boolean darkTheme) {
        if (p == null || p.timeRelativeNs == null || p.timeRelativeNs.length == 0) {
            return createPlaceholderImage(w, h, "Brak zarejestrowanego impulsu WNZ do analizy widmowej");
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color bg = darkTheme ? new Color(20, 22, 26) : new Color(250, 252, 255);
        Color boxBg = darkTheme ? new Color(16, 18, 22) : Color.WHITE;
        Color borderCol = darkTheme ? new Color(50, 55, 65) : new Color(210, 215, 225);
        Color textCol = darkTheme ? new Color(220, 225, 235) : new Color(30, 40, 55);
        Color textMuted = darkTheme ? new Color(140, 145, 155) : new Color(110, 120, 135);

        g2.setColor(bg);
        g2.fillRect(0, 0, w, h);

        int padL = 55;
        int padR = 25;
        int padT = 32;
        int padB = 40;
        int gap = 30;

        int totalPlotW = w - padL - padR - gap;
        int halfW = totalPlotW / 2;
        int plotH = h - padT - padB;

        int leftX = padL;
        int rightX = padL + halfW + gap;

        ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
        String u = cal.getUnitLabel();

        // LEFT PANEL: TIME DOMAIN
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(new Color(0, 180, 240));
        g2.drawString("PRZEBIEG CZASOWY IMPULSU WNZ (t_r=" + String.format(Locale.US, "%.1f", p.riseTimeNs)
                + " ns, t_w=" + String.format(Locale.US, "%.1f", p.pulseWidthNs) + " ns)", leftX, padT - 10);

        g2.setColor(boxBg);
        g2.fillRect(leftX, padT, halfW, plotH);
        g2.setColor(borderCol);
        g2.drawRect(leftX, padT, halfW, plotH);

        double tMin = p.timeRelativeNs[0];
        double tMax = p.timeRelativeNs[p.timeRelativeNs.length - 1];
        double yAmpMax = Math.max(1.0, p.peakMagnitude * 1.3);

        // Time grid & zero line
        g2.setColor(darkTheme ? new Color(40, 44, 52) : new Color(230, 235, 242));
        int midY = padT + plotH / 2;
        g2.drawLine(leftX, midY, leftX + halfW, midY);

        // Plot filtered pulse
        g2.setColor(new Color(0, 210, 255));
        g2.setStroke(new BasicStroke(1.8f));
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < p.timeRelativeNs.length; i++) {
            double tx = p.timeRelativeNs[i];
            double vy = p.filtered != null ? p.filtered[i] : 0.0;
            double px = leftX + ((tx - tMin) / (tMax - tMin)) * halfW;
            double py = midY - (vy / yAmpMax) * (plotH / 2.0);
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        g2.draw(path);

        // Peak marker
        if (p.peakIndex >= 0 && p.peakIndex < p.timeRelativeNs.length) {
            double pkT = p.timeRelativeNs[p.peakIndex];
            double pkY = p.filtered != null ? p.filtered[p.peakIndex] : p.peakMagnitude;
            int dotX = (int) Math.round(leftX + ((pkT - tMin) / (tMax - tMin)) * halfW);
            int dotY = (int) Math.round(midY - (pkY / yAmpMax) * (plotH / 2.0));

            g2.setColor(Color.RED);
            g2.fillOval(dotX - 4, dotY - 4, 8, 8);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String pkText = cal.isCalibrated()
                    ? String.format(Locale.US, "Qp=%.2f %s", cal.toDisplay(p.peakMagnitude), u)
                    : String.format(Locale.US, "Qp=%.1f mV", p.peakMagnitude);
            g2.drawString(pkText, dotX + 6, dotY - 4);
        }

        // Time domain axis labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(textMuted);
        g2.drawString("+" + String.format(Locale.US, "%.0f mV", yAmpMax), leftX - 48, padT + 12);
        g2.drawString("0", leftX - 16, midY + 4);
        g2.drawString("-" + String.format(Locale.US, "%.0f mV", yAmpMax), leftX - 48, padT + plotH);
        g2.drawString(String.format(Locale.US, "%.0f ns", tMin), leftX, padT + plotH + 15);
        g2.drawString(String.format(Locale.US, "%.0f ns", tMax), leftX + halfW - 40, padT + plotH + 15);
        g2.drawString("Czas względny [ns]", leftX + halfW / 2 - 45, padT + plotH + 28);

        // RIGHT PANEL: FFT SPECTRUM
        FastFourierTransform.SpectrumResult spec = p.getSpectrum(FastFourierTransform.WindowType.HANNING, true);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(new Color(245, 160, 40));
        g2.drawString("WIDMO CZĘSTOTLIWOŚCIOWE FFT (f_peak=" + String.format(Locale.US, "%.2f", spec.peakFrequency / 1e6)
                + " MHz, f_cent=" + String.format(Locale.US, "%.2f", spec.spectralCentroid / 1e6) + " MHz)", rightX, padT - 10);

        g2.setColor(boxBg);
        g2.fillRect(rightX, padT, halfW, plotH);
        g2.setColor(borderCol);
        g2.drawRect(rightX, padT, halfW, plotH);

        double maxFreq = spec.frequencies[spec.frequencies.length - 1];
        double maxMag = 1e-6;
        for (double m : spec.magnitudes) if (m > maxMag) maxMag = m;

        // Draw spectrum polygon
        int[] spX = new int[spec.frequencies.length + 2];
        int[] spY = new int[spec.frequencies.length + 2];
        spX[0] = rightX;
        spY[0] = padT + plotH;

        for (int i = 0; i < spec.frequencies.length; i++) {
            double f = spec.frequencies[i];
            double m = spec.magnitudes[i];
            int px = rightX + (int) Math.round((f / maxFreq) * halfW);
            int py = padT + plotH - (int) Math.round((m / maxMag) * plotH);
            spX[i + 1] = px;
            spY[i + 1] = py;
        }
        spX[spec.frequencies.length + 1] = rightX + halfW;
        spY[spec.frequencies.length + 1] = padT + plotH;

        g2.setColor(new Color(245, 160, 40, 45));
        g2.fillPolygon(spX, spY, spec.frequencies.length + 2);

        g2.setColor(new Color(245, 160, 40));
        g2.setStroke(new BasicStroke(1.6f));
        for (int i = 1; i < spec.frequencies.length; i++) {
            g2.drawLine(spX[i], spY[i], spX[i + 1], spY[i + 1]);
        }

        // Dominant peak marker
        int pkFreqX = rightX + (int) Math.round((spec.peakFrequency / maxFreq) * halfW);
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 3}, 0));
        g2.drawLine(pkFreqX, padT, pkFreqX, padT + plotH);

        // Frequency axis labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(textMuted);
        g2.drawString("0 MHz", rightX, padT + plotH + 15);
        g2.drawString(String.format(Locale.US, "%.1f MHz", maxFreq / 2e6), rightX + halfW / 2 - 20, padT + plotH + 15);
        g2.drawString(String.format(Locale.US, "%.1f MHz", maxFreq / 1e6), rightX + halfW - 45, padT + plotH + 15);
        g2.drawString("Częstotliwość f [MHz]", rightX + halfW / 2 - 50, padT + plotH + 28);

        g2.dispose();
        return img;
    }

    private static BufferedImage createPlaceholderImage(int w, int h, String msg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 26, 30));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(150, 160, 175));
        g.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        int strW = g.getFontMetrics().stringWidth(msg);
        g.drawString(msg, (w - strW) / 2, h / 2);
        g.dispose();
        return img;
    }
}
