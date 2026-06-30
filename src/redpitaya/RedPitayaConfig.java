package redpitaya;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class RedPitayaConfig {

    public static final double ADC_BASE_RATE = 125_000_000.0;
    public static final String BOARD_MODEL = "STEMlab 125-14 Pro Z7020 Gen 2";
    private static final double TRIGGER_CALIBRATION_DURATION_S = 0.02;
    private static final long TRIGGER_CALIBRATION_MAX_SAMPLES = 5_000_000L;

    public String host = "rp-f0f771.local";
    public int port = 9999;
    public int[] channels = {1};
    public int visualChannel = 1;
    public String gainCh1 = "LV";
    public String gainCh2 = "LV";
    public int decimation = 64;
    public boolean averaging;
    public String triggerSource = "NOW";
    public double triggerLevel = 0.0;
    public int triggerDelay = 0;
    public double triggerTimeoutS = 10.0;
    public boolean durationMode = true;
    public double durationS = 0.01;
    public int frameSize = 1048576;
    public int frameCount = 1;

    public RedPitayaConfig copy() {
        RedPitayaConfig c = new RedPitayaConfig();
        c.host = host;
        c.port = port;
        c.channels = channels.clone();
        c.visualChannel = visualChannel;
        c.gainCh1 = gainCh1;
        c.gainCh2 = gainCh2;
        c.decimation = decimation;
        c.averaging = averaging;
        c.triggerSource = triggerSource;
        c.triggerLevel = triggerLevel;
        c.triggerDelay = triggerDelay;
        c.triggerTimeoutS = triggerTimeoutS;
        c.durationMode = durationMode;
        c.durationS = durationS;
        c.frameSize = frameSize;
        c.frameCount = frameCount;
        return c;
    }

    public RedPitayaConfig forTriggerCalibration() {
        return forTriggerCalibration(1);
    }

    public RedPitayaConfig forTriggerCalibration(int channel) {
        RedPitayaConfig c = copy();
        int calibrationChannel = channel == 2 ? 2 : 1;
        c.channels = new int[]{calibrationChannel};
        c.visualChannel = calibrationChannel;
        c.triggerSource = "NOW";
        c.triggerLevel = 0.0;
        c.triggerDelay = 0;
        c.durationMode = true;
        long requestedSamples = totalSamples();
        long calibrationSamples = Math.round(c.sampleRate() * TRIGGER_CALIBRATION_DURATION_S);
        long cappedSamples = Math.min(requestedSamples, Math.min(calibrationSamples, TRIGGER_CALIBRATION_MAX_SAMPLES));
        cappedSamples = Math.max(2L, cappedSamples);
        if ((cappedSamples & 1L) != 0L) {
            cappedSamples++;
        }
        c.durationS = cappedSamples / c.sampleRate();
        c.frameCount = c.normalizedFrameCount();
        return c;
    }

    public double sampleRate() {
        return ADC_BASE_RATE / decimation;
    }

    public int normalizedFrameSize() {
        return 1048576;
    }

    public long totalSamples() {
        double fs = sampleRate();
        int normalizedFrameSize = normalizedFrameSize();
        long total;
        if (durationMode) {
            if (durationS <= 0.0) {
                return 0L;
            }
            total = Math.max(1L, Math.round(durationS * fs));
        } else {
            total = (long) normalizedFrameSize * Math.max(1, frameCount);
        }
        
        long remainder = total % 4096;
        if (remainder != 0) {
            total += 4096 - remainder;
        }
        
        return total;
    }

    public int normalizedFrameCount() {
        long total = totalSamples();
        int normalizedFrameSize = normalizedFrameSize();
        long count = (total + normalizedFrameSize - 1L) / normalizedFrameSize;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Liczba ramek jest zbyt duza");
        }
        return (int) count;
    }

    public RedPitayaConfig forTotalSamples(long requestedSamples) {
        RedPitayaConfig c = copy();
        long total = Math.max(2L, requestedSamples);
        long remainder = total % 4096;
        if (remainder != 0) {
            total += 4096 - remainder;
        }
        int normalizedFrameSize = c.normalizedFrameSize();
        long count = (total + normalizedFrameSize - 1L) / normalizedFrameSize;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Liczba ramek jest zbyt duza");
        }
        c.durationMode = true;
        c.frameCount = (int) count;
        c.durationS = total / c.sampleRate();
        return c;
    }

    public double normalizedDurationS() {
        return totalSamples() / sampleRate();
    }

    public void validate(int maxFrameSamples) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Adres Red Pitaya jest pusty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port musi byc w zakresie 1..65535");
        }
        if (decimation < 1 || decimation > 65536) {
            throw new IllegalArgumentException("Decymacja musi byc w zakresie 1..65536");
        }
        if (channels.length == 0 || Arrays.stream(channels).anyMatch(ch -> ch != 1 && ch != 2)) {
            throw new IllegalArgumentException("Nieprawidlowy wybor kanalow");
        }
        if (Arrays.stream(channels).noneMatch(ch -> ch == visualChannel)) {
            throw new IllegalArgumentException("Kanal wizualizacji musi byc wsrod odbieranych kanalow");
        }
        if (!"LV".equals(gainCh1) && !"HV".equals(gainCh1)) {
            throw new IllegalArgumentException("Zakres IN1 musi byc LV albo HV");
        }
        if (!"LV".equals(gainCh2) && !"HV".equals(gainCh2)) {
            throw new IllegalArgumentException("Zakres IN2 musi byc LV albo HV");
        }
        if (normalizedFrameSize() > maxFrameSamples) {
            throw new IllegalArgumentException("Rozmiar ramki nie moze przekraczac " + maxFrameSamples + " probek");
        }
        if (normalizedFrameCount() < 1) {
            throw new IllegalArgumentException("Liczba ramek musi byc dodatnia");
        }
        if (triggerTimeoutS <= 0.0) {
            throw new IllegalArgumentException("Timeout triggera musi byc dodatni");
        }
    }

    public String toJson() {
        return SimpleJson.stringify(toPayload());
    }

    public Map<String, Object> toPayload() {
        validate(Integer.MAX_VALUE);
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        LinkedHashMap<String, Object> gains = new LinkedHashMap<>();
        gains.put("1", gainCh1);
        gains.put("2", gainCh2);

        int normalizedFrameSize = normalizedFrameSize();
        int normalizedFrameCount = normalizedFrameCount();
        long totalSamples = totalSamples();

        root.put("command", "acquire");
        root.put("client_host", host);
        root.put("client_port", port);
        root.put("channels", channels);
        root.put("gains", gains);
        root.put("gain", channels[0] == 1 ? gainCh1 : gainCh2);
        root.put("board_model", BOARD_MODEL);
        root.put("decimation", decimation);
        root.put("averaging", averaging);
        root.put("trigger_source", triggerSource);
        root.put("trigger_level", triggerLevel);
        root.put("trigger_delay", triggerDelay);
        root.put("trigger_timeout_s", triggerTimeoutS);
        root.put("acquisition_mode", durationMode ? "duration" : "frames");
        root.put("duration_s", normalizedDurationS());
        root.put("frame_size", normalizedFrameSize);
        root.put("frame_count", normalizedFrameCount);
        root.put("total_samples", totalSamples);
        root.put("sample_rate", sampleRate());
        root.put("dtype", "int16");
        root.put("units", "RAW");
        return root;
    }

    public String gainForChannel(int channel) {
        return channel == 1 ? gainCh1 : gainCh2;
    }

    public int visualChannelIndex() {
        for (int i = 0; i < channels.length; i++) {
            if (channels[i] == visualChannel) {
                return i;
            }
        }
        return 0;
    }
}
