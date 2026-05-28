package redpitaya;

public class RedPitayaFrame {

    public final int seq;
    public final int channelCount;
    public final double sampleRate;
    public final int sampleCount;
    public final byte[] payload;

    RedPitayaFrame(int seq, int channelCount, double sampleRate, int sampleCount, byte[] payload) {
        this.seq = seq;
        this.channelCount = channelCount;
        this.sampleRate = sampleRate;
        this.sampleCount = sampleCount;
        this.payload = payload;
    }
}
