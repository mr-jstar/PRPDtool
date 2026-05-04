package prpdtool;

/**
 *
 * @author jstar
 */
public class NiceScale {

    public static class Scale {
        public final double min;
        public final double max;
        public final double step;
        public final int ticks;

        public Scale(double min, double max, double step) {
            this.min = min;
            this.max = max;
            this.step = step;
            this.ticks = (int)Math.round((max - min) / step) + 1;
        }

        @Override
        public String toString() {
            return "Scale{min=" + min + ", max=" + max +
                   ", step=" + step + ", ticks=" + ticks + "}";
        }
    }

    public static Scale niceScale(double dataMin, double dataMax) {
        if (Double.isNaN(dataMin) || Double.isNaN(dataMax)
                || Double.isInfinite(dataMin) || Double.isInfinite(dataMax)) {
            throw new IllegalArgumentException("Invalid range");
        }

        if (dataMin > dataMax) {
            double tmp = dataMin;
            dataMin = dataMax;
            dataMax = tmp;
        }

        if (dataMin == dataMax) {
            double d = Math.abs(dataMin);
            double pad = d == 0.0 ? 1.0 : niceNumber(d * 0.1, true);
            dataMin -= pad;
            dataMax += pad;
        }

        double bestMin = 0;
        double bestMax = 0;
        double bestStep = 0;
        //int bestIntervals = 0;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int intervals = 4; intervals <= 6; intervals++) {
            double rawStep = (dataMax - dataMin) / intervals;
            double step = niceNumber(rawStep, true);

            double niceMin = Math.floor(dataMin / step) * step;
            double niceMax = Math.ceil(dataMax / step) * step;

            int realIntervals = (int)Math.round((niceMax - niceMin) / step);

            if (realIntervals < 4 || realIntervals > 6) {
                continue;
            }

            double addedRange = (niceMax - niceMin) - (dataMax - dataMin);
            double score = addedRange / step + Math.abs(realIntervals - 5) * 0.2;

            if (score < bestScore) {
                bestScore = score;
                bestMin = niceMin;
                bestMax = niceMax;
                bestStep = step;
                //bestIntervals = realIntervals;
            }
        }

        // awaryjnie, gdyby filtr 4–6 nie znalazł wyniku
        if (bestStep == 0) {
            double rawStep = (dataMax - dataMin) / 5.0;
            bestStep = niceNumber(rawStep, true);
            bestMin = Math.floor(dataMin / bestStep) * bestStep;
            bestMax = Math.ceil(dataMax / bestStep) * bestStep;
        }

        bestMin = clean(bestMin);
        bestMax = clean(bestMax);
        bestStep = clean(bestStep);

        return new Scale(bestMin, bestMax, bestStep);
    }

    private static double niceNumber(double x, boolean round) {
        double exponent = Math.floor(Math.log10(x));
        double fraction = x / Math.pow(10, exponent);

        double niceFraction;

        if (round) {
            if (fraction < 1.5) {
                niceFraction = 1;
            } else if (fraction < 3) {
                niceFraction = 2;
            } else if (fraction < 7) {
                niceFraction = 5;
            } else {
                niceFraction = 10;
            }
        } else {
            if (fraction <= 1) {
                niceFraction = 1;
            } else if (fraction <= 2) {
                niceFraction = 2;
            } else if (fraction <= 5) {
                niceFraction = 5;
            } else {
                niceFraction = 10;
            }
        }

        return niceFraction * Math.pow(10, exponent);
    }

    private static double clean(double x) {
        if (Math.abs(x) < 1e-12) {
            return 0.0;
        }
        return Math.round(x * 1e12) / 1e12;
    }
}
