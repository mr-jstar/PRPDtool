package redpitaya;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Consumer;

public final class RedPitayaAcquisition {

    private RedPitayaAcquisition() {
    }

    public static RedPitayaCapture captureChannel(
            RedPitayaConfig inputConfig,
            int channel,
            int maxPoints,
            Consumer<String> status
    ) throws IOException {
        RedPitayaConfig config = inputConfig.copy();
        config.channels = new int[]{channel};
        config.visualChannel = channel;
        long total = config.totalSamples();
        int stride = Math.max(1, (int) Math.ceil(total / (double) maxPoints));
        int capacity = (int) Math.min(maxPoints, (total + stride - 1) / stride);
        double[] t = new double[capacity];
        double[] raw = new double[capacity];
        int kept = 0;
        long seen = 0;
        double sampleRate = config.sampleRate();

        if (status != null) {
            status.accept("Laczenie z " + config.host + ":" + config.port);
        }
        try (RedPitayaSession session = new RedPitayaSession(config)) {
            RedPitayaFrame frame;
            while ((frame = session.readFrame()) != null) {
                if (status != null) {
                    status.accept("Odebrano ramke " + (frame.seq + 1));
                }
                sampleRate = frame.sampleRate;
                ByteBuffer bb = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < frame.sampleCount; i++) {
                    short value = bb.getShort();
                    if (seen % stride == 0 && kept < raw.length) {
                        t[kept] = seen / sampleRate;
                        raw[kept] = value;
                        kept++;
                    }
                    seen++;
                }
            }
        }
        return new RedPitayaCapture(
                Arrays.copyOf(t, kept),
                Arrays.copyOf(raw, kept),
                sampleRate,
                config.gainForChannel(channel)
        );
    }
}
