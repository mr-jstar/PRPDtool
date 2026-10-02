import dsp.*;

public class TestFilter2 {
    public static void main(String[] args) {
        double fs = 15.625e6;
        double fc = 100e3;
        
        double[] signal = new double[1048576];
        for (int i=0; i<signal.length; i++) {
            signal[i] = 1300 * Math.sin(2 * Math.PI * 100.0 * i / fs + 1.234);
        }
        
        Filter hf = new HighPassFilter(fs, fc, 0.707, 4);
        double[] filtered = hf.filter(signal, signal.length);
        
        double maxAbs = 0;
        int maxIdx = -1;
        for(int i=0; i<filtered.length; i++) {
            if (Math.abs(filtered[i]) > maxAbs) {
                maxAbs = Math.abs(filtered[i]);
                maxIdx = i;
            }
        }
        System.out.println("Max Abs: " + maxAbs + " at idx: " + maxIdx);
    }
}
