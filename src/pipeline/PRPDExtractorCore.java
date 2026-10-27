package pipeline;

import dsp.Filter;

/**
 *
 * @author jstar
 */
public class PRPDExtractorCore {

    private double f0;
    private double t0;
    private double threshold;
    private double deadUs;
    private Filter filter;
    private Pulses pulses;

    private double lastT = Double.NaN;

    public PRPDExtractorCore(
            double f0,
            double t0,
            double threshold,
            double deadUs,
            Filter filter
    ) {
        this.f0 = f0;
        this.t0 = t0;
        this.threshold = threshold;
        this.deadUs = deadUs;
        this.filter = filter;
    }
    
    public void setT0( double t0 ) {
        this.t0 = t0;
    }
    
    public void setThreshold( double threshold ) {
        this.threshold = threshold;
    }

    private double[] tmpT = new double[0];
    private double[] tmpPhase = new double[0];
    private double[] tmpAmp = new double[0];

    public Pulses extract(Buffer b) {
        int n = b.used;

        if (n < 3) {
            b.release();
            Pulses p = new Pulses(new double[0], new double[0], new double[0], 0);
            p.n = 0;
            p.newWindow = b.newWindow;
            return p;
        }

        if (b.newWindow || Double.isNaN(lastT) || (b.used > 0 && b.t[0] < lastT)) {
            lastT = Double.NaN;
        }

        double fs = estimateFs(b.t, n);
        filter.setFs(fs);

        // Zabezpieczenie: przy wysokim próbkowaniu (np. 125 MSps) martwe okno musi wynosić co najmniej
        // ułamek mikrosekundy (np. 0.5 us), aby zapobiec traktowaniu każdego pojedynczego taktu ADC jako osobnego impulsu
        int minDeadN = (fs > 1e6) ? Math.max(1, (int) Math.round(0.5e-6 * fs)) : 1;
        int deadN = Math.max(minDeadN, (int) Math.round(deadUs * 1e-6 * fs));

        if (tmpT.length < n) {
            tmpT = new double[n];
            tmpPhase = new double[n];
            tmpAmp = new double[n];
        }

        double [] filtered = filter.filter(b.u, b.used);

        int count = 0;
        // Odcięcie krawędzi (micro-blanking) omijające stan nieustalony (szpilkę) filtra po każdej przerwie
        int skipSamples = Math.max(1, (int) Math.round(20e-6 * fs));
        int i = skipSamples;
        int maxI = n - skipSamples;

        while (i < maxI) {
            double ti = b.t[i];

            if (!Double.isNaN(lastT) && ti <= lastT) {
                i++;
                continue;
            }

            double y = filtered[i];
            double absY = Math.abs(y);

            if (absY >= threshold) {
                int j0 = i;
                int j1 = Math.min(maxI, i + deadN);

                int best = j0;
                double bestAbs = absY;

                for (int j = j0 + 1; j < j1; j++) {
                    double a = Math.abs(filtered[j]);

                    if (a > bestAbs) {
                        bestAbs = a;
                        best = j;
                    }
                }

                double tp = b.t[best];
                double amp = filtered[best];

                tmpT[count] = tp;
                tmpPhase[count] = phase(tp);
                tmpAmp[count] = amp;
                count++;

                lastT = tp;
                i = Math.max(j1, best + 1);
            } else {
                lastT = ti;
                i++;
            }
        }
        
        double[] finalT = new double[count];
        double[] finalPhase = new double[count];
        double[] finalAmp = new double[count];
        System.arraycopy(tmpT, 0, finalT, 0, count);
        System.arraycopy(tmpPhase, 0, finalPhase, 0, count);
        System.arraycopy(tmpAmp, 0, finalAmp, 0, count);
        
        Pulses p = new Pulses(finalT, finalPhase, finalAmp, count);
        p.n = count;
        p.fs = fs;
        p.newWindow = b.newWindow;
        
        b.release();

        return p;
    }

    private double phase(double t) {
        double p = 360.0 * f0 * (t - t0);
        p %= 360.0;

        if (p < 0.0) {
            p += 360.0;
        }

        return p;
    }

    private static double estimateFs(double[] t, int n) {
        double sum = 0.0;
        int count = 0;

        for (int i = 1; i < n; i++) {
            double dt = t[i] - t[i - 1];

            if (dt > 0.0) {
                sum += dt;
                count++;
            }
        }

        return 1.0 / (sum / count);
    }
}
