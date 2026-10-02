import dsp.*;

public class TestFilter {
    public static void main(String[] args) {
        double fs = 15.625e6;
        double fc = 100e3;
        
        double[] signal = new double[1048576];
        // Sinus 100 Hz, amplituda 1300
        for (int i=0; i<signal.length; i++) {
            signal[i] = 1300 * Math.sin(2 * Math.PI * 100.0 * i / fs + 1.234);
        }
        
        Filter hf = new HighPassFilter(fs, fc, 0.707, 4);
        double[] filtered = hf.filter(signal, signal.length);
        
        System.out.println("First few:");
        for(int i=0; i<10; i++) System.out.println(i + ": " + filtered[i]);
        
        System.out.println("Last few:");
        for(int i=filtered.length-10; i<filtered.length; i++) System.out.println(i + ": " + filtered[i]);
    }
}
