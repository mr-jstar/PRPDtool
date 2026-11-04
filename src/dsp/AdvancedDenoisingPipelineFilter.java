package dsp;

/**
 * Composite DSP Filter implementing the complete High-Voltage PD Denoising Pipeline:
 * Base High-Pass -> Multi-Notch NBI Rejection Bank -> Noise Gate.
 * Implements dsp.Filter for drop-in integration with PRPDExtractorCore.
 */
public class AdvancedDenoisingPipelineFilter implements Filter {

    private final HighPassFilter baseHpf;
    private final DenoisingConfigurationManager configMgr = DenoisingConfigurationManager.getInstance();

    public AdvancedDenoisingPipelineFilter(double fs, double cutF, double q, int order) {
        this.baseHpf = new HighPassFilter(fs, cutF, q, order);
        configMgr.getNotchFilter().setFs(fs);
    }

    @Override
    public double[] filter(double[] signal) {
        return filter(signal, signal.length);
    }

    @Override
    public double[] filter(double[] signal, int n) {
        // Step 1: Base High-Pass filtering (removes power frequency and low harmonics)
        double[] out = baseHpf.filter(signal, n);

        // Step 2: Advanced Denoising (if globally enabled)
        if (configMgr.isGlobalEnabled()) {
            // Apply Multi-Notch Filter Bank
            out = configMgr.getNotchFilter().filter(out, n);

            // Apply Noise Gating
            out = configMgr.getNoiseGate().filter(out, n);
        }

        return out;
    }

    @Override
    public void setFs(double fs) {
        baseHpf.setFs(fs);
        configMgr.getNotchFilter().setFs(fs);
    }

    public HighPassFilter getBaseHpf() {
        return baseHpf;
    }
}
