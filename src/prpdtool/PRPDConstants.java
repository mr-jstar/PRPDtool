package prpdtool;

import java.awt.Color;
import java.awt.Font;
import java.util.Map;

public class PRPDConstants {

    // PRPD defaults
    public static final double DEFAULT_THRESHOLD = 0.012;
    public static final double DEFAULT_FS = 1e6;
    public static final double DEFAULT_CUTOFF = 1e5;

    // Signal processing
    public static final double DEFAULT_RP_DURATION = 0.01;
    public static final double DEFAULT_FILTER_Q = 3.0;

    // Configuration keys
    public static final String DARK_MODE = "PRPDMonitor.dark.mode";

    public static boolean isDarkTheme() {
        try {
            return javax.swing.UIManager.getLookAndFeel().getName().toLowerCase().contains("dark");
        } catch (Exception e) {
            return false;
        }
    }

    // Display / Window defaults
    public static final int DEFAULT_WINDOW_WIDTH = 1600;
    public static final int DEFAULT_WINDOW_HEIGHT = 1024;
    public static final int DEFAULT_HISTOGRAM_WIDTH = 360;
    public static final int DEFAULT_HISTOGRAM_HEIGHT = 224;

    // Signal rendering
    public static final int SIGNAL_MAX_POINTS = 3_000_000;
    public static final int SIGNAL_TARGET_POINTS_PER_BUFFER = 25_000;

    // GUI Configurations
    public static final String CONFIG_FILE = ".prpd_config";
    public static final Font[] FONTS = {
        new Font("Courier", Font.PLAIN, 12),
        new Font("Courier", Font.PLAIN, 16),
        new Font("Courier", Font.PLAIN, 18)
    };

    // Classifiers
    public static final String[] CLASSES = {
        "floating",
        "corona -",
        "noise",
        "corona +",
        "surface",
        "void"
    };

    public static final Map<String, Color> CLASS_COLORS = Map.of(
            "floating", new Color(255, 200, 0),
            "corona -", new Color(0, 180, 255),
            "noise", new Color(140, 140, 140),
            "corona +", new Color(255, 60, 60),
            "surface", new Color(0, 220, 120),
            "void", new Color(180, 0, 255)
    );
}
