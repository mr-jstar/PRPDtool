package prpdtool;

/**
 *
 * @author jstar
 */
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NicerScale {

    public record Scale(double min, double max, double step, int intervals) {

    }

    public static Scale niceScale(double dataMin, double dataMax) {
        if (!Double.isFinite(dataMin) || !Double.isFinite(dataMax)) {
            throw new IllegalArgumentException("Invalid range");
        }

        if (dataMin > dataMax) {
            double t = dataMin;
            dataMin = dataMax;
            dataMax = t;
        }

        if (dataMin == dataMax) {
            double pad = dataMin == 0.0 ? 1.0 : Math.abs(dataMin) * 0.1;
            dataMin -= pad;
            dataMax += pad;
        }

        double bestMin = 0, bestMax = 0, bestStep = 0;
        int bestIntervals = 0;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int intervals = 4; intervals <= 6; intervals++) {
            double rawStep = (dataMax - dataMin) / intervals;
            double step = niceNumber(rawStep);

            double niceMin = Math.floor(dataMin / step) * step;
            double niceMax = Math.ceil(dataMax / step) * step;

            int realIntervals = (int) Math.round((niceMax - niceMin) / step);

            if (realIntervals < 4 || realIntervals > 6) {
                continue;
            }

            double added = (niceMax - niceMin) - (dataMax - dataMin);
            double score = added / step + Math.abs(realIntervals - 5) * 0.2;

            if (score < bestScore) {
                bestScore = score;
                bestMin = niceMin;
                bestMax = niceMax;
                bestStep = step;
                bestIntervals = realIntervals;
            }
        }

        if (bestStep == 0) {
            bestStep = niceNumber((dataMax - dataMin) / 5.0);
            bestMin = Math.floor(dataMin / bestStep) * bestStep;
            bestMax = Math.ceil(dataMax / bestStep) * bestStep;
            bestIntervals = (int) Math.round((bestMax - bestMin) / bestStep);
        }

        return new Scale(bestMin, bestMax, bestStep, bestIntervals);
    }

    private static double niceNumber(double x) {
        double exponent = Math.floor(Math.log10(Math.abs(x)));
        double fraction = Math.abs(x) / Math.pow(10, exponent);

        double niceFraction;

        if (fraction < 1.5) {
            niceFraction = 1;
        } else if (fraction < 3.0) {
            niceFraction = 2;
        } else if (fraction < 7.0) {
            niceFraction = 5;
        } else {
            niceFraction = 10;
        }

        return niceFraction * Math.pow(10, exponent);
    }

    public static String formatTickOld(double value, double step) {
        if (value == 0.0) {
            return "0";
        }

        double absValue = Math.abs(value);
        double absStep = Math.abs(step);

        boolean scientific
                = absValue < 0.001
                || absValue >= 10000
                || absStep < 0.001
                || absStep >= 10000;

        DecimalFormatSymbols sym
                = DecimalFormatSymbols.getInstance(Locale.US);

        if (scientific) {
            DecimalFormat df = new DecimalFormat("0.###E0", sym);
            return df.format(value);
        } else {
            int decimals = Math.max(0, (int) Math.ceil(-Math.log10(absStep)) + 1);
            decimals = Math.min(decimals, 10);

            StringBuilder pattern = new StringBuilder("0");
            if (decimals > 0) {
                pattern.append(".");
                pattern.append("#".repeat(decimals));
            }

            DecimalFormat df = new DecimalFormat(pattern.toString(), sym);
            return df.format(value);
        }
    }

    public static String formatTick(double value, double step) {
        double absStep = Math.abs(step);

        // adaptacyjne zerowanie
        if (Math.abs(value) < absStep * 1e-6) {
            return "0";
        }

        double absValue = Math.abs(value);

        boolean scientific
                = absValue < 0.001
                || absValue >= 10000
                || absStep < 0.001
                || absStep >= 10000;

        java.text.DecimalFormatSymbols sym
                = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);

        if (scientific) {
            java.text.DecimalFormat df = new java.text.DecimalFormat("0.###E0", sym);
            return df.format(value);
        } else {
            int decimals = Math.max(0, (int) Math.ceil(-Math.log10(absStep)) + 1);
            decimals = Math.min(decimals, 10);

            StringBuilder pattern = new StringBuilder("0");
            if (decimals > 0) {
                pattern.append(".");
                pattern.append("#".repeat(decimals));
            }

            java.text.DecimalFormat df = new java.text.DecimalFormat(pattern.toString(), sym);
            return df.format(value);
        }
    }
}
