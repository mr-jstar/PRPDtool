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
            return createPlaceholderImage(targetW, targetH > 0 ? targetH : 560, "Brak danych PRPD do wygenerowania wykresu");
        }

        BufferedImage base = hist.getImage();
        if (base == null) {
            return createPlaceholderImage(targetW, targetH > 0 ? targetH : 560, "Brak obrazu PRPD");
        }

        int bw = base.getWidth();
        int bh = base.getHeight();
        if (bw <= 0 || bh <= 0) {
            return createPlaceholderImage(targetW, targetH > 0 ? targetH : 560, "Błąd wymiarów obrazu PRPD");
        }

        // Calculate height preserving the natural aspect ratio of the user's PRPD display in the app
        int outH = targetH;
        if (outH <= 0 || outH < 450) {
            double aspect = (double) bh / bw;
            outH = (int) Math.round(targetW * aspect);
            outH = Math.max(540, Math.min(800, outH));
        }

        BufferedImage out = new BufferedImage(targetW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw base filling 100% of the target area - NO black letterbox bars!
        g2.drawImage(base, 0, 0, targetW, outH, null);

        // Draw outer subtle border
        g2.setColor(new Color(60, 65, 75));
        g2.drawRect(0, 0, targetW - 1, outH - 1);

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
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Color bg = darkTheme ? new Color(18, 20, 24) : new Color(250, 252, 255);
        Color boxBg = darkTheme ? new Color(14, 16, 20) : Color.WHITE;
        Color borderCol = darkTheme ? new Color(45, 50, 58) : new Color(210, 215, 225);
        Color textCol = darkTheme ? new Color(230, 235, 245) : new Color(30, 40, 55);
        Color textMuted = darkTheme ? new Color(140, 150, 165) : new Color(100, 110, 120);

        g.setColor(bg);
        g.fillRect(0, 0, w, h);

        int leftMargin = 68;
        int rightMargin = 28;
        int topMargin = 26;
        int bottomMargin = 52;
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

        // 1. Hn(φ)
        drawTitleHeader(g, leftMargin, slotY1, "Hn(φ) — Rozkład liczby impulsów WNZ [Counts]",
                new Color(0, 210, 255), textCol);
        drawChartBox(g, leftMargin, slotY1 + headerH, plotW, boxH, result.profiles.maxHn, false,
                boxBg, borderCol, textMuted, darkTheme);
        drawHnData(g, leftMargin, slotY1 + headerH, plotW, boxH, result.profiles);

        // 2. Hqn(φ)
        drawTitleHeader(g, leftMargin, slotY2, "Hqn(φ) — Rozkład średniego ładunku pozornego [" + u + "]",
                new Color(45, 230, 140), textCol);
        drawChartBox(g, leftMargin, slotY2 + headerH, plotW, boxH, cal.toDisplay(result.profiles.maxHqn), false,
                boxBg, borderCol, textMuted, darkTheme);
        drawHqnData(g, leftMargin, slotY2 + headerH, plotW, boxH, result.profiles);

        // 3. Hqmax(φ)
        drawTitleHeader(g, leftMargin, slotY3, "Hqmax(φ) — Obwiednia ładunku maksymalnego [" + u + "]",
                new Color(255, 100, 75), textCol);
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
        // Positive & Negative half tints matching StatisticalProfilesDialog
        g.setColor(darkTheme ? new Color(0, 150, 255, 16) : new Color(0, 120, 255, 12));
        g.fillRect(x0, boxY, w / 2, boxH);
        g.setColor(darkTheme ? new Color(180, 80, 255, 16) : new Color(160, 60, 255, 12));
        g.fillRect(midX, boxY, w / 2, boxH);

        // 50 Hz Sine reference wave
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

        // Grid (vertical lines every 45°)
        g.setColor(darkTheme ? new Color(40, 44, 52) : new Color(230, 235, 242));
        g.setStroke(new BasicStroke(1.0f));
        for (int deg = 45; deg < 360; deg += 45) {
            int gx = x0 + (int) Math.round((double) deg / 360.0 * w);
            g.drawLine(gx, boxY, gx, boxY + boxH);
        }

        // Zero-crossing divider at 180°
        g.setColor(darkTheme ? new Color(120, 130, 145, 160) : new Color(150, 160, 175, 180));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 3}, 0));
        g.drawLine(midX, boxY, midX, boxY + boxH);

        // Outer border
        g.setColor(borderCol);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRect(x0, boxY, w, boxH);

        // Y ticks (right-aligned to avoid overlapping with plot box)
        g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g.setColor(textMuted);
        FontMetrics fmY = g.getFontMetrics();
        String topLabel = (maxY >= 10.0) ? String.format(Locale.US, "%.0f", maxY) : String.format(Locale.US, "%.2f", maxY);
        int topW = fmY.stringWidth(topLabel);
        g.drawString(topLabel, x0 - topW - 6, boxY + 11);
        int zeroW = fmY.stringWidth("0");
        g.drawString("0", x0 - zeroW - 6, boxY + boxH);

        // X phase labels & Half-cycle indicators (only on bottom chart)
        if (isBottomChart) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(textMuted);
            for (int deg = 0; deg <= 360; deg += 45) {
                int tx = x0 + (int) Math.round((double) deg / 360.0 * w);
                String lbl = deg + "°";
                int tw = g.getFontMetrics().stringWidth(lbl);
                int drawTx = (deg == 360) ? Math.min(tx - tw / 2, x0 + w - tw) : (deg == 0 ? tx : tx - tw / 2);
                g.drawString(lbl, drawTx, boxY + boxH + 16);

                g.setColor(borderCol);
                g.drawLine(tx, boxY + boxH, tx, boxY + boxH + 4);
                g.setColor(textMuted);
            }

            // Sub-labels: Positive and Negative half-cycles
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.setColor(darkTheme ? new Color(0, 195, 245) : new Color(2, 132, 199));
            String posTxt = "PÓŁOKRES DODATNI (+)";
            int posW = g.getFontMetrics().stringWidth(posTxt);
            g.drawString(posTxt, x0 + w / 4 - posW / 2, boxY + boxH + 32);

            g.setColor(darkTheme ? new Color(195, 120, 255) : new Color(147, 51, 234));
            String negTxt = "PÓŁOKRES UJEMNY (-)";
            int negW = g.getFontMetrics().stringWidth(negTxt);
            g.drawString(negTxt, midX + w / 4 - negW / 2, boxY + boxH + 32);

            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.setColor(textMuted);
            String fazaTxt = "Faza napięcia odniesienia 50 Hz (φ)";
            int fazaW = g.getFontMetrics().stringWidth(fazaTxt);
            g.drawString(fazaTxt, x0 + w / 2 - fazaW / 2, boxY + boxH + 46);
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

            // Dual-color bars: cyan for positive half, purple for negative half
            Color col = (i < bins / 2)
                    ? new Color(0, 195, 245, 220)
                    : new Color(185, 110, 255, 220);

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

        // Semi-transparent emerald fill
        g.setColor(new Color(40, 220, 130, 45));
        g.fillPolygon(xPoints, yPoints, bins + 2);

        // Emerald outline line
        g.setColor(new Color(45, 230, 140));
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
        g.setColor(new Color(255, 100, 75));
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

    public static BufferedImage renderPulseAndSpectrum(PulseWaveform p, int w, int h, boolean darkTheme) {
        if (p == null || p.timeRelativeNs == null || p.timeRelativeNs.length == 0) {
            return createPlaceholderImage(w, h, "Brak zarejestrowanego impulsu WNZ do analizy widmowej");
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Color bg = darkTheme ? new Color(18, 20, 24) : new Color(250, 252, 255);
        Color boxBg = darkTheme ? new Color(14, 16, 20) : Color.WHITE;
        Color borderCol = darkTheme ? new Color(45, 50, 58) : new Color(210, 215, 225);
        Color gridCol = darkTheme ? new Color(38, 42, 50) : new Color(230, 235, 242);
        Color textMuted = darkTheme ? new Color(140, 150, 165) : new Color(100, 110, 120);
        Color unitCol = darkTheme ? new Color(180, 195, 210) : new Color(70, 80, 90);

        // Titles
        Color leftTitleCol = darkTheme ? new Color(0, 225, 255) : new Color(2, 132, 199);
        Color rightTitleCol = darkTheme ? new Color(102, 255, 153) : new Color(0, 136, 51);

        // Left Trace (Cyan)
        Color traceCol = darkTheme ? new Color(0, 225, 255) : new Color(2, 132, 199);

        // Right Spectrum (Emerald Green matching SinglePulseScopeDialog)
        Color specLineCol = darkTheme ? new Color(16, 185, 129) : new Color(5, 150, 105);
        Color specGradTop = darkTheme ? new Color(16, 185, 129, 90) : new Color(16, 185, 129, 60);
        Color specGradBot = darkTheme ? new Color(16, 185, 129, 5) : new Color(16, 185, 129, 5);

        // Markers
        Color peakMarkerCol = darkTheme ? new Color(255, 204, 0, 180) : new Color(200, 140, 0, 190);
        Color peakTextCol = darkTheme ? new Color(255, 220, 50) : new Color(160, 100, 0);

        Color centMarkerCol = darkTheme ? new Color(0, 210, 255, 160) : new Color(0, 102, 204, 180);
        Color centTextCol = darkTheme ? new Color(100, 220, 255) : new Color(0, 80, 180);

        g2.setColor(bg);
        g2.fillRect(0, 0, w, h);

        int padT = 38;
        int padB = 44;
        int plotH = h - padT - padB;

        int padL1 = 58; // Space for [mV], numbers
        int padR1 = 20;
        int padL2 = 58; // Space for [dB], numbers
        int padR2 = 24;

        int totalPads = padL1 + padR1 + padL2 + padR2;
        int plotW = (w - totalPads) / 2;

        int ox1 = padL1;
        int oy1 = padT;
        int pw1 = plotW;
        int ph1 = plotH;

        int ox2 = ox1 + pw1 + padR1 + padL2;
        int oy2 = padT;
        int pw2 = plotW;
        int ph2 = plotH;

        ChargeCalibrationManager cal = ChargeCalibrationManager.getInstance();
        String u = cal.getUnitLabel();

        // ==========================================
        // 1. LEFT PANEL: TIME DOMAIN SCOPE
        // ==========================================
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(leftTitleCol);
        g2.drawString("PRZEBIEG CZASOWY IMPULSU WNZ (TIME DOMAIN SCOPE)", ox1, oy1 - 12);

        // Box background & border
        g2.setColor(boxBg);
        g2.fillRect(ox1, oy1, pw1, ph1);
        g2.setColor(borderCol);
        g2.drawRect(ox1, oy1, pw1, ph1);

        double[] time = p.timeRelativeNs;
        double[] filt = (p.filtered != null && p.filtered.length > 0) ? p.filtered : p.raw;
        double tMin = time[0];
        double tMax = time[time.length - 1];

        double maxAbs = Math.abs(p.peakMagnitude);
        if (filt != null) {
            for (double v : filt) {
                if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                    double a = Math.abs(v);
                    if (a > maxAbs) maxAbs = a;
                }
            }
        }
        if (maxAbs < 0.5) maxAbs = 1.0;
        double yLimit = Math.ceil(maxAbs * 1.20); // 20% headroom
        double yMin1 = -yLimit;
        double yMax1 = yLimit;

        // Draw Left Grid (Y-axis - 4 divisions so center is strictly 0.0)
        int yDivs1 = 4;
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        for (int i = 0; i <= yDivs1; i++) {
            double frac = (double) i / yDivs1;
            int sy = oy1 + (int) Math.round(frac * ph1);
            double val = yMax1 - frac * (yMax1 - yMin1);

            g2.setColor(gridCol);
            g2.drawLine(ox1, sy, ox1 + pw1, sy);

            g2.setColor(textMuted);
            String label = cal.isCalibrated()
                    ? String.format(Locale.US, "%.1f", cal.toDisplay(val))
                    : String.format(Locale.US, "%.1f", val);
            int strW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, ox1 - strW - 6, sy + 4);
        }

        // Center zero line accent
        int midY1 = oy1 + ph1 / 2;
        g2.setColor(darkTheme ? new Color(55, 65, 80) : new Color(200, 210, 225));
        g2.drawLine(ox1, midY1, ox1 + pw1, midY1);

        // Draw Left Grid (X-axis - 6 divisions)
        int xDivs1 = 6;
        for (int i = 0; i <= xDivs1; i++) {
            double frac = (double) i / xDivs1;
            int sx = ox1 + (int) Math.round(frac * pw1);
            double val = tMin + frac * (tMax - tMin);

            g2.setColor(gridCol);
            g2.drawLine(sx, oy1, sx, oy1 + ph1);

            g2.setColor(textMuted);
            String label = String.format(Locale.US, "%.0f", val);
            int strW = g2.getFontMetrics().stringWidth(label);
            int drawSx = (i == xDivs1) ? Math.min(sx - strW / 2, ox1 + pw1 - strW) : (i == 0 ? sx : sx - strW / 2);
            g2.drawString(label, drawSx, oy1 + ph1 + 14);
        }

        // Left Units & Titles
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.setColor(unitCol);
        g2.drawString("[" + u + "]", ox1 - 36, oy1 - 8);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(textMuted);
        g2.drawString("Czas t [ns]", ox1 + pw1 / 2 - 25, oy1 + ph1 + 30);

        // Legend Left (inside top-right of waveform plot box)
        int legX = ox1 + pw1 - 75;
        int legY = oy1 + 18;
        g2.setColor(traceCol);
        g2.fillRect(legX, legY - 7, 8, 8);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(darkTheme ? new Color(180, 195, 210) : new Color(100, 110, 125));
        g2.drawString("Filtrowany", legX + 12, legY + 1);

        // Draw Left Waveform (Clipped)
        Shape oldClip = g2.getClip();
        g2.clipRect(ox1, oy1, pw1 + 1, ph1 + 1);

        if (filt != null && filt.length > 0) {
            g2.setColor(traceCol);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double path = new Path2D.Double();
            double xRange1 = Math.max(1e-6, tMax - tMin);
            double yRange1 = yMax1 - yMin1;
            int nPoints = Math.min(time.length, filt.length);
            for (int i = 0; i < nPoints; i++) {
                double sx = ox1 + ((time[i] - tMin) / xRange1) * pw1;
                double sy = oy1 + ((yMax1 - filt[i]) / yRange1) * ph1;
                if (i == 0) path.moveTo(sx, sy);
                else path.lineTo(sx, sy);
            }
            g2.draw(path);
        }

        // Peak Marker Dot & Label
        if (p.peakIndex >= 0 && p.peakIndex < time.length) {
            double pkT = time[p.peakIndex];
            double pkY = (filt != null && p.peakIndex < filt.length) ? filt[p.peakIndex] : p.peakMagnitude;
            int dotX = ox1 + (int) Math.round(((pkT - tMin) / Math.max(1e-6, tMax - tMin)) * pw1);
            int dotY = oy1 + (int) Math.round(((yMax1 - pkY) / (yMax1 - yMin1)) * ph1);

            g2.setColor(new Color(255, 60, 60));
            g2.fillOval(dotX - 4, dotY - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawOval(dotX - 4, dotY - 4, 8, 8);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String pkText = cal.isCalibrated()
                    ? String.format(Locale.US, "Qp=%.2f %s", cal.toDisplay(p.peakMagnitude), u)
                    : String.format(Locale.US, "Qp=%.1f mV", p.peakMagnitude);
            int txtW = g2.getFontMetrics().stringWidth(pkText);
            int textX = Math.max(ox1 + 6, Math.min(ox1 + pw1 - txtW - 6, dotX + 6));
            int textY = Math.max(oy1 + 14, Math.min(oy1 + ph1 - 6, dotY - 4));
            g2.setColor(new Color(255, 80, 80));
            g2.drawString(pkText, textX, textY);
        }

        g2.setClip(oldClip);


        // ==========================================
        // 2. RIGHT PANEL: FFT SPECTRUM (dB SCALE)
        // ==========================================
        FastFourierTransform.SpectrumResult spec = p.getSpectrum(FastFourierTransform.WindowType.HANNING, true);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(rightTitleCol);
        g2.drawString("WIDMO CZĘSTOTLIWOŚCIOWE FFT (FREQUENCY DOMAIN SPECTRUM)", ox2, oy2 - 12);

        // Box background & border
        g2.setColor(boxBg);
        g2.fillRect(ox2, oy2, pw2, ph2);
        g2.setColor(borderCol);
        g2.drawRect(ox2, oy2, pw2, ph2);

        double fMin = 0.0;
        double fMax = (spec != null && spec.frequencies != null && spec.frequencies.length > 0)
                ? (spec.frequencies[spec.frequencies.length - 1] / 1e6)
                : ((spec != null && spec.fs > 0) ? (spec.fs / 2e6) : 62.5);
        if (fMax <= 0.0) fMax = 62.5;

        double[] amps = (spec != null) ? spec.magnitudesDb : null;
        double yMin2 = -80.0; // -80 dBmV standard noise floor
        double maxDb = -80.0;
        if (amps != null) {
            for (double d : amps) {
                if (!Double.isNaN(d) && !Double.isInfinite(d) && d > maxDb) {
                    maxDb = d;
                }
            }
        }
        // Round upper bound to nearest 20 dB so divisions are clean (+20.0, 0.0, -20.0, -40.0, -60.0, -80.0)
        double yMax2 = Math.max(20.0, Math.ceil((maxDb + 10.0) / 20.0) * 20.0);
        int yDivs2 = (int) Math.round((yMax2 - yMin2) / 20.0);
        if (yDivs2 < 4) yDivs2 = 5;

        // Draw Right Grid (Y-axis)
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        for (int i = 0; i <= yDivs2; i++) {
            double frac = (double) i / yDivs2;
            int sy = oy2 + (int) Math.round(frac * ph2);
            double val = yMax2 - frac * (yMax2 - yMin2);

            g2.setColor(gridCol);
            g2.drawLine(ox2, sy, ox2 + pw2, sy);

            g2.setColor(textMuted);
            String label = String.format(Locale.US, "%.1f", val);
            int strW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, ox2 - strW - 6, sy + 4);
        }

        // Draw Right Grid (X-axis - 6 divisions)
        int xDivs2 = 6;
        for (int i = 0; i <= xDivs2; i++) {
            double frac = (double) i / xDivs2;
            int sx = ox2 + (int) Math.round(frac * pw2);
            double val = fMin + frac * (fMax - fMin);

            g2.setColor(gridCol);
            g2.drawLine(sx, oy2, sx, oy2 + ph2);

            g2.setColor(textMuted);
            String label = String.format(Locale.US, "%.1f", val);
            int strW = g2.getFontMetrics().stringWidth(label);
            int drawSx = (i == xDivs2) ? Math.min(sx - strW / 2, ox2 + pw2 - strW) : (i == 0 ? sx : sx - strW / 2);
            g2.drawString(label, drawSx, oy2 + ph2 + 14);
        }

        // Right Units & Titles
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.setColor(unitCol);
        g2.drawString("[dB]", ox2 - 36, oy2 - 8);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(textMuted);
        g2.drawString("Częstotliwość f [MHz]", ox2 + pw2 / 2 - 50, oy2 + ph2 + 30);

        // Draw Right Spectrum (Clipped)
        if (spec != null && spec.frequencies != null && spec.frequencies.length > 0 && amps != null) {
            g2.clipRect(ox2, oy2, pw2 + 1, ph2 + 1);

            double xRange2 = Math.max(1e-6, fMax - fMin);
            double yRange2 = yMax2 - yMin2;

            Path2D.Double area = new Path2D.Double();
            Path2D.Double line = new Path2D.Double();

            double baseSy = oy2 + ((yMax2 - yMin2) / yRange2) * ph2;
            area.moveTo(ox2, baseSy);

            for (int i = 0; i < spec.frequencies.length; i++) {
                double fMhz = spec.frequencies[i] / 1e6;
                double sx = ox2 + ((fMhz - fMin) / xRange2) * pw2;
                double val = Math.max(yMin2, Math.min(yMax2, amps[i]));
                double sy = oy2 + ((yMax2 - val) / yRange2) * ph2;

                if (i == 0) {
                    area.lineTo(sx, sy);
                    line.moveTo(sx, sy);
                } else {
                    area.lineTo(sx, sy);
                    line.lineTo(sx, sy);
                }
            }
            area.lineTo(ox2 + pw2, baseSy);
            area.closePath();

            // Gradient Fill
            g2.setPaint(new GradientPaint(0, oy2, specGradTop, 0, oy2 + ph2, specGradBot));
            g2.fill(area);

            // Spectrum Stroke
            g2.setColor(specLineCol);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);

            // Dominant Peak Marker (Yellow/Gold dotted)
            if (spec.peakFrequency > 0) {
                double fPkMhz = spec.peakFrequency / 1e6;
                int px = ox2 + (int) Math.round(((fPkMhz - fMin) / xRange2) * pw2);
                if (px >= ox2 && px <= ox2 + pw2) {
                    g2.setColor(peakMarkerCol);
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{3, 3}, 0.0f));
                    g2.drawLine(px, oy2, px, oy2 + ph2);

                    g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    g2.setColor(peakTextCol);
                    g2.drawString(String.format(Locale.US, "fpk=%.2f MHz", fPkMhz), px + 4, oy2 + 16);
                }
            }

            // Spectral Centroid Marker (Cyan/Blue dotted)
            if (spec.spectralCentroid > 0) {
                double fCentMhz = spec.spectralCentroid / 1e6;
                int cx = ox2 + (int) Math.round(((fCentMhz - fMin) / xRange2) * pw2);
                if (cx >= ox2 && cx <= ox2 + pw2) {
                    g2.setColor(centMarkerCol);
                    g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2, 2}, 0.0f));
                    g2.drawLine(cx, oy2 + 22, cx, oy2 + ph2);

                    g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                    g2.setColor(centTextCol);
                    g2.drawString(String.format(Locale.US, "f_cent=%.1f MHz", fCentMhz), cx + 4, oy2 + 30);
                }
            }

            g2.setClip(oldClip);
        }

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
