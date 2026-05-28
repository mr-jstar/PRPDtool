package redpitaya;

public class RedPitayaCapture {

    public final double[] t;
    public final double[] raw;
    public final double sampleRate;
    public final String gain;

    RedPitayaCapture(double[] t, double[] raw, double sampleRate, String gain) {
        this.t = t;
        this.raw = raw;
        this.sampleRate = sampleRate;
        this.gain = gain;
    }

    public double[] volts() {
        double fullScale = "HV".equals(gain) ? 20.0 : 1.0;
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = raw[i] / 8192.0 * fullScale;
        }
        return out;
    }
}
