package pipeline;

public enum PRPDPalette {
    HIGH_CONTRAST("High Contrast (Jaskrawa tęcza)"),
    HOT_FIRE("Hot Fire (Płomień)"),
    ELECTRIC_PLASMA("Electric Plasma (Neon)"),
    TURBO("Turbo (Vivid Jet)"),
    CLASSIC("Classic Heat (Oryginalna)");

    private final String displayName;

    PRPDPalette(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColorRGB(double v) {
        v = Math.max(0.0, Math.min(1.0, v));
        int r, g, b;
        switch (this) {
            case HIGH_CONTRAST -> {
                // Jaskrawy cyjan dla rzadkich impulsów -> zieleń -> żółć -> pomarańcz/czerwień -> biel
                if (v < 0.25) {
                    double k = v / 0.25;
                    r = 0;
                    g = (int) (220 + 35 * k);
                    b = (int) (255 - 155 * k);
                } else if (v < 0.50) {
                    double k = (v - 0.25) / 0.25;
                    r = (int) (255 * k);
                    g = 255;
                    b = (int) (100 * (1.0 - k));
                } else if (v < 0.75) {
                    double k = (v - 0.50) / 0.25;
                    r = 255;
                    g = (int) (255 - 195 * k);
                    b = 0;
                } else {
                    double k = (v - 0.75) / 0.25;
                    r = 255;
                    g = (int) (60 + 195 * k);
                    b = (int) (255 * k);
                }
            }
            case HOT_FIRE -> {
                // Jaskrawy bursztyn/pomarańcz -> intensywna żółć -> biel
                if (v < 0.40) {
                    double k = v / 0.40;
                    r = 255;
                    g = (int) (90 + 140 * k);
                    b = 0;
                } else if (v < 0.70) {
                    double k = (v - 0.40) / 0.30;
                    r = 255;
                    g = (int) (230 + 25 * k);
                    b = (int) (160 * k);
                } else {
                    double k = (v - 0.70) / 0.30;
                    r = 255;
                    g = 255;
                    b = (int) (160 + 95 * k);
                }
            }
            case ELECTRIC_PLASMA -> {
                // Neonowa magenta/fuksja -> koralowy pomarańcz -> żółć -> biel
                if (v < 0.35) {
                    double k = v / 0.35;
                    r = (int) (235 + 20 * k);
                    g = (int) (30 + 80 * k);
                    b = (int) (235 - 205 * k);
                } else if (v < 0.70) {
                    double k = (v - 0.35) / 0.35;
                    r = 255;
                    g = (int) (110 + 130 * k);
                    b = (int) (30 - 10 * k);
                } else {
                    double k = (v - 0.70) / 0.30;
                    r = 255;
                    g = (int) (240 + 15 * k);
                    b = (int) (20 + 235 * k);
                }
            }
            case TURBO -> {
                // Żywy błękit -> cyjan -> zieleń -> żółć -> czerwień
                if (v < 0.25) {
                    double k = v / 0.25;
                    r = (int) (50 - 50 * k);
                    g = (int) (130 + 100 * k);
                    b = (int) (255 - 15 * k);
                } else if (v < 0.50) {
                    double k = (v - 0.25) / 0.25;
                    r = (int) (50 * k);
                    g = 230 + (int) (25 * k);
                    b = (int) (240 * (1.0 - k) + 50 * k);
                } else if (v < 0.75) {
                    double k = (v - 0.50) / 0.25;
                    r = (int) (50 + 205 * k);
                    g = 255 - (int) (15 * k);
                    b = 0;
                } else {
                    double k = (v - 0.75) / 0.25;
                    r = 255;
                    g = (int) (240 - 210 * k);
                    b = 0;
                }
            }
            default -> { // CLASSIC
                if (v < 0.25) {
                    double k = v / 0.25;
                    r = 0;
                    g = 0;
                    b = (int) (80 + 175 * k);
                } else if (v < 0.50) {
                    double k = (v - 0.25) / 0.25;
                    r = 0;
                    g = (int) (255 * k);
                    b = 255;
                } else if (v < 0.75) {
                    double k = (v - 0.50) / 0.25;
                    r = (int) (255 * k);
                    g = 255;
                    b = (int) (255 * (1.0 - k));
                } else {
                    double k = (v - 0.75) / 0.25;
                    r = 255;
                    g = (int) (255 * (1.0 - k));
                    b = 0;
                }
            }
        }
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
