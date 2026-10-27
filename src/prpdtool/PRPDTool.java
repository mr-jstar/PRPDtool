package prpdtool;

/**
 *
 * @author jstar
 */
import classifiers.*;
import javax.swing.Timer;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.imageio.ImageIO;
import pipeline.Buffer;
import pipeline.BufferFactory;
import pipeline.DynamicPRPDHistogram;
import dsp.Filter;
import dsp.HighPassFilter;
import dsp.LowPassFilter;
import dsp.PhaseEstimator;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.text.ParseException;
import java.text.DecimalFormat;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import pipeline.PRPDExtractorCore;
import pipeline.PRPDHistogram;
import pipeline.PRPDPipeline;
import pipeline.PRPDPipelineListener;
import pipeline.Pulses;
import pipeline.PRPDPalette;
import redpitaya.RedPitayaConfig;
import redpitaya.RedPitayaSignalReader;
import redpitaya.RpprFileSignalReader;

public class PRPDTool extends JFrame {

    private JDialog classifyDialog;
    private volatile boolean inBatchMode;
    private volatile boolean realTimeData;
    private String serverPort = "127.0.0.1:7777";

    private final ArrayList<Classifier> classifiers = new ArrayList<>();

    private Map<Classifier, JLabel> cResults;

    private BufferedImage prpd4YOLO;

    // GUI config
    private Font currentFont = PRPDConstants.FONTS[1];

    private static final String CONFIG_FILE = ".prpd_config";
    private final Configuration configuration = new Configuration(CONFIG_FILE);

    private final String LAST_DIR = "PRPDMonitor.last.dir";
    private final String MODELS_DIR = "PRPDMonitor.models.dir";
    private final String FONTSIZE = "PRPDMonitor.font.size";
    private final String FRAMESIZE = "PRPDMonitor.frame.size";
    private final String RP_HOST = "PRPDMonitor.rp.host";
    private final String RP_PORT = "PRPDMonitor.rp.port";
    private final String RP_CHANNELS = "PRPDMonitor.rp.channels";
    private final String RP_VISUAL_CHANNEL = "PRPDMonitor.rp.visual.channel";
    private final String RP_GAIN1 = "PRPDMonitor.rp.gain1";
    private final String RP_GAIN2 = "PRPDMonitor.rp.gain2";
    private final String RP_DECIMATION = "PRPDMonitor.rp.decimation";
    private final String RP_AVERAGING = "PRPDMonitor.rp.averaging";
    private final String RP_TRIGGER_SOURCE = "PRPDMonitor.rp.trigger.source";
    private final String RP_TRIGGER_LEVEL = "PRPDMonitor.rp.trigger.level";
    private final String RP_TRIGGER_DELAY = "PRPDMonitor.rp.trigger.delay";
    private final String RP_TRIGGER_TIMEOUT = "PRPDMonitor.rp.trigger.timeout";
    private final String RP_MODE = "PRPDMonitor.rp.mode";
    private final String RP_DURATION = "PRPDMonitor.rp.duration";
    private final String RP_FRAME_SIZE = "PRPDMonitor.rp.frame.size";
    private final String RP_FRAME_COUNT = "PRPDMonitor.rp.frame.count";
    private final String RP_FILE_LIMIT = "PRPDMonitor.rp.file.limit";
    private final String RP_FILE_PREFIX = "PRPDMonitor.rp.file.prefix";
    private final String FAST_RENDERING = "PRPDMonitor.fast.rendering";
    // --- Metadane eksperymentu ---
    private final String RP_DEFECT_TYPE   = "PRPDMonitor.rp.defect.type";
    private final String RP_VOLTAGE_KV    = "PRPDMonitor.rp.voltage.kv";
    private final String RP_SENSOR        = "PRPDMonitor.rp.sensor";
    private final String RP_VARIANT       = "PRPDMonitor.rp.variant";
    private final String RP_SESSION_LABEL = "PRPDMonitor.rp.session.label";

    private JPanel left;
    private ImagePanel center;
    private JPanel right;
    private JPanel bottom;
    private JPanel paramPanel;
    private JPanel modelPanel;

    private JSplitPane splitLeft;
    private JSplitPane verticalSplit;

    private final JLabel status = new JLabel("") {
        @Override
        public void setText(String text) {
            super.setText(text);
            setToolTipText(text);
        }
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = Math.min(d.width, 200);
            return d;
        }
    };
    private boolean filterValid = false;
    private int rpFileLimit = 30;
    private String rpFilePrefix = "rp_";
    private final java.util.Queue<File> currentSessionFiles = new java.util.LinkedList<>();
    private final File receivedSignalsDir = new File("data" + File.separator + "received_bin");

    // PRPD config
    private double f0 = 50;
    private volatile double t0 = 0;
    private volatile double fs = PRPDConstants.DEFAULT_FS;  // próbkowanie 
    private volatile double dfs = fs;  // próbkowanie z danych
    private double threshold = PRPDConstants.DEFAULT_THRESHOLD; //próg detekcji impulsu po odjęciu tła
    private double cutF = PRPDConstants.DEFAULT_CUTOFF; // f odcięcia

    private double ampMin = 0.0; // minimum histogramu
    private double ampMax = 100.0; // maximum histogramu
    private double deadUs = PRPDConstants.DEFAULT_DEAD_US; //martwy czas po wykryciu impulsu [µs]
    private double filterQ = 0.707; // Q filtra
    private int filterOrder = 4; // rząd filtra

    private AtomicBoolean signalStart = new AtomicBoolean(false);

    Param<?>[] params = {
        Param.dbl("Basic frequency [Hz]", () -> f0, v -> f0 = v),
        Param.dbl("Zero-crossing instant [s]", () -> t0, v -> t0 = v),
        Param.dbl("Sampling frequency [Hz]", () -> fs, v -> fs = v),
        Param.dbl("Pulse ampl. threshold [mV]", () -> threshold, v -> threshold = v),
        Param.dbl("Dead time [us]", () -> deadUs, v -> deadUs = v),
        Param.dbl("HPF cutoff frequency [Hz]", () -> cutF, v -> cutF = v),
        Param.dbl("Filter Q", () -> filterQ, v -> filterQ = v),
        Param.integer("Filter Order", () -> filterOrder, v -> filterOrder = v),
        Param.dbl("Histogram min [V]", () -> ampMin, v -> ampMin = v),
        Param.dbl("Histogram max [V]", () -> ampMax, v -> ampMax = v)
    };

    // Misc options
    private boolean drawF0 = true;
    private boolean bipolarHistogram;
    private boolean fastRendering = true;
    private PRPDPalette currentPalette = PRPDPalette.HIGH_CONTRAST;

    private int topPlotMode = 2; // 0 = Envelope, 1 = Baseline, 2 = Base signal (Raw)

    // Data
    private InteractiveSignalPanel interactiveSignalPanel;
    private static final int MAX_CACHED_SIGNAL_SAMPLES = 25_000_000;
    private static final long RP_LIVE_RESTART_DELAY_MS = 500L;
    private final ReceivedSignalCache receivedSignalCache = new ReceivedSignalCache(MAX_CACHED_SIGNAL_SAMPLES);

    private String lastDataFile;
    private PRPDHistogram histogram;
    private PRPDPipeline pipeline;
    private PRPDExtractorCore extractor;

    private JLabel dataSource;

    private JLabel paramChange;
    private JButton applyButton;
    private JCheckBox autoscaleCb;
    private JCheckBox showRawDataCb;
    private JCheckBox showImpulseBarsCb;
    private JCheckBox useHwPhaseRefCb;

    private JTextField rpHostField;
    private JSpinner rpPortSpinner;
    private JComboBox<String> rpChannelsCombo;
    private JComboBox<String> rpVisualChannelCombo;
    private JComboBox<String> rpGain1Combo;
    private JComboBox<String> rpGain2Combo;
    private JSpinner rpDecimationSpinner;
    private JCheckBox rpAveragingBox;
    private JComboBox<String> rpTriggerCombo;
    private JSpinner rpTriggerLevelSpinner;
    private JSpinner rpTriggerDelaySpinner;
    private JSpinner rpTriggerTimeoutSpinner;
    public static final class FrameSizeItem {
        public final int size;
        public final String label;

        public FrameSizeItem(int size, String label) {
            this.size = size;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof FrameSizeItem other) {
                return this.size == other.size;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(size);
        }
    }

    private static final FrameSizeItem[] FRAME_SIZE_OPTIONS = {
        new FrameSizeItem(4_194_304, "4 194 304 (4 MS)"),
        new FrameSizeItem(2_097_152, "2 097 152 (2 MS)"),
        new FrameSizeItem(1_048_576, "1 048 576 (1 MS)"),
        new FrameSizeItem(524_288, "524 288 (512 kS)"),
        new FrameSizeItem(65_536, "65 536 (64 kS)")
    };

    private JComboBox<String> rpModeCombo;
    private JSpinner rpDurationSpinner;
    private JComboBox<FrameSizeItem> rpFrameSizeCombo;
    private JSpinner rpFrameCountSpinner;
    private JTextField rpFrameTimeField;
    private JLabel rpMaxFilesDisplayLabel;
    private JLabel rpEstimatedSizeLabel;
    private JLabel rpSettingsEstimatedSizeLabel;
    private JDialog rpSettingsDialog;
    private boolean updatingRedPitayaDerivedFields;
    private JButton rpStartOnceButton;
            private JButton rpStartLiveButton;
    private JButton rpStopButton;
    private JTextField rpFilePrefixField;
    private JButton rpTriggerIn1Button;
    private JButton rpTriggerIn2Button;
    private JLabel mainTriggerModeValue;
    private JLabel mainTriggerLevelValue;
    // --- Komponenty metadanych eksperymentu (zadania 1-4) ---
    private JComboBox<String> rpDefectTypeCombo;
    private JTextField rpVoltageKvField;
    private JComboBox<String> rpSensorCombo;
    private JComboBox<String> rpVariantCombo;
    private JTextField rpSessionLabelField;
    private JLabel outputDirLabel;
    private JLabel outputFileLabel;
    private JLabel dialogPreviewDirLabel;
    private JLabel dialogPreviewFileLabel;
    private JTextField rpOutputDirField;
    private JButton rpOutputDirBtn;

    private JButton classifyButton;
    private DefaultListModel<File> receivedSignalsModel;
    private JList<File> receivedSignalsList;

    private static final class CachedBuffer {

        final double[] t;
        final double[] u;
        final double[] u2;
        final int used;

        CachedBuffer(double[] t, double[] u, double[] u2, int used) {
            this.t = t;
            this.u = u;
            this.u2 = u2;
            this.used = used;
        }
    }

    private static final class ReceivedSignalCache {

        private final int maxSamples;
        private final ArrayList<CachedBuffer> buffers = new ArrayList<>();
        private int samples;

        ReceivedSignalCache(int maxSamples) {
            this.maxSamples = maxSamples;
        }

        synchronized void reset() {
            buffers.clear();
            samples = 0;
        }

        synchronized void add(Buffer buffer) {
            if (buffer == null || buffer.used <= 0) {
                return;
            }
            int n = buffer.used;
            double[] u2Copy = (buffer.u2 != null) ? Arrays.copyOf(buffer.u2, n) : null;
            buffers.add(new CachedBuffer(
                    Arrays.copyOf(buffer.t, n),
                    Arrays.copyOf(buffer.u, n),
                    u2Copy,
                    n
            ));
            samples += n;
            while (samples > maxSamples && !buffers.isEmpty()) {
                CachedBuffer removed = buffers.remove(0);
                samples -= removed.used;
            }
        }

        synchronized List<CachedBuffer> snapshot() {
            return new ArrayList<>(buffers);
        }
    }

    private static abstract class Param<T> {

        final String name;

        JTextField field;

        Param(String name) {
            this.name = name;
        }

        void setField(JTextField field) {
            this.field = field;
        }

        abstract String getText();

        abstract void setFromText(String text);

        void setFromField() {
            if (field != null) {
                setFromText(field.getText());
            }
        }

        static Param<Double> dbl(String name, DoubleSupplier getter, DoubleConsumer setter) {
            return new Param<>(name) {
                @Override
                String getText() {
                    return Double.toString(getter.getAsDouble());
                }

                @Override
                void setFromText(String text) {
                    setter.accept(Double.parseDouble(text.trim()));
                }
            };
        }

        static Param<Integer> integer(String name, IntSupplier getter, IntConsumer setter) {
            return new Param<>(name) {
                @Override
                String getText() {
                    return Integer.toString(getter.getAsInt());
                }

                @Override
                void setFromText(String text) {
                    setter.accept(Integer.parseInt(text.trim()));
                }
            };
        }
    }

    public PRPDTool() {
        super("PRPDtool");
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay(60000); // 60 seconds

        try {
            rpFileLimit = Integer.parseInt(configuration.getValue(RP_FILE_LIMIT).trim());
            if (rpFileLimit < 1) rpFileLimit = 30;
        } catch (Exception ex) {
        }
        try {
            String p = configuration.getValue(RP_FILE_PREFIX);
            if (p != null && !p.trim().isEmpty()) {
                rpFilePrefix = p.trim();
            }
        } catch (Exception ex) {
        }
        try {
            String fr = configuration.getValue(FAST_RENDERING);
            if (fr != null) fastRendering = Boolean.parseBoolean(fr.trim());
        } catch (Exception ex) {
        }
        try {
            String palStr = configuration.getValue(PRPDConstants.PRPD_PALETTE);
            if (palStr != null) {
                currentPalette = PRPDPalette.valueOf(palStr.trim());
            }
        } catch (Exception ex) {
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });

        try {
            String[] wh = configuration.getValue(FRAMESIZE).trim().split("x");
            int w = Integer.parseInt(wh[0]);
            int h = Integer.parseInt(wh[1]);
            setSize(w, h);
        } catch (Exception ex) {
            setSize(1600, 1024);
        }
        setLocationRelativeTo(null);

        try {
            int size = Integer.parseInt(configuration.getValue(FONTSIZE));
            for (Font f : PRPDConstants.FONTS) {
                if (Math.abs(f.getSize() - size) < Math.abs(currentFont.getSize() - size)) {
                    currentFont = f;
                }
            }
        } catch (Exception e) {
        }

        try {
            String modelsDir = configuration.getValue(MODELS_DIR);
            String[] onnx = Files.list(Paths.get(modelsDir))
                    .filter(p -> {
                        String s = p.toString().toLowerCase();
                        return s.endsWith(".onnx") || s.endsWith(".zip");
                    }).map(Path::toString).toArray(String[]::new);
            Arrays.sort(onnx);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    for (String s : onnx) {
                        System.err.println("Found model in " + s);
                        try {
                            if (s.toLowerCase().contains("mobileyolo")) {
                                Classifier c = new ONNXClassifier(
                                        s, new PreprocessorYOLO(),
                                        new MobileYOLOParser(7, 9, PRPDConstants.CLASSES, 0.5f, 0.55f));
                                if (c.ok()) {
                                    classifiers.add(c);
                                }
                            } else if (s.toLowerCase().contains("squeezeyolo")) {
                                Classifier c = new ONNXClassifier(
                                        s, new PreprocessorYOLO(),
                                        new SqueezeYOLOParser(7, 9, PRPDConstants.CLASSES, 0.25f)
                                );
                                if (c.ok()) {
                                    classifiers.add(c);
                                }
                            } else if (s.toLowerCase().contains("rtdetr")) {
                                Classifier c = new ONNXClassifier(
                                        s, new PreprocessorRTDETR(),
                                        new RTDETRParser(300, PRPDConstants.CLASSES, 0.25f)
                                );
                                if (c.ok()) {
                                    classifiers.add(c);
                                }
                            }
                        } catch (Exception ex) {
                            System.err.println(ex.getMessage());
                        }
                    }
                    return null;
                }
                @Override
                protected void done() {
                    updateModelPanel();
                }
            }.execute();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
        createMenuBar();
        initGui();
        setCurrentFont();
    }

    private void createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu fileM = new JMenu("File");
        JMenuItem fileMI = new JMenuItem("Read (t,u) from file");
        fileMI.addActionListener(e -> loadFile());
        fileM.add(fileMI);
        fileM.addSeparator();

        JMenuItem exportMI = new JMenuItem("Export histogram data");
        exportMI.addActionListener(e -> exportHistogram());
        fileM.add(exportMI);

        JMenuItem imageMI = new JMenuItem("Export histogram image");
        imageMI.addActionListener(e -> exportImage());
        fileM.add(imageMI);

        JMenuItem exportCsvMI = new JMenuItem("Export raw pulses to CSV");
        exportCsvMI.addActionListener(e -> exportRawPulsesCSV());
        fileM.add(exportCsvMI);

        JMenuItem prpdMI = new JMenuItem("Export YOLO image");
        prpdMI.addActionListener(e -> exportPRPD4YOLODialog());
        fileM.add(prpdMI);

        fileM.addSeparator();

        JMenuItem exitMI = new JMenuItem("Exit");
        exitMI.addActionListener(e -> onExit());
        fileM.add(exitMI);
        mb.add(fileM);

        JMenu scriptsM = new JMenu("Machine Learning");
        JMenuItem batchMI = new JMenuItem("Batch Export (Dir -> YOLO PNGs)");
        batchMI.addActionListener(e -> dir2prpd());
        scriptsM.add(batchMI);
        
        JMenuItem classifyMI = new JMenuItem("Real-time Classification");
        classifyMI.addActionListener(e -> {
            if (classifyDialog != null) {
                classifyDialog.setVisible(true);
            }
        });
        scriptsM.add(classifyMI);
        
        mb.add(scriptsM);

        JMenu profilesM = new JMenu("Profiles");
        buildProfilesMenu(profilesM);
        mb.add(profilesM);

        JMenu optM = new JMenu("View");
        JMenu fontM = new JMenu("Font size");
        ButtonGroup fgroup = new ButtonGroup();
        for (Font f : PRPDConstants.FONTS) {
            JRadioButtonMenuItem fontOpt = new JRadioButtonMenuItem(String.valueOf(f.getSize()));
            final Font cf = f;
            fontOpt.addActionListener(e -> {
                currentFont = cf;
                setCurrentFont();
                try {
                    configuration.saveValue(FONTSIZE, "" + cf.getSize());
                } catch (IOException ex) {
                }
            });
            fontOpt.setSelected(f == currentFont);
            fgroup.add(fontOpt);
            fontM.add(fontOpt);
        }
        optM.add(fontM);
        optM.addSeparator();

        JCheckBoxMenuItem sinMB = new JCheckBoxMenuItem("Draw base sine", drawF0);
        sinMB.addActionListener(e -> {
            drawF0 = sinMB.isSelected();
            histogram.drawF0(drawF0);
            if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
        });
        optM.add(sinMB);

        JCheckBoxMenuItem bipolarMB = new JCheckBoxMenuItem("Bipolar histogram", bipolarHistogram);
        bipolarMB.addActionListener(e -> {
            if (bipolarMB.isSelected()) {
                bipolarHistogram = true;
                setParamField("Histogram min [V]", "-" + roundme(histogram.getDataMax(), 3));
            } else {
                bipolarHistogram = false;
                setParamField("Histogram min [V]", "0");
            }
            onParameterChanged();
        });
        optM.add(bipolarMB);

        JMenu paletteM = new JMenu("PRPD color palette");
        ButtonGroup pgroup = new ButtonGroup();
        for (PRPDPalette pal : PRPDPalette.values()) {
            JRadioButtonMenuItem palOpt = new JRadioButtonMenuItem(pal.getDisplayName());
            palOpt.setSelected(pal == currentPalette);
            palOpt.addActionListener(e -> {
                currentPalette = pal;
                if (histogram != null) {
                    histogram.setPalette(pal);
                    center.setImage(histogram.getImage());
                    center.repaint();
                }
                try {
                    configuration.saveValue(PRPDConstants.PRPD_PALETTE, pal.name());
                } catch (Exception ex) {}
            });
            pgroup.add(palOpt);
            paletteM.add(palOpt);
        }
        optM.add(paletteM);

        // "Fit histogram to data" removed from menu, moved to FIT button in UI
        optM.addSeparator();
        
        JMenuItem fastMB = new JCheckBoxMenuItem("Fast rendering (no interpolator)", fastRendering);
        fastMB.addActionListener(ev -> {
            fastRendering = fastMB.isSelected();
            if (histogram instanceof DynamicPRPDHistogram) {
                ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
            }
            try {
                configuration.saveValue(FAST_RENDERING, "" + fastRendering);
            } catch (Exception ex) {}
            if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
        });
        optM.add(fastMB);
        
        JMenuItem cursorsMB = new JCheckBoxMenuItem("Show signal cursors", false);
        cursorsMB.addActionListener(ev -> {
            if (interactiveSignalPanel != null) {
                interactiveSignalPanel.setShowCursors(cursorsMB.isSelected());
            }
        });
        optM.add(cursorsMB);

        JMenuItem prpdCursorsMB = new JCheckBoxMenuItem("Show PRPD cursors", false);
        prpdCursorsMB.addActionListener(ev -> {
            if (center != null) {
                center.setShowCursors(prpdCursorsMB.isSelected());
            }
        });
        optM.add(prpdCursorsMB);

        JCheckBoxMenuItem darkModeCheckbox = new JCheckBoxMenuItem("Dark Mode", isDarkMode());
        darkModeCheckbox.addActionListener(ev -> {
            boolean isDark = darkModeCheckbox.isSelected();
            try {
                configuration.saveValue(PRPDConstants.DARK_MODE, Boolean.toString(isDark));
                if (isDark) {
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                } else {
                    UIManager.setLookAndFeel(new FlatLightLaf());
                }
                for (java.awt.Window window : java.awt.Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window);
                }
                if (histogram != null && center != null) {
                    if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                        ((pipeline.DynamicPRPDHistogram) histogram).forceRedraw();
                    }
                    center.setImage(histogram.getImage());
                    center.repaint();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        optM.add(darkModeCheckbox);
        
        optM.addSeparator();

        ButtonGroup egroup = new ButtonGroup();
        
        JRadioButtonMenuItem rawOpt = new JRadioButtonMenuItem("\t\tBase signal");
        rawOpt.setSelected(true);
        rawOpt.addActionListener(e -> {
            topPlotMode = 2;
            if (lastDataFile != null) {
                try {
                    getData(lastDataFile);
                } catch (Exception ex) {}
            } else {
                interactiveSignalPanel.reset("Base signal", null, "Filtered signal", null, false);
            }
        });
        egroup.add(rawOpt);
        optM.add(rawOpt);

        JRadioButtonMenuItem eOpt = new JRadioButtonMenuItem("\t\tEnvelope");
        eOpt.addActionListener(e -> {
            topPlotMode = 0;
            if (lastDataFile != null) {
                try {
                    getData(lastDataFile);
                } catch (Exception ex) {}
            } else {
                interactiveSignalPanel.reset("Signal envelope", null, "Filtered signal", null, false);
            }
        });
        egroup.add(eOpt);
        optM.add(eOpt);

        JRadioButtonMenuItem bOpt = new JRadioButtonMenuItem("\t\tBaseline");
        bOpt.addActionListener(e -> {
            topPlotMode = 1;
            if (lastDataFile != null) {
                try {
                    getData(lastDataFile);
                } catch (Exception ex) {}
            } else {
                interactiveSignalPanel.reset("Baseline signal", null, "Filtered signal", null, false);
            }
        });
        egroup.add(bOpt);
        optM.add(bOpt);

        mb.add(optM);

        JMenu rpMenu = new JMenu("Red Pitaya");
        JMenuItem rpSettingsMI = new JMenuItem("Settings");
        rpSettingsMI.addActionListener(e -> {
            if (rpSettingsDialog != null) {
                updateRedPitayaDerivedControls();
                rpSettingsDialog.setVisible(true);
            }
        });
        rpMenu.add(rpSettingsMI);
        mb.add(rpMenu);

        JMenu aboutMenu = new JMenu("About") {
            @Override
            public void setPopupMenuVisible(boolean b) {
                // Zapobiega wyskakiwaniu pustego okienka popup
            }
            @Override
            public void doClick(int pressTime) {
                javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                showAboutDialog();
            }
        };
        aboutMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                    showAboutDialog();
                }
            }
        });
        mb.add(aboutMenu);

        setJMenuBar(mb);
    }

    private boolean isDarkMode() {
        try {
            String darkStr = configuration.getValue(PRPDConstants.DARK_MODE);
            return darkStr != null && Boolean.parseBoolean(darkStr);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean aboutDialogShowing = false;

    private void showAboutDialog() {
        if (aboutDialogShowing) return;
        aboutDialogShowing = true;
        try {
            boolean dark = isDarkMode();
            String titleColor = dark ? "#ffffff" : "#000000";
            String bodyColor = dark ? "#dcdcdc" : "#222222";
            String boldColor = dark ? "#ffffff" : "#000000";
            String hrColor = dark ? "#555555" : "#cccccc";
            String codeBg = dark ? "#383a3e" : "#f0f0f0";
            String quoteBg = dark ? "#33363a" : "#f4f4f4";
            String quoteBorder = dark ? "#666666" : "#aaaaaa";
            String bgColor = dark ? "#2b2d30" : "#ffffff";

            String style = String.format(Locale.US,
                "body { font-family: Segoe UI, Arial, sans-serif; font-size: 11px; color: %s; background-color: %s; margin: 10px; }\n"
                + "h2 { font-size: 15px; font-weight: bold; color: %s; margin-top: 4px; margin-bottom: 8px; }\n"
                + "h3 { font-size: 13px; font-weight: bold; color: %s; margin-top: 14px; margin-bottom: 5px; }\n"
                + "p, li { font-size: 11px; color: %s; line-height: 1.4; margin-bottom: 4px; }\n"
                + "b, strong { color: %s; font-weight: bold; }\n"
                + "code { font-family: Consolas, monospace; font-size: 11px; background-color: %s; padding: 1px 4px; }\n"
                + "hr { border: 0; border-top: 1px solid %s; margin: 10px 0; }\n"
                + "blockquote { margin: 6px 0; padding: 4px 8px; background-color: %s; border-left: 3px solid %s; }\n"
                + "ol { margin-top: 4px; margin-bottom: 6px; padding-left: 20px; }\n"
                + "ul { margin-top: 4px; margin-bottom: 6px; padding-left: 20px; }\n"
                + "li { margin-bottom: 4px; }\n",
                bodyColor, bgColor,
                titleColor,
                titleColor,
                bodyColor,
                boldColor,
                codeBg,
                hrColor,
                quoteBg, quoteBorder
            );

            String html = "<html><head><style>" + style + "</style></head><body>"
                + """
                <h2>PRPD Tool &ndash; jak działa program?</h2>
                <p>Program służy do rejestracji i analizy <b>wyładowań niezupełnych (WNZ / PD &ndash; Partial Discharge)</b>
                w urządzeniach elektroenergetycznych (np. rozdzielnicach SN, transformatorach, kablach).
                Poniżej opisano krok po kroku, co dzieje się z sygnałem od chwili wejścia na przetwornik ADC
                aż do wygenerowania wykresu PRPD.</p>
                <hr>

                <h3>1. Akwizycja &ndash; skąd pochodzi sygnał?</h3>
                <p>Sygnał pomiarowy pochodzi z karty <b>Red Pitaya STEMlab 125-14</b> &ndash; miniaturowego systemu akwizycji
                z 14-bitowym przetwornikiem ADC o częstotliwości próbkowania do <b>125 MS/s</b> (125 milionów próbek
                na sekundę, regulowanej decymacją).</p>
                <ul>
                  <li><b>CH1 (IN1)</b> &ndash; główny kanał pomiarowy. Podłącza się tu czujnik WNZ
                      (np. cewkę HFCT, czujnik TEV lub antenę VHF). Pracuje zwykle w zakresie <b>LV (Low Voltage, &plusmn;1&nbsp;V)</b>
                      dla zapewnienia maksymalnej czułości i rozdzielczości pomiaru.</li>
                  <li><b>CH2 (IN2)</b> &ndash; kanał sygnału referencyjnego napięcia zasilającego 50&nbsp;Hz.
                      Podłącza się tu wyjście dzielnika napięcia lub sygnał referencyjny z generatora.
                      Pracuje zwykle w zakresie <b>HV (High Voltage, &plusmn;20&nbsp;V)</b>.</li>
                </ul>
                <p>Po naciśnięciu <b>Start once</b> (jeden pomiar) lub <b>Start live</b> (ciągły podgląd na żywo)
                program wysyła ramkę konfiguracyjną JSON przez połączenie TCP/IP (port&nbsp;9999). Agent Python na płytce
                konfiguruje rejestry FPGA, uzbraja trigger i przesyła bufor próbek w pamięci DDR RAM (AXI DMA)
                z powrotem do komputera.</p>

                <h3>2. Przetwarzanie wstępne &ndash; przeliczenie na miliwolty [mV]</h3>
                <p>Każda odebrana próbka to 14-bitowa liczba całkowita ze znakiem (int16 od &minus;8192 do +8191).
                Program przelicza każdą wartość na <b>miliwolty [mV]</b> według wybranego zakresu wejścia:</p>
                <ul>
                  <li><b>LV (&plusmn;1&nbsp;V)</b>: 1 LSB = 1000&nbsp;mV / 8192 &asymp; <b>0,122&nbsp;mV</b></li>
                  <li><b>HV (&plusmn;20&nbsp;V)</b>: 1 LSB = 20&nbsp;000&nbsp;mV / 8192 &asymp; <b>2,441&nbsp;mV</b> (20-krotnie większy zakres)</li>
                </ul>
                <p>Ustawienie w programie (IN range) musi bezwzględnie odpowiadać fizycznemu położeniu zworki na płytce Red Pitaya.</p>

                <h3>3. Czym jest wykres Base signal?</h3>
                <p><b>Base signal</b> (górny wykres, kolor niebieski) to <b>surowy sygnał napięciowy wprost z przetwornika</b>
                &ndash; bez żadnej filtracji. Na tym wykresie widać:</p>
                <ul>
                  <li>wolnozmienną sinusoidę 50&nbsp;Hz (tło napięcia sieciowego lub zakłócenia sieci),</li>
                  <li>szybkie szpilki wyładowań niezupełnych nałożone na tę sinusoidę,</li>
                  <li>szum termiczny i elektroniczny toru pomiarowego.</li>
                </ul>
                <p>Gdy w opcji <i>Visual plot</i> wybrano <b>IN1 + IN2</b>, w tle wykresu pojawia się szary przebieg
                z prawej osi Y &ndash; jest to fizyczny sygnał referencyjny 50&nbsp;Hz z wejścia IN2.</p>

                <h3>4. Czym jest wykres Filtered signal i do czego służą filtry?</h3>
                <p><b>Filtered signal</b> (dolny wykres, kolor zielony) to sygnał po przepuszczeniu przez
                <b>cyfrowy filtr górnoprzepustowy (HPF &ndash; High-Pass Filter)</b>. Rola filtra jest kluczowa:</p>
                <ul>
                  <li><b>odcina</b> wolną sinusoidę 50&nbsp;Hz oraz wolne dryfy napięcia stałego i zakłócenia sieciowe,</li>
                  <li><b>przepuszcza</b> wyłącznie wąskie, strome impulsy wyładowań niezupełnych (o czasach narastania w mikrosekundach).</li>
                </ul>
                <p>Parametry filtra (konfigurowane w panelu parametrów po lewej stronie):</p>
                <ul>
                  <li><b>HPF cutoff frequency [Hz]</b> &ndash; dolna granica pasma przepustowego. Typowo 50&nbsp;000&nbsp;Hz (50&nbsp;kHz)
                      lub 100&nbsp;000&nbsp;Hz. Wszystko poniżej tej częstotliwości jest wycinane.</li>
                  <li><b>Filter Q</b> &ndash; dobroć filtra (kształt kolana filtracji). Domyślna wartość <b>0,707</b> (filtr Butterwortha)
                      zapewnia maksymalnie płaską charakterystykę w paśmie i brak sztucznego oscylowania (dzwonienia) na impulsach.</li>
                  <li><b>Filter Order</b> &ndash; rząd filtra (np. 2, 4, 8). Wyższy rząd ostrzej tłumi częstotliwości poniżej granicy,
                      ale wymaga więcej obliczeń CPU.</li>
                </ul>

                <h3>5. Wyznaczanie kąta fazowego (0&ndash;360&deg;)</h3>
                <p>W analizie WNZ kluczowa jest informacja, w której fazie sinusoidy napięcia roboczego wystąpiło dane wyładowanie.
                Program wyznacza chwilę przejścia napięcia 50&nbsp;Hz przez zero w kierunku dodatnim (<b>Zero-crossing instant [s]</b>):</p>
                <ul>
                  <li>Gdy włączona jest opcja <b>HW Phase Ref</b> &ndash; faza estymowana jest z fizycznego sygnału na kanale CH2 (IN2),</li>
                  <li>Gdy opcja jest wyłączona &ndash; faza estymowana jest matematycznie z tła sygnału CH1 (IN1).</li>
                </ul>
                <p>Każdy wykryty impuls otrzymuje kąt fazowy &phi; wyliczony ze wzoru:</p>
                <blockquote><code>&phi; = 360 &times; f0 &times; (t &minus; t0) mod 360&deg;</code></blockquote>

                <h3>6. Kwalifikacja impulsów &ndash; kiedy sygnał jest uznawany za puls (Pulses)?</h3>
                <p>Detekcja impulsów odbywa się wyłącznie na <b>przefiltrowanym sygnale (Filtered signal)</b>.
                Algorytm przegląda próbki z zachowaniem następujących kryteriów:</p>
                <ol>
                  <li><b>Ignorowanie brzegów bufora (blanking):</b> pierwsze i ostatnie ~20&nbsp;&mu;s każdego bufora danych
                      są pomijane, aby filtr cyfrowy osiągnął stan ustalony i nie wygenerował fałszywej szpilki na krawędzi.</li>
                  <li><b>Warunek progu amplitudy:</b> próbka jest kandydatem na impuls, gdy jej moduł przekroczy próg
                      <b>Pulse ampl. threshold [mV]</b>: <code>|y| &ge; threshold</code>. Szum tła poniżej tego progu jest odrzucany.</li>
                  <li><b>Wyszukiwanie wierzchołka (Peak Picker):</b> po przekroczeniu progu program nie zatrzymuje się na pierwszej próbce,
                      lecz w oknie czasowym o długości <b>Dead time [µs]</b> wyszukuje punkt o <i>maksymalnej wartości bezwzględnej</i>.
                      To właśnie ten lokalny ekstremum jest rejestrowany jako właściwy impuls.</li>
                  <li><b>Czas martwy (Dead time):</b> po zarejestrowaniu impulsu kolejne próbki przez czas <b>Dead time [µs]</b>
                      są bezwzględnie ignorowane. Zapobiega to wielokrotnemu zliczaniu gasnących oscylacji (dzwonienia obwodu)
                      jako rzekomo oddzielnych wyładowań. Typowa wartość: 5&ndash;20&nbsp;&mu;s.</li>
                </ol>
                <p>Każdy zakwalifikowany impuls zapisywany jest jako trójka danych: czas <i>t</i> [s], kąt fazowy <i>&phi;</i> [0&ndash;360&deg;] oraz amplituda <i>amp</i> [mV] (dodatnia lub ujemna).</p>

                <h3>7. Czym jest wykres PRPD oraz licznik Pulses?</h3>
                <p><b>PRPD (Phase Resolved Partial Discharge)</b> to dwuwymiarowy rozkład fazowo-amplitudowy wyładowań:</p>
                <ul>
                  <li><b>Oś X:</b> kąt fazowy napięcia zasilającego (0&ndash;360&deg;) &ndash; jeden pełny okres 50&nbsp;Hz (20&nbsp;ms),</li>
                  <li><b>Oś Y:</b> amplituda impulsu w miliwoltach [mV],</li>
                  <li><b>Kolor punktu:</b> gęstość występowania wyładowań (mapa ciepła). Kolor niebieski oznacza pojedyncze zdarzenia,
                      a kolory ciepłe (żółty, czerwony) oznaczają obszary, w których wyładowania powtarzają się najczęściej.
                      Pozwala to natychmiast rozpoznać typ defektu (np. ulot, wyładowania wtrącone, ślizgowe).</li>
                </ul>
                <p><b>Jakie próbki i za jaki okres są brane pod uwagę?</b></p>
                <ul>
                  <li>Na wykres PRPD trafiają <b>wyłącznie zakwalifikowane impulsy</b> (spełniające kryteria z punktu 6).</li>
                  <li><b>Start once (Pojedynczy pomiar):</b> Każde kliknięcie <i>Start once</i> czyści poprzedni wykres oraz bufor oscylogramów i wykonuje nowy pomiar dla zadanego czasu trwania (<i>Duration</i>). Wykres PRPD prezentuje impulsy zebrane w tym jednym oknie pomiarowym.</li>
                  <li><b>Start sequence (Pomiar sekwencyjny):</b> Program zbiera wykres PRPD dla zadanego czasu <i>Duration</i>, zapisuje plik, a następnie automatycznie czyści wykres PRPD i rozpoczyna tworzenie kolejnego nowego wykresu dla następnego okna czasowego. Cykl powtarza się w pętli aż do kliknięcia <b>Stop RP</b>.</li>
                </ul>
                <p><b>Tryby prezentacji wykresu PRPD:</b></p>
                <ul>
                  <li><b>Heatmap (domyślny):</b> Dwuwymiarowa mapa ciepła gęstości prawdopodobieństwa &ndash; skupiska powtarzających się wyładowań tworzą świecące chmury WNZ w wybranej palecie barw (<i>PRPD color palette</i>).</li>
                  <li><b>Show Raw Data:</b> Wykres punktowy w stałym, jaskrawym kolorze surowym &ndash; 100% impulsów jest widoczne z pełnym kontrastem bez wygaszania gęstościowego.</li>
                  <li><b>Show Impulse Bars:</b> Wykres igłowy / słupkowy (<i>Stem Plot</i>), w którym każdy impuls jest rysowany jako pionowa linia od 0&nbsp;mV do wierzchołka amplitudy. Gdy <i>Show Raw Data</i> jest odznaczone, słupki przyjmują kolory Heatmapy na podstawie gęstości skupiska wierzchołków; w połączeniu z <i>Show Raw Data</i> słupki są jednolicie zielone. Ułatwia to obserwację dynamiki pojedynczych wystrzałów oraz ekstrakcję cech w sieciach neuronowych (CNN/YOLO).</li>
                </ul>

                <p><b>Licznik "Pulses":</b> wskazuje łączną liczbę zarejestrowanych impulsów o amplitudzie &ge; <i>Pulse ampl. threshold</i> w bieżącym oknie. Jeśli zmienisz próg po pomiarze, program natychmiast zaktualizuje liczbę zliczonych impulsów oraz widok wykresu.</p>

                <h3>8. Zapisywanie plików (.rppr.bin) i ich późniejsze odczytywanie</h3>
                <p>Każdy pomiar rejestrowany przez program jest zapisywany w postaci binarnego pliku archiwalnego z rozszerzeniem <b>.rppr.bin</b>.</p>

                <p><b>Kiedy i jak tworzone są pliki? Różnica między Start once a Start sequence:</b></p>
                <ul>
                  <li><b>Start once:</b> Program wykonuje pojedynczą akwizycję o zadanym czasie trwania (<i>Duration</i>). Po odebraniu wszystkich danych plik zostaje sfinalizowany i zapisany na dysku. Powstaje dokładnie <b>jeden plik</b> odpowiadający temu pomiarowi.</li>
                  <li><b>Start sequence:</b> Program rejestruje dane w sposób ciągły w powtarzających się oknach czasowych (o długości <i>Duration</i>). Dla każdego okna tworzony jest <b>osobny, nowy plik</b> z unikalnym znacznikiem czasu (z dokładnością do milisekund). Po zakończeniu okna plik jest zapisywany, a program natychmiast rozpoczyna zapis kolejnego pliku w następnym oknie, aż do naciśnięcia przycisku <b>Stop RP</b>.
                  <br><i>Wskazówka:</i> Parametr <b>File limit</b> w panelu <i>Output Configuration</i> pozwala ograniczyć maksymalną liczbę przechowywanych plików w sesji (starsze pliki są usuwane w kolejce FIFO, chroniąc dysk przed zapełnieniem).</li>
                </ul>

                <p><b>Gdzie pliki są zapisywane i jak są nazywane?</b></p>
                <ul>
                  <li>Pliki trafiają do katalogu wyjściowego (domyślnie <code>data/received_bin</code> lub ścieżki wybranej w <i>Output Configuration</i>).</li>
                  <li>W zależności od wybranego defektu pliki są automatycznie segregowane do podkatalogów tematycznych, np. <code>sessions/Corona_HV/</code>.</li>
                  <li>Nazwa pliku tworzona jest automatycznie z parametrów eksperymentu:<br>
                  <code>&lt;TypDefektu&gt;_&lt;Napięcie&gt;kV_&lt;Czujnik&gt;_&lt;Wariant&gt;_[Etykieta]_[Data_Godzina_Milisekundy].rppr.bin</code><br>
                  Przykładowo: <code>Corona_HV_12kV_HFCT_A_Lab_20261027_124530_123.rppr.bin</code>. Dzięki temu już sama nazwa i ścieżka pliku precyzyjnie opisują warunki fizyczne badania.</li>
                </ul>

                <p><b>Co jest zapisane wewnątrz pliku?</b></p>
                <ul>
                  <li><b>Nagłówek JSON:</b> Pełne metadane konfiguracji sprzętowej i eksperymentu &ndash; lista nagrywanych kanałów (IN1, IN2), ustawione zakresy wejściowe (gains: LV/HV), częstotliwość próbkowania <i>fs</i>, decymacja, parametry wyzwalania (trigger), typ defektu, napięcie probiercze [kV], typ czujnika i wariant obiektu.</li>
                  <li><b>Ramki danych binarnych:</b> Każda ramka posiada sumę kontrolną CRC32 (gwarantującą spójność danych) oraz 14-bitowe surowe próbki przetwornika ADC (int16). Jeśli wybrano 2 kanały (IN1 + IN2), plik zawiera jednocześnie pełny sygnał wyładowań (CH1) oraz fizyczną sinusoidę referencyjną 50 Hz (CH2).</li>
                </ul>

                <p><b>Co można zrobić po ponownym wczytaniu pliku do programu?</b></p>
                <ul>
                  <li>Plik można wczytać w dowolnym momencie z dysku (menu <i>File &rarr; Open data file...</i> lub z listy odebranych sygnałów).</li>
                  <li>Program odczytuje z nagłówka parametry próbkowania i zakresów LV/HV, odtwarzając rzeczywiste amplitudy w miliwoltach [mV].</li>
                  <li><b>Pełna analiza post-processing:</b> Na wczytanym sygnale można <b>dowolnie zmieniać parametry filtracji i detekcji</b> (częstotliwość odcięcia HPF, dobroć Q, rząd filtra, próg <i>Pulse ampl. threshold</i> oraz czas martwy <i>Dead time</i>). Wykres PRPD oraz oscylogramy są natychmiast przeliczane i przerysowywane na nowo z oryginalnych surowych próbek!</li>
                  <li>Możliwa jest automatyczna klasyfikacja defektu (przycisk <i>Classify</i>) oraz eksport wyekstrahowanych impulsów do pliku CSV (<i>File &rarr; Export raw pulses to CSV</i>).</li>
                </ul>

                <h3>9. Podsumowanie &ndash; cały łańcuch przetwarzania krok po kroku</h3>
                <ol>
                  <li><b>Akwizycja ADC:</b> Red Pitaya pobiera próbki sygnału z wejść IN1 i IN2 z częstotliwością <i>fs = 125 MS/s / Decimation</i>.</li>
                  <li><b>Transmisja danych:</b> Serwer strumieniuje ramki danych binarnie przez sieć TCP/IP do programu.</li>
                  <li><b>Skalowanie napięcia:</b> Program przelicza surowe bity int16 na miliwolty [mV] wg zakresu LV/HV.</li>
                  <li><b>Podgląd Base signal:</b> Sygnał trafia na górny wykres jako niefiltrowany przebieg wejściowy (z opcjonalną referencją IN2).</li>
                  <li><b>Filtracja cyfrowa HPF:</b> Filtr górnoprzepustowy usuwa wolną sinusoidę 50&nbsp;Hz i zakłócenia tła.</li>
                  <li><b>Podgląd Filtered signal:</b> Wykres dolny prezentuje wyczyszczone, szybkie piki WNZ.</li>
                  <li><b>Detekcja impulsów:</b> Algorytm lokalizuje wierzchołki powyżej progu z uwzględnieniem czasu martwego (Dead time).</li>
                  <li><b>Wyznaczenie fazy:</b> Każdemu impulsowi przypisywany jest kąt fazowy &phi; (0&ndash;360&deg;) względem przejścia przez zero napięcia 50&nbsp;Hz.</li>
                  <li><b>Wykres PRPD i licznik Pulses:</b> Wyznaczone impulsy nanoszone są na diagram fazowo-rozdzielczy, a licznik Pulses ulega zwiększeniu.</li>
                </ol>
                <br>
                </body>
                </html>
                """;

            JEditorPane editorPane = new JEditorPane("text/html", html);
            editorPane.setEditable(false);
            editorPane.setCaretPosition(0);
            editorPane.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            Color panelBg = dark ? new Color(43, 45, 48) : Color.WHITE;
            editorPane.setBackground(panelBg);

            JScrollPane scrollPane = new JScrollPane(editorPane);
            scrollPane.getViewport().setBackground(panelBg);
            scrollPane.setPreferredSize(new Dimension(760, 580));
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);

            JOptionPane.showMessageDialog(
                this,
                scrollPane,
                "About – jak działa program PRPD Tool",
                JOptionPane.PLAIN_MESSAGE
            );
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(this, "Błąd okna About: " + t.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
        } finally {
            aboutDialogShowing = false;
        }
    }

    private void buildProfilesMenu(JMenu profilesM) {
        profilesM.removeAll();
        
        JMenuItem saveProfileMI = new JMenuItem("Save current profile...");
        saveProfileMI.addActionListener(e -> {
            createNewProfile(profilesM);
        });
        profilesM.add(saveProfileMI);

        File dir = new File("profiles");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".cfg"));
            if (files != null && files.length > 0) {
                profilesM.addSeparator();
                for (File f : files) {
                    JMenu profM = new JMenu(f.getName().replace(".cfg", ""));
                    JMenuItem loadMI = new JMenuItem("Load");
                    loadMI.addActionListener(e -> loadProfile(f));
                    profM.add(loadMI);
                    
                    JMenuItem deleteMI = new JMenuItem("Delete");
                    deleteMI.addActionListener(e -> {
                        int r = JOptionPane.showConfirmDialog(this, "Delete profile " + f.getName() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                        if (r == JOptionPane.YES_OPTION) {
                            f.delete();
                            buildProfilesMenu(profilesM);
                        }
                    });
                    profM.add(deleteMI);
                    
                    profilesM.add(profM);
                }
            }
        }
    }


    private String editProfileMap(java.util.Map<String, String> profileData, String title, String actionButton, boolean isSaveMode, String defaultName) {
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(new String[]{"Parameter", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // Only values are editable
            }
        };
        for (java.util.Map.Entry<String, String> entry : profileData.entrySet()) {
            model.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
        javax.swing.JTable table = new javax.swing.JTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(table);
        scroll.setPreferredSize(new Dimension(400, 300));

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(new JLabel("Review and edit the profile parameters below:"), BorderLayout.NORTH);
        
        JTextField nameField = new JTextField(defaultName != null ? defaultName : "my_profile");
        if (isSaveMode) {
            JPanel namePanel = new JPanel(new BorderLayout(5, 5));
            namePanel.add(new JLabel("Profile Name: "), BorderLayout.WEST);
            namePanel.add(nameField, BorderLayout.CENTER);
            topPanel.add(namePanel, BorderLayout.SOUTH);
        }
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        Object[] options = {actionButton, "Cancel"};
        int result = JOptionPane.showOptionDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (result == JOptionPane.OK_OPTION) {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            profileData.clear();
            for (int i = 0; i < model.getRowCount(); i++) {
                profileData.put(model.getValueAt(i, 0).toString(), model.getValueAt(i, 1) != null ? model.getValueAt(i, 1).toString() : "");
            }
            return isSaveMode ? nameField.getText().trim() : (defaultName != null ? defaultName : "");
        }
        return null;
    }

    private void createNewProfile(JMenu profilesM) {
        try {
            java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
            data.put(RP_HOST, rpHostField.getText());
            data.put(RP_PORT, rpPortSpinner.getValue().toString());
            data.put(RP_CHANNELS, rpChannelsCombo.getSelectedItem().toString());
            data.put(RP_VISUAL_CHANNEL, rpVisualChannelCombo.getSelectedItem().toString());
            data.put(RP_GAIN1, rpGain1Combo.getSelectedItem().toString());
            data.put(RP_GAIN2, rpGain2Combo.getSelectedItem().toString());
            data.put(RP_DECIMATION, rpDecimationSpinner.getValue().toString());
            data.put(RP_AVERAGING, Boolean.toString(rpAveragingBox.isSelected()));
            data.put(RP_TRIGGER_SOURCE, rpTriggerCombo.getSelectedItem().toString());
            data.put(RP_TRIGGER_LEVEL, rpTriggerLevelSpinner.getValue().toString());
            data.put(RP_TRIGGER_DELAY, rpTriggerDelaySpinner.getValue().toString());
            data.put(RP_TRIGGER_TIMEOUT, rpTriggerTimeoutSpinner.getValue().toString());
            data.put(RP_MODE, rpModeCombo.getSelectedItem().toString());
            data.put(RP_DURATION, rpDurationSpinner.getValue().toString());
            data.put(RP_FRAME_SIZE, String.valueOf(getSelectedFrameSize()));
            data.put(RP_FRAME_COUNT, rpFrameCountSpinner.getValue().toString());

            for (Param<?> p : params) {
                data.put("Param." + p.name, p.getText());
            }

            String profileName = editProfileMap(data, "Save New Profile", "Save Profile", true, "my_profile");
            if (profileName == null || profileName.isEmpty()) {
                return; // User cancelled or entered empty name
            }

            File dir = new File("profiles");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, profileName + ".cfg");
            
            Configuration prof = new Configuration(file.getAbsolutePath());
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                prof.saveValue(entry.getKey(), entry.getValue());
            }
            status.setText("Profile saved to " + file.getName());
            buildProfilesMenu(profilesM);
        } catch (Exception ex) {
            ex.printStackTrace();
            status.setText("Failed to save profile: " + ex.getMessage());
        }
    }

    private void loadProfile(File file) {
        Configuration prof = new Configuration(file.getAbsolutePath());
        
        java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
        String[] keys = {RP_HOST, RP_PORT, RP_CHANNELS, RP_VISUAL_CHANNEL, RP_GAIN1, RP_GAIN2, 
                         RP_DECIMATION, RP_AVERAGING, RP_TRIGGER_SOURCE, RP_TRIGGER_LEVEL, 
                         RP_TRIGGER_DELAY, RP_TRIGGER_TIMEOUT, RP_MODE, RP_DURATION, 
                         RP_FRAME_SIZE, RP_FRAME_COUNT};
        for(String k : keys) {
            try { String v = prof.getValue(k); if(v != null) data.put(k, v); } catch(Exception e) {}
        }
        for (Param<?> p : params) {
            try { String v = prof.getValue("Param." + p.name); if (v != null) data.put("Param." + p.name, v); } catch(Exception e) {}
        }
        
        if (editProfileMap(data, "Load Profile: " + file.getName(), "Load into Application", false, file.getName()) == null) {
            return;
        }
        
        try { String v = data.get(RP_HOST); if(v != null) rpHostField.setText(v); } catch(Exception e) {}
        try { String v = data.get(RP_PORT); if(v != null) rpPortSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try {
            String v = data.get(RP_CHANNELS);
            if (v != null) {
                if ("IN1+IN2".equals(v)) v = "IN1 + IN2";
                rpChannelsCombo.setSelectedItem(v);
            }
        } catch(Exception e) {}
        try {
            String v = data.get(RP_VISUAL_CHANNEL);
            if (v != null) {
                if ("IN1+IN2".equals(v)) v = "IN1 + IN2";
                rpVisualChannelCombo.setSelectedItem(v);
            }
        } catch(Exception e) {}
        try { String v = data.get(RP_GAIN1); if(v != null) rpGain1Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_GAIN2); if(v != null) rpGain2Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_DECIMATION); if(v != null) rpDecimationSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_AVERAGING); if(v != null) rpAveragingBox.setSelected(Boolean.parseBoolean(v)); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_SOURCE); if(v != null) rpTriggerCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_LEVEL); if(v != null) rpTriggerLevelSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_DELAY); if(v != null) rpTriggerDelaySpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_TRIGGER_TIMEOUT); if(v != null) rpTriggerTimeoutSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = data.get(RP_MODE); if(v != null) rpModeCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = data.get(RP_DURATION); if(v != null) rpDurationSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = data.get(RP_FRAME_SIZE); if(v != null) setSelectedFrameSize(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = data.get(RP_FRAME_COUNT); if(v != null) rpFrameCountSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}

        for (Param<?> p : params) {
            String v = data.get("Param." + p.name);
            if (v != null) {
                p.setFromText(v);
                if (p.field != null) {
                    p.field.setText(p.getText());
                }
            }
        }
        
        onParameterChanged();
    }

    private void initGui() {

        left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        right = new JPanel();
        bottom = new JPanel();
        center = new ImagePanel(new BufferedImage(640, 480, BufferedImage.TYPE_BYTE_GRAY));

        // --- lewy + reszta ---
        JScrollPane leftScroll = new JScrollPane(left);
        leftScroll.setBorder(BorderFactory.createEmptyBorder());
        leftScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        splitLeft = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftScroll,
                center
        );
        splitLeft.setResizeWeight(0.22);

        // --- góra (80%) + dół (20%) ---
        verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                splitLeft,
                bottom
        );
        verticalSplit.setResizeWeight(0.80);

        // Brak widocznych dzielników
        splitLeft.setDividerSize(0);
        verticalSplit.setDividerSize(0);

        setContentPane(verticalSplit);

        // ustawienie początkowych proporcji
        SwingUtilities.invokeLater(() -> {
            verticalSplit.setDividerLocation(0.75);
            splitLeft.setDividerLocation(0.22);

            JPanel rpPanel = initRedPitayaControls();

            histogram = new DynamicPRPDHistogram(
                    center.getWidth(), center.getHeight(),
                    360, 224,
                    (bipolarHistogram ? -ampMax : 0), ampMax,
                    bipolarHistogram
            );
            histogram.drawF0(drawF0);
            ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
            ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
            if (showRawDataCb != null) {
                ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
            }
            if (showImpulseBarsCb != null) {
                ((DynamicPRPDHistogram) histogram).setShowImpulseBars(showImpulseBarsCb.isSelected());
            }
            ((DynamicPRPDHistogram) histogram).setPalette(currentPalette);
            center.setHistogram((DynamicPRPDHistogram) histogram);
            if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
            center.revalidate();

            interactiveSignalPanel = new InteractiveSignalPanel();
            center.setSaveAllAction(() -> saveAllGraphsToPng());
            interactiveSignalPanel.reset(
                    topPlotMode == 2 ? "Base signal" : (topPlotMode == 1 ? "Baseline signal" : "Signal envelope"),
                    null,
                    "Filtered signal",
                    null,
                    false
            );
            JPanel recSignalsPanel = createReceivedSignalsPanel();
            bottom.setLayout(new BorderLayout());
            bottom.add(interactiveSignalPanel, BorderLayout.CENTER);

            paramPanel = new JPanel();
            paramPanel.setLayout(new GridLayout(0, 2, 3, 3));
            paramPanel.add(new JLabel("Data source: "));
            dataSource = new JLabel("none") {
                @Override
                public void setText(String text) {
                    super.setText(text);
                    setToolTipText(text);
                }
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    d.width = Math.min(d.width, 200);
                    return d;
                }
            };
            paramPanel.add(dataSource);

            paramPanel.add(new JLabel("Status:"));
            paramPanel.add(status);

            for (Param p : params) {
                JPanel labelPanel = new JPanel(new BorderLayout(4, 0));
                labelPanel.setOpaque(false);
                JLabel label = new JLabel(p.name);
                String tooltipText = paramHelpText(p.name);
                label.setToolTipText(htmlTooltip(tooltipText));
                
                JLabel help = new JLabel(new HelpIcon());
                help.setToolTipText(htmlTooltip(tooltipText));
                help.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                help.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                final String paramName = p.name;
                final String paramDesc = tooltipText;
                help.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        showHelpDialog(paramName, paramDesc);
                    }
                });
                
                labelPanel.add(label, BorderLayout.CENTER);
                labelPanel.add(help, BorderLayout.EAST);

                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.setToolTipText(htmlTooltip(tooltipText));
                field.addActionListener(e -> {
                    try {
                        p.setFromText(field.getText());
                        field.setBackground(UIManager.getColor("TextField.background"));
                        paramChange.setText("Param(s) change!");
                        applyButton.setBackground(Color.red);
                    } catch (NumberFormatException ex) {
                        field.setBackground(new Color(255, 200, 200));
                    }
                });

                field.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        if (!field.getText().equals(p.getText())) {
                            paramChange.setText("Param(s) change!");
                            applyButton.setBackground(Color.red);
                        }
                    }
                });
                
                Component toAdd = field;
                if ("Histogram max [V]".equals(p.name)) {
                    JPanel fp = new JPanel(new BorderLayout(4, 0));
                    fp.setOpaque(false);
                    fp.add(field, BorderLayout.CENTER);
                    JButton fitBtn = new JButton("Fit");
                    fitBtn.setToolTipText("Fit histogram vertical resolution to current data bounds");
                    fitBtn.setMargin(new Insets(1, 4, 1, 4));
                    fitBtn.addActionListener(ev -> {
                        if (histogram != null) {
                            setParamField("Histogram min [V]", "" + roundme(histogram.getDataMin(), 3));
                            setParamField("Histogram max [V]", "" + roundme(histogram.getDataMax(), 3));
                            onParameterChanged();
                        }
                    });
                    fp.add(fitBtn, BorderLayout.EAST);
                    toAdd = fp;
                }
                
                paramPanel.add(labelPanel);
                paramPanel.add(toAdd);
            }
            paramChange = new JLabel(" ");
            applyButton = new JButton("APPLY");
            applyButton.addActionListener(e -> onParameterChanged());
            paramPanel.add(paramChange);
            paramPanel.add(applyButton);

            autoscaleCb = new JCheckBox("Autoscale PRPD", true);
            autoscaleCb.addActionListener(e -> {
                if (autoscaleCb.isSelected()) {
                    applyAutoscale();
                }
            });
            JPanel autoPanel = new JPanel(new BorderLayout(4, 0));
            autoPanel.setOpaque(false);
            autoPanel.add(autoscaleCb, BorderLayout.CENTER);
            JLabel autoHelp = new JLabel(new HelpIcon());
            autoHelp.setToolTipText(htmlTooltip("Visual Zoom Adjustment.\n\nAutomatically adjusts the visual zoom of the PRPD plot to perfectly frame the visible pulses. Unlike \"Histogram min/max\" which rebuilds the actual image resolution, Autoscale only moves the camera.\n\nIt turns off automatically if you pan or zoom manually with the mouse."));
            autoHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            autoHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            autoPanel.add(autoHelp, BorderLayout.EAST);
            paramPanel.add(autoPanel);
            
            showRawDataCb = new JCheckBox("Show Raw Data", false);
            showRawDataCb.addActionListener(e -> {
                if (histogram instanceof DynamicPRPDHistogram) {
                    ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
                    center.setImage(histogram.getImage());
                    center.repaint();
                }
            });
            JPanel rawPanel = new JPanel(new BorderLayout(4, 0));
            rawPanel.setOpaque(false);
            rawPanel.add(showRawDataCb, BorderLayout.CENTER);
            JLabel rawHelp = new JLabel(new HelpIcon());
            String rawHelpMsg = "Przełącznik trybu renderowania PRPD:\n\n"
                    + "• Odznaczony (domyślny - Heatmap):\n"
                    + "  Wykres 2D gęstości prawdopodobieństwa wyładowań. Kolor każdego piksela zależy od liczby nakładających się "
                    + "  impulsów (rzadkie/pojedyncze impulsy są ciemnogranatowe, a powtarzalne chmury WNZ świecą na żółto/czerwono/biel).\n\n"
                    + "• Zaznaczony (Raw Data Scatter Plot):\n"
                    + "  Klasyczny wykres punktowy w stałym, jaskrawym kolorze. Każdy pojedynczy impuls (nawet unikalna szpilka o zliczeniu 1) "
                    + "  jest rysowany jako wyraźny punkt o średnicy 5 px z białym rdzeniem, całkowicie bez wygaszania gęstościowego. "
                    + "  Pozwala to natychmiast zobaczyć 100% wszystkich impulsów, w tym rzadkie anomalie na szczytach.\n\n"
                    + "• W połączeniu z 'Show Impulse Bars':\n"
                    + "  Wymusza jednolicie zielony kolor wszystkich pionowych słupków (odpowiadający kolorowi sygnału przefiltrowanego).";
            rawHelp.setToolTipText(htmlTooltip(rawHelpMsg));
            rawHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            rawHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            rawHelp.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showHelpDialog("Show Raw Data", rawHelpMsg);
                }
            });
            rawPanel.add(rawHelp, BorderLayout.EAST);
            paramPanel.add(rawPanel);

            useHwPhaseRefCb = new JCheckBox("Use HW Phase Ref (CH2)", false);
            useHwPhaseRefCb.addActionListener(e -> {
                if (useHwPhaseRefCb.isSelected()) {
                    java.util.List<String> problems = new java.util.ArrayList<>();
                    String channels = rpChannelsCombo != null ? (String) rpChannelsCombo.getSelectedItem() : "IN1";
                    String visual = rpVisualChannelCombo != null ? (String) rpVisualChannelCombo.getSelectedItem() : "IN1";
                    if (!"IN1 + IN2".equals(channels) && !"IN1+IN2".equals(channels)) {
                        problems.add("  Channels   zmień na \"IN1 + IN2\" (aktualnie: \"" + channels + "\")");
                    }
                    if ("IN2".equals(visual)) {
                        problems.add("  Visual plot   zmień na \"IN1\" lub \"IN1 + IN2\" (IN2 jest zarezerwowany jako referencja 50 Hz)");
                    }
                    if (!problems.isEmpty()) {
                        useHwPhaseRefCb.setSelected(false);
                        JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "<html><b>Nie można użyć referencji CH2.</b><br><br>"
                            + "Aby korzystać z \"Use HW Phase Ref (CH2)\", zmień<br>"
                            + "następujące ustawienia w zakładce <i>Red Pitaya Settings</i>:<br><br>"
                            + String.join("<br>", problems)
                            + "</html>",
                            "Błąd konfiguracji",
                            JOptionPane.WARNING_MESSAGE
                        );
                    }
                }
            });
            JPanel hwPanel = new JPanel(new BorderLayout(4, 0));
            hwPanel.setOpaque(false);
            hwPanel.add(useHwPhaseRefCb, BorderLayout.CENTER);
            JLabel hwHelp = new JLabel(new HelpIcon());
            hwHelp.setToolTipText(htmlTooltip("Hardware Phase Synchronization.\n\nForces the software to use the physical signal on IN2 for 50 Hz phase synchronization.\nIf unchecked, the software attempts to mathematically extract the reference from the main IN1 signal.\n\nNote: Requires \"Channels\" to be set to IN1 + IN2 and \"Visual plot\" to IN1 or IN1 + IN2 in the Red Pitaya Settings."));
            hwHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            hwHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            hwPanel.add(hwHelp, BorderLayout.EAST);
            paramPanel.add(hwPanel);

            showImpulseBarsCb = new JCheckBox("Show Impulse Bars", false);
            showImpulseBarsCb.addActionListener(e -> {
                if (histogram instanceof DynamicPRPDHistogram) {
                    ((DynamicPRPDHistogram) histogram).setShowImpulseBars(showImpulseBarsCb.isSelected());
                    center.setImage(histogram.getImage());
                    center.repaint();
                }
            });
            JPanel barsPanel = new JPanel(new BorderLayout(4, 0));
            barsPanel.setOpaque(false);
            barsPanel.add(showImpulseBarsCb, BorderLayout.CENTER);
            JLabel barsHelp = new JLabel(new HelpIcon());
            String barsHelpMsg = "Wykres igłowy / słupkowy impulsów (Stem Plot):\n\n"
                    + "• Zamiast pojedynczych kropek, każdy zarejestrowany impuls jest rysowany jako pionowa kreska "
                    + "  biegnąca od osi zerowej (0 mV) aż do wierzchołka amplitudy danego wyładowania, zwieńczona punktem szczytowym.\n\n"
                    + "• Kolorystyka i tryby pracy:\n"
                    + "  - Samo Show Impulse Bars (Show Raw Data odznaczone):\n"
                    + "    Słupki są barwione zgodnie z wybraną paletą Heatmapy (PRPD color palette) na podstawie gęstości skupiska wierzchołków. "
                    + "    Rzadkie szpilki mają barwy chłodne (np. błękit/granat), natomiast powtarzalne chmury WNZ świecą w gorących barwach (żółć/czerwień/biel).\n"
                    + "  - Show Impulse Bars + zaznaczone Show Raw Data:\n"
                    + "    Wszystkie słupki są rysowane w jednolitym, jaskrawym kolorze surowym (zielony Filtered signal) bez wygaszania gęstościowego.\n\n"
                    + "• Zastosowanie i zalety:\n"
                    + "  - Wizualizacja wystrzałów: doskonale obrazuje energię i 'wysokość' każdego impulsu względem fazy sinusoidy.\n"
                    + "  - Uczenie maszynowe i sieci neuronowe: w sieciach splotowych (np. CNN / YOLO) pojedyncze piksele (kropki) "
                    + "    mogą zanikać przy operacjach zmniejszania rozdzielczości (downsampling / pooling). Ciągłe pionowe kreski "
                    + "    tworzą wyraźne krawędzie, które filtry splotowe wykrywają ze znacznie wyższą stabilnością.\n\n"
                    + "• Wskazówka:\n"
                    + "  W przypadku gęstych chmur wyładowań zaleca się zwiększyć próg 'Pulse ampl. threshold', aby odciąć drobny szum przy zerze "
                    + "  i wyeksponować tylko wiodące wystrzały.";
            barsHelp.setToolTipText(htmlTooltip(barsHelpMsg));
            barsHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            barsHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            barsHelp.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    showHelpDialog("Show Impulse Bars", barsHelpMsg);
                }
            });
            barsPanel.add(barsHelp, BorderLayout.EAST);
            paramPanel.add(barsPanel);

            center.setResetAction(() -> {
                applyAutoscale();
            });

            center.addMouseWheelListener(e -> autoscaleCb.setSelected(false));
            center.addMouseMotionListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    autoscaleCb.setSelected(false);
                }
            });

            paramPanel.setBorder(BorderFactory.createTitledBorder("Filters & Parameters"));
            setFontRecursively(paramPanel, currentFont, 0);

            JPanel classifyPanel = new JPanel(new BorderLayout());
            classifyPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            modelPanel = new JPanel();
            cResults = new HashMap<>();
            updateModelPanel();

            classifyPanel.add(modelPanel, BorderLayout.CENTER);

            classifyButton = new JButton("CLASSIFY NOW");
            classifyButton.setFont(classifyButton.getFont().deriveFont(Font.BOLD));
            classifyButton.addActionListener(e -> classifyPRPD(cResults));
            classifyButton.setEnabled(false);
            classifyPanel.add(classifyButton, BorderLayout.SOUTH);

            setFontRecursively(classifyPanel, currentFont, 0);
            
            classifyDialog = new JDialog(PRPDTool.this, "AI Classification", false);
            classifyDialog.add(classifyPanel);
            classifyDialog.setSize(500, 400);
            classifyDialog.setLocationRelativeTo(PRPDTool.this);

            left.add(paramPanel);
            left.add(rpPanel);
            left.add(recSignalsPanel);
            left.add(Box.createVerticalGlue());
            
            setCurrentFont();
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                verticalSplit.setDividerLocation(0.75);
                splitLeft.setDividerLocation(0.22);
                histogram.resize(center.getWidth(), center.getHeight());
                if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
                try {
                    configuration.saveValue(FRAMESIZE, getWidth() + "x" + getHeight());
                } catch (IOException ex) {
                }
            }
        });
    }

    private void updateModelPanel() {
        if (modelPanel == null) return;
        modelPanel.removeAll();
        modelPanel.setLayout(new GridLayout(classifiers.size() + 1, 2, 3, 3));
        cResults.clear();
        JLabel c1 = new JLabel("Classifier");
        c1.setBorder(BorderFactory.createLineBorder(Color.black));
        JLabel c2 = new JLabel("Result (confidence)");
        c2.setBorder(BorderFactory.createLineBorder(Color.black));
        modelPanel.add(c1);
        modelPanel.add(c2);
        for (Classifier c : classifiers) {
            if (c != null && c.ok()) {
                JLabel name = new JLabel(c.name());
                name.setBackground(Color.gray);
                JLabel result = new JLabel("           ");
                result.setBackground(Color.gray);
                modelPanel.add(name);
                modelPanel.add(result);
                cResults.put(c, result);
            }
        }
        setFontRecursively(modelPanel, currentFont, 0);
        modelPanel.revalidate();
        modelPanel.repaint();
    }

    private JPanel createReceivedSignalsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Received signals"));
        
        receivedSignalsModel = new DefaultListModel<>();
        receivedSignalsList = new JList<>(receivedSignalsModel);
        receivedSignalsList.setVisibleRowCount(6);
        receivedSignalsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        receivedSignalsList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value.getName());
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            label.setToolTipText(value.getAbsolutePath());
            return label;
        });
        receivedSignalsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    loadSelectedReceivedSignal();
                }
            }
        });

        JPanel buttons = new JPanel(new GridLayout(2, 2, 3, 3));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshReceivedSignals());
        JButton load = new JButton("Load");
        load.addActionListener(e -> loadSelectedReceivedSignal());
        JButton rename = new JButton("Rename");
        rename.addActionListener(e -> renameSelectedReceivedSignal());
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelectedReceivedSignal());
        buttons.add(refresh);
        buttons.add(load);
        buttons.add(rename);
        buttons.add(delete);

        panel.add(new JScrollPane(receivedSignalsList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        refreshReceivedSignals();
        return panel;
    }

    private void cleanupOldSignals() {
        if (rpFileLimit <= 0) return;
        // Czyść bieżący podfolder defektu (lub katalog główny jeśli Custom)
        String currentDefect = rpDefectTypeCombo != null
            ? (String) rpDefectTypeCombo.getSelectedItem() : "unknown";
        if (currentDefect == null || currentDefect.isBlank()) currentDefect = "unknown";
        
        File sessionDir;
        if ("<None / Custom>".equals(currentDefect)) {
            sessionDir = receivedSignalsDir;
        } else {
            sessionDir = new File(receivedSignalsDir, "sessions" + File.separator + redpitaya.RedPitayaFileWriter.sanitizeForFilename(currentDefect));
        }
        if (!sessionDir.exists()) return;

        File[] files = sessionDir.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".rppr.bin"));
        if (files == null || files.length <= rpFileLimit) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = rpFileLimit; i < files.length; i++) {
            files[i].delete();
        }
    }

    private void refreshReceivedSignals() {
        if (receivedSignalsModel == null) {
            return;
        }
        receivedSignalsModel.clear();

        List<File> found = new ArrayList<>();

        // Nowa struktura: data/received_bin/sessions/{defect}/...
        File sessionsRoot = new File(receivedSignalsDir, "sessions");
        if (sessionsRoot.exists() && sessionsRoot.isDirectory()) {
            File[] defectDirs = sessionsRoot.listFiles(File::isDirectory);
            if (defectDirs != null) {
                for (File defectDir : defectDirs) {
                    File[] rpprFiles = defectDir.listFiles((dir, name) -> {
                        String lower = name.toLowerCase(Locale.US);
                        return lower.endsWith(".rppr.bin");
                    });
                    if (rpprFiles != null) {
                        for (File f : rpprFiles) if (f.isFile()) found.add(f);
                    }
                }
            }
        }

        // Stara struktura (kompatybilność wsteczna): data/received_bin/*.bin
        if (!receivedSignalsDir.exists()) receivedSignalsDir.mkdirs();
        File[] legacy = receivedSignalsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.US);
            return lower.endsWith(".rppr.bin")
                    || lower.endsWith(".prpdtool.bin")
                    || (lower.endsWith(".bin") && !lower.startsWith("."));
        });
        if (legacy != null) {
            for (File f : legacy) if (f.isFile()) found.add(f);
        }

        found.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : found) {
            receivedSignalsModel.addElement(file);
        }
    }

    private File selectedReceivedSignal() {
        if (receivedSignalsList == null) {
            return null;
        }
        return receivedSignalsList.getSelectedValue();
    }

    private void loadSelectedReceivedSignal() {
        File file = selectedReceivedSignal();
        if (file == null) {
            status.setText("No received signal selected.");
            return;
        }
        inBatchMode = false;
        realTimeData = false;
        try {
            String filename = file.getAbsolutePath();
            getData(filename);
            saveLastUsedDirectory(file.getParentFile().getAbsolutePath());
            dataSource.setText("received (" + file.getName() + ")");
            setLastDataFile(filename);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Load received signal", JOptionPane.ERROR_MESSAGE);
            status.setText(ex.getMessage());
            setLastDataFile(null);
        }
    }

    private void renameSelectedReceivedSignal() {
        File file = selectedReceivedSignal();
        if (file == null) {
            status.setText("No received signal selected.");
            return;
        }
        String newName = JOptionPane.showInputDialog(this, "New file name:", file.getName());
        if (newName == null || newName.isBlank()) {
            return;
        }
        newName = newName.trim();
        if (!newName.toLowerCase(Locale.US).endsWith(".bin")) {
            String lowerOld = file.getName().toLowerCase(Locale.US);
            if (lowerOld.endsWith(".rppr.bin")) {
                newName += ".rppr.bin";
            } else if (lowerOld.endsWith(".prpdtool.bin")) {
                newName += ".prpdtool.bin";
            } else {
                newName += ".bin";
            }
        }
        File target = new File(file.getParentFile(), newName);
        if (target.exists()) {
            JOptionPane.showMessageDialog(this, "Target file already exists.", "Rename received signal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Files.move(file.toPath(), target.toPath());
            status.setText("Renamed to " + target.getName());
            refreshReceivedSignals();
            receivedSignalsList.setSelectedValue(target, true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Rename received signal", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedReceivedSignal() {
        File file = selectedReceivedSignal();
        if (file == null) {
            status.setText("No received signal selected.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Delete this file permanently?\n" + file.getName(),
                "Delete received signal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.delete(file.toPath());
            status.setText("Deleted " + file.getName());
            refreshReceivedSignals();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Delete received signal", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel initRedPitayaControls() {
        JPanel rpPanel = new JPanel(new BorderLayout(4, 4)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        rpPanel.setBorder(BorderFactory.createTitledBorder("Red Pitaya"));
        JPanel formPanel = new JPanel(new GridBagLayout());

        rpHostField = new JTextField(configValue(RP_HOST, "rp-f0f84e.local"), 12);
        rpPortSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_PORT, 9999), 1, 65535, 1));
        rpChannelsCombo = new JComboBox<>(new String[]{"IN1", "IN2", "IN1 + IN2"});
        String savedChannels = configValue(RP_CHANNELS, "IN1 + IN2");
        if ("IN1+IN2".equals(savedChannels)) {
            savedChannels = "IN1 + IN2";
        }
        rpChannelsCombo.setSelectedItem(savedChannels);
        rpVisualChannelCombo = new JComboBox<>(new String[]{"IN1", "IN2", "IN1 + IN2"});
        String savedVisual = configValue(RP_VISUAL_CHANNEL, "IN1 + IN2");
        if ("IN1+IN2".equals(savedVisual)) {
            savedVisual = "IN1 + IN2";
        }
        rpVisualChannelCombo.setSelectedItem(savedVisual);
        rpGain1Combo = new JComboBox<>(new String[]{"LV", "HV"});
        rpGain1Combo.setSelectedItem(configValue(RP_GAIN1, "LV"));
        rpGain2Combo = new JComboBox<>(new String[]{"LV", "HV"});
        rpGain2Combo.setSelectedItem(configValue(RP_GAIN2, "LV"));
        rpDecimationSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_DECIMATION, 64), 1, 65536, 1) {
            @Override
            public Object getNextValue() {
                int val = (Integer) super.getValue();
                if (val >= 65536) return null;
                return val * 2;
            }

            @Override
            public Object getPreviousValue() {
                int val = (Integer) super.getValue();
                if (val <= 1) return null;
                return val / 2;
            }
        });
        rpAveragingBox = new JCheckBox("enabled", configBoolean(RP_AVERAGING, false));
        rpTriggerCombo = new JComboBox<>(new String[]{"NOW", "CH1_PE", "CH1_NE", "CH2_PE", "CH2_NE", "EXT_PE", "EXT_NE"});
        rpTriggerCombo.setSelectedItem(configValue(RP_TRIGGER_SOURCE, "NOW"));
        rpTriggerLevelSpinner = new JSpinner(new SpinnerNumberModel(configDouble(RP_TRIGGER_LEVEL, 0.0), -20.0, 20.0, 0.000001));
        rpTriggerDelaySpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_TRIGGER_DELAY, 0), -100_000_000, 100_000_000, 1));
        rpTriggerTimeoutSpinner = new JSpinner(new SpinnerNumberModel(configDouble(RP_TRIGGER_TIMEOUT, 10.0), 0.1, 600.0, 0.001));
        rpModeCombo = new JComboBox<>(new String[]{"duration", "frames"});
        rpModeCombo.setSelectedItem(configValue(RP_MODE, "duration"));
        rpDurationSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100000.0, configDouble(RP_DURATION, 0.01)), 0.000001, 100000.0, 0.000001));
        rpFrameSizeCombo = new JComboBox<>(FRAME_SIZE_OPTIONS);
        DefaultListCellRenderer frameSizeRenderer = new DefaultListCellRenderer();
        frameSizeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        rpFrameSizeCombo.setRenderer(frameSizeRenderer);
        setSelectedFrameSize(configInt(RP_FRAME_SIZE, BufferFactory.bufferSize()));
        rpFrameCountSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_FRAME_COUNT, 1), 1, Integer.MAX_VALUE, 1));
        rpFrameTimeField = new JTextField();
        rpFrameTimeField.setEditable(false);
        rpFrameTimeField.setEnabled(false);
        rpFrameTimeField.setHorizontalAlignment(JTextField.RIGHT);
        rpFrameTimeField.setColumns(12);

        rpEstimatedSizeLabel = new JLabel(" ");
        rpSettingsEstimatedSizeLabel = new JLabel(" ");

        setIntegerSpinnerEditor(rpPortSpinner);
        setIntegerSpinnerEditor(rpDecimationSpinner);
        setDecimalSpinnerEditor(rpTriggerLevelSpinner);
        setIntegerSpinnerEditor(rpTriggerDelaySpinner);
        setDecimalSpinnerEditor(rpTriggerTimeoutSpinner);
        setDecimalSpinnerEditor(rpDurationSpinner);
        setIntegerSpinnerEditor(rpFrameCountSpinner);

        addFormRow(formPanel, "Host", rpHostField, helpText("host"));
        addFormRow(formPanel, "Port", rpPortSpinner, helpText("port"));
        addFormRow(formPanel, "Channels", rpChannelsCombo, helpText("channels"));
        addFormRow(formPanel, "Visual plot", rpVisualChannelCombo, helpText("visual"));
        addFormRow(formPanel, "IN1 range", rpGain1Combo, helpText("gain1"));
        addFormRow(formPanel, "IN2 range", rpGain2Combo, helpText("gain2"));
        addFormRow(formPanel, "Decimation (fs)", rpDecimationSpinner, helpText("decimation"));
        addFormRow(formPanel, "Averaging", rpAveragingBox, helpText("averaging"));
        addFormRow(formPanel, "Trigger mode", rpTriggerCombo, helpText("trigger"));
        addFormRow(formPanel, "Trigger [V]", rpTriggerLevelSpinner, helpText("triggerLevel"));
        addFormRow(formPanel, "Delay [samples]", rpTriggerDelaySpinner, helpText("triggerDelay"));
        addFormRow(formPanel, "Timeout [s]", rpTriggerTimeoutSpinner, helpText("triggerTimeout"));
        addFormRow(formPanel, "Mode", rpModeCombo, helpText("mode"));
        addFormRow(formPanel, "Duration [s]", rpDurationSpinner, helpText("duration"));
        addFormRow(formPanel, "Frame size", rpFrameSizeCombo, helpText("frameSize"));
        addFormRow(formPanel, "Frame count", rpFrameCountSpinner, helpText("frameCount"));
        addFormRow(formPanel, "Frame time", rpFrameTimeField, helpText("frameTime"));

        int nextRow = formPanel.getComponentCount() / 3;
        GridBagConstraints gbcEst = new GridBagConstraints();
        gbcEst.gridx = 0;
        gbcEst.gridy = nextRow;
        gbcEst.gridwidth = 3;
        gbcEst.fill = GridBagConstraints.HORIZONTAL;
        gbcEst.anchor = GridBagConstraints.WEST;
        gbcEst.insets = new Insets(4, 2, 2, 2);
        formPanel.add(rpSettingsEstimatedSizeLabel, gbcEst);

        GridBagConstraints gbcGlue = new GridBagConstraints();
        gbcGlue.gridx = 0;
        gbcGlue.gridy = nextRow + 1;
        gbcGlue.gridwidth = 3;
        gbcGlue.weighty = 1.0;
        gbcGlue.fill = GridBagConstraints.VERTICAL;
        formPanel.add(Box.createVerticalGlue(), gbcGlue);

        updateRedPitayaEstimateFont();

        // (usunięte zduplikowane pola metadanych z okna ustawień technicznych)

        rpTriggerIn1Button = new JButton("Auto trigger IN1");
        rpTriggerIn1Button.setToolTipText(htmlTooltip(helpText("autoTriggerIn1")));
        rpTriggerIn1Button.addActionListener(e -> openTriggerDialog(1));
        rpTriggerIn2Button = new JButton("Auto trigger IN2");
        rpTriggerIn2Button.setToolTipText(htmlTooltip(helpText("autoTriggerIn2")));
        rpTriggerIn2Button.addActionListener(e -> openTriggerDialog(2));
        rpStartOnceButton = new JButton("Start once");
        rpStartOnceButton.setToolTipText(htmlTooltip(helpText("startOnce")));
        rpStartOnceButton.addActionListener(e -> startRedPitaya(false));
        rpStartLiveButton = new JButton("Start sequence");
        rpStartLiveButton.setToolTipText(htmlTooltip(helpText("startLive")));
        rpStartLiveButton.addActionListener(e -> startRedPitaya(true));
        
        rpStopButton = new JButton("Stop RP");
        rpStopButton.setToolTipText(htmlTooltip(helpText("stopRp")));
        rpStopButton.addActionListener(e -> stopPipeline());
        rpStopButton.setEnabled(false);

        JPanel actions = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 2, 2, 2);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        actions.add(rpTriggerIn1Button, gbc);
        gbc.gridx = 1;
        actions.add(rpTriggerIn2Button, gbc);

        mainTriggerModeValue = new JLabel(rpTriggerCombo.getSelectedItem().toString());
        mainTriggerModeValue.setFont(mainTriggerModeValue.getFont().deriveFont(java.awt.Font.BOLD));
        mainTriggerLevelValue = new JLabel(String.format(java.util.Locale.US, "%.6f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue()));
        mainTriggerLevelValue.setFont(mainTriggerLevelValue.getFont().deriveFont(java.awt.Font.BOLD));
        
        rpTriggerCombo.addActionListener(e -> mainTriggerModeValue.setText(rpTriggerCombo.getSelectedItem().toString()));
        rpTriggerLevelSpinner.addChangeListener(e -> mainTriggerLevelValue.setText(String.format(java.util.Locale.US, "%.6f", ((Number)rpTriggerLevelSpinner.getValue()).doubleValue())));
        
        if (rpTriggerLevelSpinner.getEditor() instanceof javax.swing.JSpinner.DefaultEditor) {
            javax.swing.JSpinner.DefaultEditor editor = (javax.swing.JSpinner.DefaultEditor) rpTriggerLevelSpinner.getEditor();
            editor.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void update() {
                    try {
                        double v = Double.parseDouble(editor.getTextField().getText().replace(',', '.'));
                        mainTriggerLevelValue.setText(String.format(java.util.Locale.US, "%.6f", v));
                    } catch (Exception ex) {}
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            });
        }

        JPanel triggerDisplayPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        
                String modeTooltipStr = helpText("trigger") + "\n\nThis parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";
        String levelTooltipStr = helpText("triggerLevel") + "\n\nThis parameter can be calibrated automatically using the Auto trigger assistant above, or set manually in the Red Pitaya Settings window.";
        
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        modePanel.add(new JLabel("Trigger mode:"));
        modePanel.add(mainTriggerModeValue);
        JLabel modeHelp = new JLabel(new HelpIcon());
        modeHelp.setToolTipText(htmlTooltip(modeTooltipStr));
        modeHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        modePanel.add(modeHelp);
        
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        levelPanel.add(new JLabel("Trigger [V]:"));
        levelPanel.add(mainTriggerLevelValue);
        JLabel levelHelp = new JLabel(new HelpIcon());
        levelHelp.setToolTipText(htmlTooltip(levelTooltipStr));
        levelHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        levelPanel.add(levelHelp);
        
        triggerDisplayPanel.add(modePanel);
        triggerDisplayPanel.add(levelPanel);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.insets = new java.awt.Insets(6, 4, 10, 4);
        actions.add(triggerDisplayPanel, gbc);
        gbc.insets = new java.awt.Insets(2, 4, 2, 4);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        actions.add(rpStartOnceButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        actions.add(rpStartLiveButton, gbc);
        gbc.gridx = 1;
        actions.add(rpStopButton, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;

        JPanel fileConfigPanel = new JPanel(new GridBagLayout());
        // === TWORZENIE OKNA "CONFIGURE OUTPUT" ===
        JDialog outputConfigDialog = new JDialog(this, "Configure Output", true);
        JPanel outDiagPanel = new JPanel(new GridBagLayout());
        outDiagPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints dGbc = new GridBagConstraints();
        dGbc.fill = GridBagConstraints.HORIZONTAL;
        dGbc.insets = new Insets(4, 4, 4, 4);

        // Defect Type
        dGbc.gridx = 0; dGbc.gridy = 0; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Defect type:"), dGbc);
        rpDefectTypeCombo = new JComboBox<>(new String[]{
            "<None / Custom>", "Corona_HV", "Corona_LV", "Floating_Potential",
            "Void_Discharge", "Surface_Discharge", "Surface_Tracking",
            "Loose_Contact", "Series_Arc", "Parallel_Arc"
        });
        rpDefectTypeCombo.setEditable(true);
        rpDefectTypeCombo.setSelectedItem("Corona_HV");
        rpDefectTypeCombo.addActionListener(e -> {
            updateOutputPreview();
        });
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(rpDefectTypeCombo, dGbc);

        // Session Label (teraz gridy = 4)
        dGbc.gridx = 0; dGbc.gridy = 4; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Label/Prefix:"), dGbc);
        rpSessionLabelField = new JTextField("unknown", 15);
        rpSessionLabelField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateOutputPreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateOutputPreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateOutputPreview(); }
        });
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(rpSessionLabelField, dGbc);

        // Voltage (teraz gridy = 1)
        dGbc.gridx = 0; dGbc.gridy = 1; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Voltage [kV]:"), dGbc);
        rpVoltageKvField = new JTextField("12.0", 15);
        rpVoltageKvField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { handleVoltageChange(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { handleVoltageChange(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { handleVoltageChange(); }
        });
        rpVoltageKvField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                handleVoltageChange();
            }
        });
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(rpVoltageKvField, dGbc);

        // Sensor (teraz gridy = 2)
        dGbc.gridx = 0; dGbc.gridy = 2; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Sensor:"), dGbc);
        rpSensorCombo = new JComboBox<>(new String[]{"<None>", "HFCT", "TEV", "Rogowski", "VHF", "Other"});
        rpSensorCombo.setSelectedItem("<None>");
        rpSensorCombo.addActionListener(e -> {
            updateOutputPreview();
        });
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(rpSensorCombo, dGbc);

        // Variant (teraz gridy = 3)
        dGbc.gridx = 0; dGbc.gridy = 3; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Variant:"), dGbc);
        rpVariantCombo = new JComboBox<>(new String[]{"<None>", "A_Lab", "B_Industrial"});
        rpVariantCombo.setSelectedItem("<None>");
        rpVariantCombo.addActionListener(e -> {
            updateOutputPreview();
        });
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(rpVariantCombo, dGbc);

        // Max Files
        dGbc.gridx = 0; dGbc.gridy = 5; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Max files:"), dGbc);
        JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(Math.max(1, rpFileLimit), 1, 9999, 1));
        limitSpinner.addChangeListener(e -> {
            rpFileLimit = (Integer) limitSpinner.getValue();
            if (rpMaxFilesDisplayLabel != null) {
                rpMaxFilesDisplayLabel.setText(String.valueOf(rpFileLimit));
            }
            try { configuration.saveValue(RP_FILE_LIMIT, "" + rpFileLimit); } catch (IOException ex) {}
        });
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(limitSpinner, dGbc);

        // Custom Directory
        dGbc.gridx = 0; dGbc.gridy = 6; dGbc.weightx = 0.0;
        outDiagPanel.add(new JLabel("Directory:"), dGbc);
        JPanel dirPanel = new JPanel(new BorderLayout(4, 0));
        rpOutputDirField = new JTextField(receivedSignalsDir.getAbsolutePath());
        rpOutputDirField.setEditable(false);
        rpOutputDirBtn = new JButton("...");
        rpOutputDirBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(rpOutputDirField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(outputConfigDialog) == JFileChooser.APPROVE_OPTION) {
                String chosenPath = chooser.getSelectedFile().getAbsolutePath();
                rpOutputDirField.setText(chosenPath);
                updateOutputPreview();
            }
        });
        dirPanel.add(rpOutputDirField, BorderLayout.CENTER);
        dirPanel.add(rpOutputDirBtn, BorderLayout.EAST);
        dGbc.gridx = 1; dGbc.weightx = 1.0;
        outDiagPanel.add(dirPanel, dGbc);

        // Dialog Preview Labels
        dGbc.gridx = 0; dGbc.gridy = 7; dGbc.gridwidth = 2; dGbc.weightx = 1.0;
        dGbc.insets = new Insets(15, 4, 0, 4);
        outDiagPanel.add(new JLabel("<html><b>Preview:</b></html>"), dGbc);
        
        dialogPreviewDirLabel = new JLabel("...");
        dialogPreviewDirLabel.setFont(dialogPreviewDirLabel.getFont().deriveFont(Font.ITALIC, 11f));
        dialogPreviewDirLabel.setForeground(Color.DARK_GRAY);
        dGbc.gridy = 8; dGbc.insets = new Insets(2, 4, 0, 4);
        outDiagPanel.add(dialogPreviewDirLabel, dGbc);

        dialogPreviewFileLabel = new JLabel("...");
        dialogPreviewFileLabel.setFont(dialogPreviewFileLabel.getFont().deriveFont(Font.BOLD, 12f));
        dialogPreviewFileLabel.setForeground(Color.BLUE.darker());
        dGbc.gridy = 9; dGbc.insets = new Insets(2, 4, 10, 4);
        outDiagPanel.add(dialogPreviewFileLabel, dGbc);

        // Dialog Close Button
        JButton closeDiagBtn = new JButton("OK");
        closeDiagBtn.addActionListener(e -> outputConfigDialog.setVisible(false));
        dGbc.gridy = 10; dGbc.fill = GridBagConstraints.NONE; dGbc.anchor = GridBagConstraints.CENTER;
        outDiagPanel.add(closeDiagBtn, dGbc);

        outputConfigDialog.add(outDiagPanel);
        outputConfigDialog.pack();
        outputConfigDialog.setSize(Math.max(650, outputConfigDialog.getWidth()), outputConfigDialog.getHeight());

        // === PANEL GŁÓWNY (PREVIEW I PRZYCISK) ===
        fileConfigPanel = new JPanel(new GridBagLayout());
        fileConfigPanel.setBorder(BorderFactory.createTitledBorder("Output Configuration"));
        GridBagConstraints fcGbc = new GridBagConstraints();
        fcGbc.fill = GridBagConstraints.HORIZONTAL;
        fcGbc.insets = new Insets(2, 4, 2, 4);

        outputDirLabel = new JLabel("...");
        outputDirLabel.setFont(outputDirLabel.getFont().deriveFont(Font.ITALIC, 10f));
        
        outputFileLabel = new JLabel("...");
        outputFileLabel.setFont(outputFileLabel.getFont().deriveFont(Font.BOLD, 11f));

        JButton configureOutputBtn = new JButton("Configure Output");
        configureOutputBtn.addActionListener(e -> {
            outputConfigDialog.setLocationRelativeTo(this);
            outputConfigDialog.setVisible(true);
        });

        fcGbc.gridx = 0; fcGbc.gridy = 0; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("Directory:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(outputDirLabel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 1; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("File:"), fcGbc);
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(outputFileLabel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 2; fcGbc.weightx = 0.0;
        fileConfigPanel.add(new JLabel("Max files:"), fcGbc);
        
        JPanel maxFilesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rpMaxFilesDisplayLabel = new JLabel(String.valueOf(rpFileLimit));
        rpMaxFilesDisplayLabel.setFont(rpMaxFilesDisplayLabel.getFont().deriveFont(Font.BOLD, 11f));
        maxFilesPanel.add(rpMaxFilesDisplayLabel);
        JLabel maxFilesHelp = new JLabel(new HelpIcon());
        maxFilesHelp.setToolTipText(htmlTooltip("Controls how many files are kept on disk during sequential acquisition.\n\nFor example, if set to 30, the program acts as a rolling buffer, keeping only the 30 newest files and automatically deleting older ones to save disk space.\n\nYou can change this limit in the 'Configure Output' window."));
        maxFilesHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        maxFilesPanel.add(maxFilesHelp);
        
        fcGbc.gridx = 1; fcGbc.weightx = 1.0;
        fileConfigPanel.add(maxFilesPanel, fcGbc);

        fcGbc.gridx = 0; fcGbc.gridy = 3; fcGbc.gridwidth = 2;
        fcGbc.fill = GridBagConstraints.NONE; fcGbc.anchor = GridBagConstraints.CENTER;
        fcGbc.insets = new Insets(6, 4, 2, 4);
        fileConfigPanel.add(configureOutputBtn, fcGbc);

        // Wywołanie aktualizacji preview na start
        updateOutputPreview();
        rpSettingsDialog = new JDialog(this, "Red Pitaya Settings", false);
        rpSettingsDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        dialogPanel.add(formPanel, BorderLayout.CENTER);
        
        rpSettingsDialog.add(dialogPanel);
        rpSettingsDialog.pack();
        rpSettingsDialog.setLocationRelativeTo(this);

        rpEstimatedSizeLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JPanel actionsAndFiles = new JPanel();
        actionsAndFiles.setLayout(new BoxLayout(actionsAndFiles, BoxLayout.Y_AXIS));
        actionsAndFiles.add(actions);
        actionsAndFiles.add(fileConfigPanel);
        rpPanel.add(actionsAndFiles, BorderLayout.NORTH);
        rpPanel.add(rpEstimatedSizeLabel, BorderLayout.SOUTH);

        rpChannelsCombo.addActionListener(e -> updateRedPitayaChannelControls(true));
        rpVisualChannelCombo.addActionListener(e -> {
            boolean isDual = "IN1 + IN2".equals(rpVisualChannelCombo.getSelectedItem());
            if (interactiveSignalPanel != null) {
                interactiveSignalPanel.setShowIn2(isDual);
            }
        });
        rpModeCombo.addActionListener(e -> updateRedPitayaModeControls());
        rpDecimationSpinner.addChangeListener(e -> {
            int val = (Integer) rpDecimationSpinner.getValue();
            if (val < 1) val = 1;
            int powerOfTwo = Integer.highestOneBit(val);
            if (val != powerOfTwo) {
                javax.swing.SwingUtilities.invokeLater(() -> rpDecimationSpinner.setValue(powerOfTwo));
            } else {
                updateRedPitayaDerivedControls();
            }
        });
        rpDurationSpinner.addChangeListener(e -> updateRedPitayaDerivedControls());
        rpFrameSizeCombo.addActionListener(e -> updateRedPitayaDerivedControls());
        rpFrameCountSpinner.addChangeListener(e -> updateRedPitayaDerivedControls());

        setupAutoClampListener(rpDurationSpinner);
        setupAutoClampListener(rpFrameCountSpinner);
        setupAutoClampListener(rpDecimationSpinner);
        setupAutoClampListener(rpPortSpinner);
        setupAutoClampListener(rpTriggerLevelSpinner);
        setupAutoClampListener(rpTriggerDelaySpinner);
        setupAutoClampListener(rpTriggerTimeoutSpinner);

        updateRedPitayaChannelControls();
        updateRedPitayaModeControls();
        return rpPanel;
    }

    private void addFormRow(JPanel panel, String label, JComponent component, String tooltip) {
        int row = panel.getComponentCount() / 3;

        JLabel labelComponent = new JLabel(label);
        labelComponent.setToolTipText(htmlTooltip(tooltip));

        JLabel help = new JLabel(new HelpIcon());
        help.setToolTipText(htmlTooltip(tooltip));
        help.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        help.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        help.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showHelpDialog(label, tooltip);
            }
        });
        component.setToolTipText(htmlTooltip(tooltip));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;

        // Col 0: Label
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(2, 2, 2, 6);
        panel.add(labelComponent, gbc);

        // Col 1: Help icon (?)
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(2, 0, 2, 8);
        panel.add(help, gbc);

        // Col 2: Input component
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 2, 2, 2);
        panel.add(component, gbc);
    }

    private void showHelpDialog(String title, String content) {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        editorPane.setText(htmlTooltip(content));
        editorPane.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(new Dimension(540, 420));
        JOptionPane.showMessageDialog(this, scrollPane, "Pomoc: " + title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void setDecimalSpinnerEditor(JSpinner spinner) {
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.0##############");
        if (spinner.getModel() instanceof SpinnerNumberModel numberModel) {
            DecimalFormat df = (DecimalFormat) editor.getFormat();
            NumberFormatter clampingFormatter = new NumberFormatter(df) {
                @Override
                public Object stringToValue(String text) throws ParseException {
                    if (text == null || text.trim().isEmpty()) {
                        throw new ParseException("Empty text", 0);
                    }
                    try {
                        double v = Double.parseDouble(text.trim().replace(',', '.'));
                        Comparable<?> max = numberModel.getMaximum();
                        Comparable<?> min = numberModel.getMinimum();
                        if (max instanceof Number maxN && v > maxN.doubleValue()) {
                            v = maxN.doubleValue();
                        }
                        if (min instanceof Number minN && v < minN.doubleValue()) {
                            v = minN.doubleValue();
                        }
                        return v;
                    } catch (NumberFormatException e) {
                        throw new ParseException(e.getMessage(), 0);
                    }
                }
            };
            clampingFormatter.setValueClass(Double.class);
            editor.getTextField().setFormatterFactory(new DefaultFormatterFactory(clampingFormatter));
        }
        spinner.setEditor(editor);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor defaultEditor) {
            defaultEditor.getTextField().setColumns(12);
        }
    }

    private void setIntegerSpinnerEditor(JSpinner spinner) {
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "#");
        if (spinner.getModel() instanceof SpinnerNumberModel numberModel) {
            DecimalFormat df = (DecimalFormat) editor.getFormat();
            NumberFormatter clampingFormatter = new NumberFormatter(df) {
                @Override
                public Object stringToValue(String text) throws ParseException {
                    if (text == null || text.trim().isEmpty()) {
                        throw new ParseException("Empty text", 0);
                    }
                    try {
                        long v = Math.round(Double.parseDouble(text.trim().replace(',', '.')));
                        Comparable<?> max = numberModel.getMaximum();
                        Comparable<?> min = numberModel.getMinimum();
                        if (max instanceof Number maxN && v > maxN.longValue()) {
                            v = maxN.longValue();
                        }
                        if (min instanceof Number minN && v < minN.longValue()) {
                            v = minN.longValue();
                        }
                        if (numberModel.getValue() instanceof Long) {
                            return v;
                        }
                        return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, v));
                    } catch (NumberFormatException e) {
                        throw new ParseException(e.getMessage(), 0);
                    }
                }
            };
            clampingFormatter.setValueClass(numberModel.getValue() instanceof Long ? Long.class : Integer.class);
            editor.getTextField().setFormatterFactory(new DefaultFormatterFactory(clampingFormatter));
        }
        spinner.setEditor(editor);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor defaultEditor) {
            defaultEditor.getTextField().setColumns(10);
        }
    }

    private void setupAutoClampListener(JSpinner spinner) {
        if (spinner == null || !(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)) {
            return;
        }
        JFormattedTextField tf = editor.getTextField();
        tf.addActionListener(e -> {
            commitSpinnerEdit(spinner);
            updateRedPitayaDerivedControls();
        });
        tf.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commitSpinnerEdit(spinner);
                updateRedPitayaDerivedControls();
            }
        });
    }

    private String helpText(String key) {
        return switch (key) {
            case "host" ->
                "IP address or DNS name of the Red Pitaya board.\n"
                + "\n"
                + "Tip: The default hostname is usually printed on the front connector of the physical board. "
                + "For example, if your board shows 'f0f771', the host name is 'rp-f0f771.local'.\n"
                + "\n"
                + "Note: The rp_prpd_agent.py agent must be running on this board.";
            case "port" ->
                "TCP network port used to communicate with the Red Pitaya board.\n"
                + "\n"
                + "This value must exactly match the port number configured in the Python agent running on the board (the --port parameter).\n"
                + "\n"
                + "Default value: 9999";
            case "channels" ->
                "ADC input channels to record during acquisition.\n"
                + "\n"
                + "Available options:\n"
                + "- IN1: Record only channel 1 (typically the main PD signal).\n"
                + "- IN2: Record only channel 2.\n"
                + "- IN1 + IN2: Record both channels simultaneously. Required if you want to use the 'HW Phase Ref' feature to synchronize the PRPD plot with a reference voltage on IN2.\n"
                + "\n"
                + "Hardware Buffer Impact (256 MB AXI DMA DDR RAM):\n"
                + "Red Pitaya STEMlab 125-14 allocates a dedicated 256 MB block in DDR RAM for hardware AXI DMA streaming (134,215,680 total 16-bit samples).\n"
                + "- 1 Channel (IN1 or IN2): The entire buffer is dedicated to one channel: 134,215,680 samples (~1.07 s at 125 MS/s, ~68.7 s at Decimation 64).\n"
                + "- 2 Channels (IN1 + IN2): The 256 MB buffer is shared equally: 67,107,840 samples per channel (~0.53 s at 125 MS/s, ~34.3 s at Decimation 64).\n"
                + "\n"
                + "Selecting 2 channels halves the maximum allowed acquisition duration.";
            case "visual" ->
                "Channel forwarded to the PRPD visualization and signal viewer.\n"
                + "\n"
                + "Available options:\n"
                + "- IN1: Visualize channel 1.\n"
                + "- IN2: Visualize channel 2.\n"
                + "- IN1 + IN2: Display IN1 on the main plot and overlay IN2 in the background (grey curve, right Y-axis) on the Base signal graph.";
            case "gain1" ->
                "Red Pitaya input range configuration for channel IN1.\n"
                + "\n"
                + "Available jumper settings:\n"
                + "- LV (Low Voltage): ±1V range. Use for weak signals requiring high ADC precision.\n"
                + "- HV (High Voltage): ±20V range. Use for strong signals to prevent clipping/saturation.\n"
                + "\n"
                + "Note: This must physically match the jumper position on the STEMlab 125-14 board.";
            case "gain2" ->
                "Red Pitaya input range configuration for channel IN2.\n"
                + "\n"
                + "Available jumper settings:\n"
                + "- LV (Low Voltage): ±1V range. Use for weak signals requiring high ADC precision.\n"
                + "- HV (High Voltage): ±20V range. Use for strong signals to prevent clipping/saturation.\n"
                + "\n"
                + "Note: This must physically match the jumper position on the STEMlab 125-14 board.";
            case "decimation" ->
                "Decimation factor for FPGA ADC downsampling.\n"
                + "Only powers of 2 are supported (1, 2, 4, 8, 16, 32, 64, 128, ...).\n"
                + "\n"
                + "Formula:\n"
                + "fs = 125 MS/s / Decimation\n"
                + "\n"
                + "Hardware Buffer Limits (256 MB DDR RAM AXI DMA):\n"
                + "Because the physical RAM buffer is fixed at 256 MB (134.2 MS for 1 ch / 67.1 MS for 2 ch), "
                + "increasing decimation directly increases the maximum continuous recording duration:\n"
                + "<pre>\n"
                + "+-----+----------------+----------------+----------------+\n"
                + "| Dec | Sampling (fs)  | Max Time (1ch) | Max Time (2ch) |\n"
                + "+-----+----------------+----------------+----------------+\n"
                + "|   1 |   125.000 MS/s |         1.07 s |         0.53 s |\n"
                + "|   2 |    62.500 MS/s |         2.14 s |         1.07 s |\n"
                + "|   4 |    31.250 MS/s |         4.29 s |         2.14 s |\n"
                + "|   8 |    15.625 MS/s |         8.58 s |         4.29 s |\n"
                + "|  16 |     7.813 MS/s |        17.17 s |         8.58 s |\n"
                + "|  32 |     3.906 MS/s |        34.35 s |        17.17 s |\n"
                + "|  64 |     1.953 MS/s |        68.71 s |        34.35 s |\n"
                + "| 128 |   976.562 kS/s |       137.43 s |        68.71 s |\n"
                + "+-----+----------------+----------------+----------------+\n"
                + "</pre>\n"
                + "Note: Higher decimation enables longer recording times, but reduces pulse time resolution.";
            case "averaging" ->
                "Red Pitaya hardware decimation averaging.\n"
                + "\n"
                + "How it works when Decimation > 1:\n"
                + "- Disabled: Discards the extra samples (aliases high frequencies).\n"
                + "- Enabled: Averages the extra samples inside the FPGA before outputting.\n"
                + "\n"
                + "Effect:\n"
                + "Enabling acts as a digital low-pass filter, reducing high-frequency noise and increasing effective resolution (ENOB). Highly recommended for high decimation values.";
            case "trigger" ->
                "The trigger defines when acquisition starts.\n"
                + "\n"
                + "Available modes:\n"
                + "- NOW: start immediately after clicking Start.\n"
                + "- CH1_PE: rising edge on IN1.\n"
                + "- CH1_NE: falling edge on IN1.\n"
                + "- CH2_PE: rising edge on IN2.\n"
                + "- CH2_NE: falling edge on IN2.\n"
                + "- EXT_PE: external trigger, rising edge.\n"
                + "- EXT_NE: external trigger, falling edge.\n"
                + "\n"
                + "For CH1/CH2 modes, the Trigger Level [V] field is used.";
            case "triggerLevel" ->
                "Trigger level in volts for CH1_PE, CH1_NE, CH2_PE, and CH2_NE modes.\n"
                + "\n"
                + "Example:\n"
                + "CH1_PE with level 0.1 V starts acquisition when IN1 crosses about 0.1 V on a rising edge.\n"
                + "\n"
                + "Note on Hysteresis:\n"
                + "Red Pitaya hardware has a built-in trigger hysteresis (typically around 5-10 mV). "
                + "If the signal's peak-to-peak amplitude is extremely small (e.g., background noise of just a few mV), "
                + "the trigger will not arm and will result in a timeout. To observe such tiny signals, use the 'NOW' trigger mode.";
            case "triggerDelay" ->
                "Trigger delay in samples used by the DMA buffer.\n"
                + "\n"
                + "Meaning:\n"
                + "- defines the trigger position relative to the saved buffer,\n"
                + "- affects how many samples after the trigger are stored,\n"
                + "- value 0 in this program means automatic mode.\n"
                + "\n"
                + "Automatic mode:\n"
                + "the agent sets the delay to the full acquisition size, so the whole requested buffer is collected after the trigger.\n"
                + "\n"
                + "Example:\n"
                + "at fs=1 MS/s, value 10000 corresponds to 10 ms.\n"
                + "\n"
                + "Negative values may be used to inspect a fragment before the trigger if the specific Red Pitaya API/FPGA version supports it.";
            case "triggerTimeout" ->
                "Maximum time (in seconds) the system will wait for an acquisition to complete.\n"
                + "\n"
                + "This timer includes both:\n"
                + "1. Waiting for the trigger condition to occur (if not using 'NOW').\n"
                + "2. Filling the DMA buffer with the requested number of samples.\n"
                + "\n"
                + "If this time elapses before the data is ready, the acquisition will abort with a timeout error. It is recommended to set this value slightly higher than your expected Duration + Trigger wait time.";
            case "mode" ->
                "Defines how the total length of the acquisition is specified.\n"
                + "\n"
                + "Available modes:\n"
                + "- duration: You specify the exact time [s]. The program automatically calculates how many samples and frames are needed to cover this time period.\n"
                + "- frames: You manually specify the exact number of frames (chunks) to capture. The duration is then calculated based on the Frame size and sampling frequency.";
            case "duration" ->
                "Total time (in seconds) to record per single continuous acquisition.\n"
                + "\n"
                + "Origin of Hardware Duration Limits:\n"
                + "The Red Pitaya FPGA streams continuous samples into a dedicated 256 MB (268,431,360 bytes) "
                + "circular DDR RAM buffer via AXI DMA. Each sample is 16-bit (2 bytes), allowing up to:\n"
                + "- 134,215,680 samples for 1 channel (IN1 or IN2)\n"
                + "- 67,107,840 samples per channel for 2 channels (IN1 + IN2)\n"
                + "\n"
                + "Why Duration is bounded:\n"
                + "If an acquisition exceeds the buffer capacity before data is transferred, the circular DMA pointer "
                + "overwrites earlier unread samples, causing dropped samples, buffer wrap-around, and phase discontinuities. "
                + "To guarantee zero data loss, the software strictly clamps Duration to the physical buffer limit:\n"
                + "<pre>\n"
                + "+-----+----------------+----------------+----------------+\n"
                + "| Dec | Sampling (fs)  | Max Time (1ch) | Max Time (2ch) |\n"
                + "+-----+----------------+----------------+----------------+\n"
                + "|   1 |   125.000 MS/s |         1.07 s |         0.53 s |\n"
                + "|   2 |    62.500 MS/s |         2.14 s |         1.07 s |\n"
                + "|   4 |    31.250 MS/s |         4.29 s |         2.14 s |\n"
                + "|   8 |    15.625 MS/s |         8.58 s |         4.29 s |\n"
                + "|  16 |     7.813 MS/s |        17.17 s |         8.58 s |\n"
                + "|  32 |     3.906 MS/s |        34.35 s |        17.17 s |\n"
                + "|  64 |     1.953 MS/s |        68.71 s |        34.35 s |\n"
                + "| 128 |   976.562 kS/s |       137.43 s |        68.71 s |\n"
                + "+-----+----------------+----------------+----------------+\n"
                + "</pre>\n"
                + "Behavior depends on Mode:\n"
                + "- 'duration' mode: You set Duration [s], and the program auto-calculates the required number of 4 MS frames.\n"
                + "- 'frames' mode: Duration is calculated from Frame count * Frame size / fs.";
            case "frameSize" ->
                "Number of samples per channel transmitted in a single DMA chunk (TCP packet).\n"
                + "\n"
                + "Streaming & Memory Architecture:\n"
                + "Red Pitaya user Linux space has limited free RAM (~150 MB). To ensure stability and prevent Out-Of-Memory (OOM) crashes, "
                + "data is read from the 256 MB FPGA DMA buffer and streamed to the PC in manageable chunks (default 4 MS = 4,194,304 samples).\n"
                + "\n"
                + "Available tested sizes (selectable in 'frames' mode):\n"
                + "- 4 194 304 (4 MS - default & recommended for continuous streaming)\n"
                + "- 2 097 152 (2 MS)\n"
                + "- 1 048 576 (1 MS)\n"
                + "- 524 288 (512 kS)\n"
                + "- 65 536 (64 kS)\n"
                + "\n"
                + "In 'duration' mode, frame size is locked to 4 MS for optimal network throughput and continuous buffer synchronization.";
            case "frameCount" ->
                "Number of frames (data chunks) to collect per acquisition.\n"
                + "\n"
                + "Behavior depends on Mode:\n"
                + "- 'duration' mode: Auto-calculated based on Duration and 4 MS frame size (4,194,304 samples).\n"
                + "- 'frames' mode: You set this manually. Total samples = Frame count * Frame size.\n"
                + "\n"
                + "Hardware Buffer Limit:\n"
                + "The total recorded samples cannot exceed the 256 MB DDR RAM DMA buffer (134,215,680 samples for 1 ch, 67,107,840 for 2 ch). "
                + "For example, with standard 4 MS frames (4,194,304 samples):\n"
                + "- 1 Channel: maximum 32 frames (32 * 4 MS = 134.2 MS)\n"
                + "- 2 Channels: maximum 16 frames (16 * 4 MS = 67.1 MS)\n"
                + "\n"
                + "Setting a higher frame count is prevented by the software to avoid circular DMA buffer overwriting and lost packets.";
            case "frameTime" ->
                "Duration of a single acquisition frame in seconds (and milliseconds).\n"
                + "\n"
                + "Calculated automatically as: Frame size / fs, where fs = 125 MHz / Decimation.\n"
                + "This field is informative and read-only.";
            case "autoTriggerIn1" ->
                "Open the trigger calibration window for IN1.\n"
                + "\n"
                + "The calibrator collects a reference measurement without defect and a second measurement with defect, then proposes a trigger level.";
            case "autoTriggerIn2" ->
                "Open the trigger calibration window for IN2.\n"
                + "\n"
                + "The calibrator collects a reference measurement without defect and a second measurement with defect, then proposes a trigger level.";
            case "startLive" ->
                "Start repeated Red Pitaya acquisitions.\n"
                + "\n"
                + "After one acquisition finishes, the next one starts automatically until Stop RP is clicked.";
            case "startOnce" ->
                "Start one Red Pitaya acquisition using the current settings.";
            case "stopRp" ->
                "Stop the active Red Pitaya acquisition or live loop and close the TCP reader.";
            default ->
                "";
        };
    }

    private String paramHelpText(String key) {
        return switch (key) {
            case "Basic frequency [Hz]" ->
                "Power line frequency.\n\n"
                + "Serves as the reference to calculate the phase angle of each pulse (0-360 degrees) and to filter out slow background drift.\n\n"
                + "Values: Typically 50.0";
            case "Zero-crossing instant [s]" ->
                "Phase shift correction.\n\n"
                + "Determines the fraction of a second where the voltage crosses zero. Set automatically by the software.\n\n"
                + "Values: Changing this manually (e.g., adding 0.005 s) will horizontally shift the PRPD plot by 90 degrees. Useful for manual phase alignment.";
            case "Sampling frequency [Hz]" ->
                "Data acquisition speed.\n\n"
                + "Defines how many measurement points per second were recorded. Set automatically from data files.\n\n"
                + "Values: Typically 1953125.0 (for decimation 64). Edit only if loading a raw text file missing header information.";
            case "Pulse ampl. threshold [mV]" ->
                "Noise rejection threshold [mV].\n\n"
                + "Any peaks with an amplitude lower than this value (in mV) are considered background noise and ignored on the PRPD plot.\n\n"
                + "Values: Typically 5.0 to 50.0 mV (or 0.0 to display all pulses). Increase this value slightly until dense background noise disappears from the screen, leaving only clear discharge pulses.";
            case "Dead time [us]" ->
                "Detector blind time.\n\n"
                + "The time immediately following a detected pulse during which the software ignores any further peaks. Prevents falsely counting a single oscillating (ringing) peak as multiple pulses.\n\n"
                + "Values: Typically 5.0 to 20.0 us.";
            case "HPF cutoff frequency [Hz]" ->
                "High-Pass Filter boundary.\n\n"
                + "Removes slow background fluctuations (including the 50 Hz sine wave). Lets only ultra-fast partial discharge spikes pass through.\n\n"
                + "Values: Usually 50000.0 (50 kHz) or 100000.0 (100 kHz).";
            case "Filter Q" ->
                "Filter Quality Factor.\n\n"
                + "Determines the steepness and shape of the digital filter at the cutoff point.\n\n"
                + "Values: Recommended to leave at the default 0.707 (Butterworth) to prevent artificial ringing in the signal.";
            case "Filter Order" ->
                "Digital filter steepness.\n\n"
                + "Determines how aggressively the filter cuts off unwanted frequencies.\n\n"
                + "Values: Typically 2, 4, or 8. Higher values cut the signal more sharply but significantly increase CPU usage and can distort tiny pulses.";
            case "Histogram min [V]" ->
                "Lower boundary of the Y-axis.\n\n"
                + "Sets the absolute minimum physical limit for the rendered PRPD image.\n\n"
                + "Tip: Use the 'Fit' button to let the software automatically calculate the ideal boundaries for maximum image sharpness.";
            case "Histogram max [V]" ->
                "Upper boundary of the Y-axis.\n\n"
                + "Sets the absolute maximum physical limit. Directly influences the vertical resolution of the internal PRPD matrix.\n\n"
                + "Tip: Use the 'Fit' button to automatically scale this to your highest detected pulse.";
            default ->
                "";
        };
    }


    public static class HelpIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(150, 150, 150));
            g2.fillOval(x, y, getIconWidth(), getIconHeight());
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth("?");
            int textHeight = fm.getAscent();
            g2.drawString("?", x + (getIconWidth() - textWidth) / 2, y + (getIconHeight() + textHeight) / 2 - 2);
            g2.dispose();
        }
        @Override
        public int getIconWidth() { return 16; }
        @Override
        public int getIconHeight() { return 16; }
    }
    public static String htmlTooltip(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.startsWith("<html>")) {
            return text;
        }
        StringBuilder html = new StringBuilder("<html><div style='width: 480px; font-family: sans-serif;'>");
        boolean inPre = false;
        String separator = "";
        for (String line : text.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("<pre>")) {
                html.append("<pre style='font-family: monospace; font-size: 11px; margin: 4px 0;'>");
                inPre = true;
                separator = "";
                continue;
            } else if (trimmed.equalsIgnoreCase("</pre>")) {
                html.append("</pre>");
                inPre = false;
                separator = "";
                continue;
            }

            if (inPre) {
                String escaped = line.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
                html.append(separator).append(escaped);
                separator = "\n";
            } else {
                String escaped = line.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
                if (escaped.startsWith("- ")) {
                    escaped = "&bull; " + escaped.substring(2);
                }
                html.append(separator).append(escaped);
                separator = "<br>";
            }
        }
        if (inPre) {
            html.append("</pre>");
        }
        html.append("</div></html>");
        return html.toString();
    }

    private String configValue(String key, String fallback) {
        try {
            String value = configuration.getValue(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        } catch (Exception ex) {
        }
        return fallback;
    }

    private int configInt(String key, int fallback) {
        try {
            return Integer.parseInt(configValue(key, Integer.toString(fallback)));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double configDouble(String key, double fallback) {
        try {
            return Double.parseDouble(configValue(key, Double.toString(fallback)));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private boolean configBoolean(String key, boolean fallback) {
        try {
            return Boolean.parseBoolean(configValue(key, Boolean.toString(fallback)));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void updateRedPitayaChannelControls() {
        updateRedPitayaChannelControls(false);
    }

    private void updateRedPitayaChannelControls(boolean syncVisualToChannels) {
        String channels = (String) rpChannelsCombo.getSelectedItem();
        Object selectedVisual = rpVisualChannelCombo.getSelectedItem();
        boolean hasIn1 = !"IN2".equals(channels);
        boolean hasIn2 = !"IN1".equals(channels);
        rpGain1Combo.setEnabled(hasIn1);
        rpGain2Combo.setEnabled(hasIn2);
        rpVisualChannelCombo.removeAllItems();
        if (hasIn1) {
            rpVisualChannelCombo.addItem("IN1");
        }
        if (hasIn2) {
            rpVisualChannelCombo.addItem("IN2");
        }
        if (hasIn1 && hasIn2) {
            rpVisualChannelCombo.addItem("IN1 + IN2");
        }
        if (syncVisualToChannels) {
            if ("IN1 + IN2".equals(channels) || "IN1+IN2".equals(channels)) {
                rpVisualChannelCombo.setSelectedItem("IN1 + IN2");
            } else if ("IN2".equals(channels)) {
                rpVisualChannelCombo.setSelectedItem("IN2");
            } else {
                rpVisualChannelCombo.setSelectedItem("IN1");
            }
        } else {
            if (selectedVisual != null) {
                rpVisualChannelCombo.setSelectedItem(selectedVisual);
            }
        }
        boolean isDual = "IN1 + IN2".equals(rpVisualChannelCombo.getSelectedItem());
        if (interactiveSignalPanel != null) {
            interactiveSignalPanel.setShowIn2(isDual);
        }
        updateRedPitayaDerivedControls();
    }

    private void updateRedPitayaModeControls() {
        boolean duration = "duration".equals(rpModeCombo.getSelectedItem());
        setSpinnerEnabled(rpDurationSpinner, duration);
        rpFrameSizeCombo.setEnabled(!duration);
        setSpinnerEnabled(rpFrameCountSpinner, !duration);
        if (duration) {
            setSelectedFrameSize(BufferFactory.bufferSize());
        }
        updateRedPitayaDerivedControls();
    }

    private void setSpinnerEnabled(JSpinner spinner, boolean enabled) {
        if (spinner == null) return;
        spinner.setEnabled(enabled);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setEnabled(enabled);
            editor.getTextField().setEditable(enabled);
        }
    }

    private int getSelectedFrameSize() {
        if (rpFrameSizeCombo != null) {
            Object item = rpFrameSizeCombo.getSelectedItem();
            if (item instanceof FrameSizeItem fsi) {
                return fsi.size;
            }
        }
        return BufferFactory.bufferSize();
    }

    private void setSelectedFrameSize(int size) {
        if (rpFrameSizeCombo == null) {
            return;
        }
        for (int i = 0; i < rpFrameSizeCombo.getItemCount(); i++) {
            FrameSizeItem item = rpFrameSizeCombo.getItemAt(i);
            if (item.size == size) {
                rpFrameSizeCombo.setSelectedIndex(i);
                return;
            }
        }
        rpFrameSizeCombo.setSelectedIndex(0);
    }

    private void updateRedPitayaDerivedControls() {
        if (updatingRedPitayaDerivedFields || rpEstimatedSizeLabel == null) {
            return;
        }
        updatingRedPitayaDerivedFields = true;
        try {
            boolean durationMode = "duration".equals(rpModeCombo.getSelectedItem());
            int decimation = Math.max(1, currentSpinnerIntValue(rpDecimationSpinner));
            double sampleRate = RedPitayaConfig.ADC_BASE_RATE / decimation;
            int frameSize = getSelectedFrameSize();
            long frameCount;
            long totalSamples;
            double durationS;

            int channelCount = redPitayaChannelCount();
            long maxSamplesPerChannel = RedPitayaConfig.AXI_USABLE_BYTES / ((long) channelCount * RedPitayaConfig.BYTES_PER_SAMPLE);
            maxSamplesPerChannel -= maxSamplesPerChannel % 4096;
            double maxDurationS = (double) maxSamplesPerChannel / sampleRate;
            long maxFrameCount = Math.max(1L, maxSamplesPerChannel / frameSize);

            // Dynamic clamping of spinner models to prevent buffer overflow
            if (rpDurationSpinner.getModel() instanceof SpinnerNumberModel durationModel) {
                durationModel.setMaximum(maxDurationS);
            }
            if (rpFrameCountSpinner.getModel() instanceof SpinnerNumberModel frameCountModel) {
                frameCountModel.setMaximum((int) Math.min((long) Integer.MAX_VALUE, maxFrameCount));
            }

            if (durationMode) {
                durationS = currentSpinnerDoubleValue(rpDurationSpinner);
                double minDurationS = Math.max(0.000001, 1.0 / sampleRate);
                if (durationS < minDurationS) {
                    durationS = minDurationS;
                    setSpinnerDoubleValue(rpDurationSpinner, durationS);
                } else if (durationS > maxDurationS) {
                    durationS = maxDurationS;
                    setSpinnerDoubleValue(rpDurationSpinner, durationS);
                }
                long reqSamples = Math.max(1L, Math.round(durationS * sampleRate));
                long remainder = reqSamples % 4096;
                if (remainder != 0) {
                    reqSamples += 4096 - remainder;
                }
                if (reqSamples > maxSamplesPerChannel) {
                    reqSamples = maxSamplesPerChannel;
                }
                totalSamples = reqSamples;
                frameCount = ceilDiv(totalSamples, frameSize);
                setSpinnerLongValue(rpFrameCountSpinner, frameCount);
            } else {
                frameCount = currentSpinnerIntValue(rpFrameCountSpinner);
                if (frameCount < 1L) {
                    frameCount = 1L;
                    setSpinnerLongValue(rpFrameCountSpinner, frameCount);
                } else if (frameCount > maxFrameCount) {
                    frameCount = maxFrameCount;
                    setSpinnerLongValue(rpFrameCountSpinner, frameCount);
                }
                totalSamples = (long) frameSize * frameCount;
                long remainder = totalSamples % 4096;
                if (remainder != 0) {
                    totalSamples += 4096 - remainder;
                }
                if (totalSamples > maxSamplesPerChannel) {
                    totalSamples = maxSamplesPerChannel;
                    frameCount = totalSamples / frameSize;
                    setSpinnerLongValue(rpFrameCountSpinner, frameCount);
                }
                durationS = totalSamples / sampleRate;
                setSpinnerDoubleValue(rpDurationSpinner, durationS);
            }

            double frameTimeS = (double) frameSize / sampleRate;
            if (rpFrameTimeField != null) {
                String frameTimeText;
                if (frameTimeS >= 1.0) {
                    frameTimeText = String.format(Locale.US, "%.4f s", frameTimeS);
                } else if (frameTimeS >= 0.001) {
                    frameTimeText = String.format(Locale.US, "%.6f s (%.2f ms)", frameTimeS, frameTimeS * 1000.0);
                } else {
                    frameTimeText = String.format(Locale.US, "%.6f s (%.2f µs)", frameTimeS, frameTimeS * 1_000_000.0);
                }
                rpFrameTimeField.setText(frameTimeText);
            }

            long payloadBytes = multiplySaturating(multiplySaturating(totalSamples, channelCount), 2L);
            long frameHeaderBytes = multiplySaturating(frameCount, 34L);
            long approximateFileBytes = addSaturating(addSaturating(payloadBytes, frameHeaderBytes), 4096L);

            String fsStr;
            if (sampleRate >= 1_000_000) {
                fsStr = String.format(Locale.US, "%.3f MHz", sampleRate / 1_000_000.0);
            } else if (sampleRate >= 1_000) {
                fsStr = String.format(Locale.US, "%.3f kHz", sampleRate / 1_000.0);
            } else {
                fsStr = String.format(Locale.US, "%.3f Hz", sampleRate);
            }

            String maxDurText = (maxDurationS >= 10.0)
                    ? String.format(Locale.US, "%.1f s", maxDurationS)
                    : String.format(Locale.US, "%.3f s", maxDurationS);

            rpEstimatedSizeLabel.setText(String.format(
                    Locale.US,
                    "<html>Approx. file/data: %s<br>Samples: %s | %d ch | fs=%s<br><font color='#FF8C00'><b>Max buffer duration: %s (%s MS)</b></font></html>",
                    formatBytes(approximateFileBytes),
                    formatInteger(totalSamples),
                    channelCount,
                    fsStr,
                    maxDurText,
                    formatInteger(maxSamplesPerChannel / 1_000_000)
            ));
            if (rpSettingsEstimatedSizeLabel != null) {
                rpSettingsEstimatedSizeLabel.setText(String.format(
                        Locale.US,
                        "<html><div style='margin-top: 2px;'><font color='#FF8C00'><b>Max buffer duration: %s (%s MS)</b></font><br><font color='#9E9E9E'>Approx: %s | Samples: %s | fs=%s</font></div></html>",
                        maxDurText,
                        formatInteger(maxSamplesPerChannel / 1_000_000),
                        formatBytes(approximateFileBytes),
                        formatInteger(totalSamples),
                        fsStr
                ));
            }
            updateRedPitayaEstimateFont();
        } catch (Exception ex) {
            rpEstimatedSizeLabel.setText("Approx. file/data size: unavailable");
            if (rpSettingsEstimatedSizeLabel != null) {
                rpSettingsEstimatedSizeLabel.setText("<html><font color='#FF8C00'><b>Max buffer duration: unavailable</b></font></html>");
            }
            if (rpFrameTimeField != null) {
                rpFrameTimeField.setText("-");
            }
        } finally {
            updatingRedPitayaDerivedFields = false;
        }
    }

    private RedPitayaConfig readRedPitayaConfig() {
        commitRedPitayaSpinnerEdits();
        updateRedPitayaDerivedControls();
        RedPitayaConfig config = new RedPitayaConfig();
        config.host = rpHostField.getText().trim();
        config.port = spinnerIntValue(rpPortSpinner);
        config.channels = switch ((String) rpChannelsCombo.getSelectedItem()) {
            case "IN2" ->
                new int[]{2};
            case "IN1 + IN2", "IN1+IN2" ->
                new int[]{1, 2};
            default ->
                new int[]{1};
        };
        config.visualChannel = "IN2".equals(rpVisualChannelCombo.getSelectedItem()) ? 2 : 1;
        config.gainCh1 = (String) rpGain1Combo.getSelectedItem();
        config.gainCh2 = (String) rpGain2Combo.getSelectedItem();
        config.decimation = spinnerIntValue(rpDecimationSpinner);
        config.averaging = rpAveragingBox.isSelected();
        config.triggerSource = (String) rpTriggerCombo.getSelectedItem();
        config.triggerLevel = spinnerDoubleValue(rpTriggerLevelSpinner);
        config.triggerDelay = spinnerIntValue(rpTriggerDelaySpinner);
        config.triggerTimeoutS = spinnerDoubleValue(rpTriggerTimeoutSpinner);
        config.durationMode = "duration".equals(rpModeCombo.getSelectedItem());
        config.durationS = spinnerDoubleValue(rpDurationSpinner);
        config.frameSize = config.durationMode ? BufferFactory.bufferSize() : getSelectedFrameSize();
        config.frameCount = spinnerIntValue(rpFrameCountSpinner);
        // --- Metadane eksperymentu ---
        if (rpDefectTypeCombo != null) config.defectType = (String) rpDefectTypeCombo.getSelectedItem();
        if (rpVoltageKvField != null) {
            try {
                config.voltageKv = Double.parseDouble(rpVoltageKvField.getText().trim().replace(',', '.'));
            } catch (Exception e) {
                config.voltageKv = 0.0;
            }
        }
        if (rpSensorCombo != null) config.sensor = (String) rpSensorCombo.getSelectedItem();
        if (rpVariantCombo != null) config.variant = (String) rpVariantCombo.getSelectedItem();
        if (rpSessionLabelField != null) config.sessionLabel = rpSessionLabelField.getText();
        config.validate(BufferFactory.bufferSize());
        return config;
    }

    private void commitRedPitayaSpinnerEdits() {
        JSpinner[] spinners = {
            rpPortSpinner,
            rpDecimationSpinner,
            rpTriggerLevelSpinner,
            rpTriggerDelaySpinner,
            rpTriggerTimeoutSpinner,
            rpDurationSpinner,
            rpFrameCountSpinner
        };
        for (JSpinner spinner : spinners) {
            commitSpinnerEdit(spinner);
        }
    }

    private void commitSpinnerEdit(JSpinner spinner) {
        if (spinner == null) return;
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            String text = editor.getTextField().getText().trim().replace(',', '.');
            if (text.isEmpty() || text.equals(".") || text.equals("-")) {
                editor.getTextField().setValue(spinner.getValue());
                return;
            }
            SpinnerModel model = spinner.getModel();
            if (model instanceof SpinnerNumberModel numberModel) {
                Number current = numberModel.getNumber();
                try {
                    Comparable<?> max = numberModel.getMaximum();
                    Comparable<?> min = numberModel.getMinimum();
                    if (current instanceof Integer || current instanceof Long) {
                        long val = Math.round(Double.parseDouble(text));
                        if (max instanceof Number maxN && val > maxN.longValue()) {
                            val = maxN.longValue();
                        }
                        if (min instanceof Number minN && val < minN.longValue()) {
                            val = minN.longValue();
                        }
                        if (current instanceof Long) {
                            spinner.setValue(val);
                        } else {
                            spinner.setValue((int) val);
                        }
                    } else {
                        double val = Double.parseDouble(text);
                        if (max instanceof Number maxN && val > maxN.doubleValue()) {
                            val = maxN.doubleValue();
                        }
                        if (min instanceof Number minN && val < minN.doubleValue()) {
                            val = minN.doubleValue();
                        }
                        spinner.setValue(val);
                    }
                    editor.getTextField().setValue(spinner.getValue());
                    editor.getTextField().setBackground(UIManager.getColor("TextField.background"));
                    return;
                } catch (NumberFormatException ex) {
                    editor.getTextField().setBackground(new Color(255, 200, 200));
                }
            }
        }
        try {
            spinner.commitEdit();
        } catch (ParseException ex) {
        }
    }

    private double spinnerDoubleValue(JSpinner spinner) {
        commitSpinnerEdit(spinner);
        return ((Number) spinner.getValue()).doubleValue();
    }

    private int spinnerIntValue(JSpinner spinner) {
        commitSpinnerEdit(spinner);
        return ((Number) spinner.getValue()).intValue();
    }

    private double currentSpinnerDoubleValue(JSpinner spinner) {
        if (spinner != null && spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            String text = editor.getTextField().getText().trim().replace(',', '.');
            if (!text.isEmpty() && !text.equals(".") && !text.equals("-")) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException ignored) {}
            }
        }
        return ((Number) spinner.getValue()).doubleValue();
    }

    private int currentSpinnerIntValue(JSpinner spinner) {
        if (spinner != null && spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            String text = editor.getTextField().getText().trim().replace(',', '.');
            if (!text.isEmpty() && !text.equals(".") && !text.equals("-")) {
                try {
                    return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, Math.round(Double.parseDouble(text))));
                } catch (NumberFormatException ignored) {}
            }
        }
        return ((Number) spinner.getValue()).intValue();
    }

    private int normalizedEven(int value) {
        int normalized = Math.max(1, value);
        return (normalized & 1) == 0 ? normalized : normalized + 1;
    }

    private long normalizedEven(long value) {
        long normalized = Math.max(1L, value);
        return (normalized & 1L) == 0L ? normalized : normalized + 1L;
    }

    private long ceilDiv(long a, long b) {
        return (a + b - 1L) / b;
    }

    private int redPitayaChannelCount() {
        String channels = (String) rpChannelsCombo.getSelectedItem();
        return ("IN1 + IN2".equals(channels) || "IN1+IN2".equals(channels)) ? 2 : 1;
    }

    private void setSpinnerLongValue(JSpinner spinner, long value) {
        long bounded = Math.max(1L, Math.min(Integer.MAX_VALUE, value));
        spinner.setValue((int) bounded);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setValue((int) bounded);
        }
    }

    private void setSpinnerDoubleValue(JSpinner spinner, double value) {
        if (spinner.getModel() instanceof SpinnerNumberModel model) {
            Comparable<?> min = model.getMinimum();
            Comparable<?> max = model.getMaximum();
            if (min instanceof Number number && value < number.doubleValue()) {
                model.setMinimum(value);
            }
            if (max instanceof Number number && value > number.doubleValue()) {
                model.setMaximum(value);
            }
        }
        spinner.setValue(value);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setValue(value);
        }
    }

    private long multiplySaturating(long a, long b) {
        if (a == 0L || b == 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private long addSaturating(long a, long b) {
        if (Long.MAX_VALUE - a < b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private String formatInteger(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String formatBytes(long bytes) {
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        if (unit == 0) {
            return String.format(Locale.US, "%d %s", bytes, units[unit]);
        }
        return String.format(Locale.US, value < 10.0 ? "%.2f %s" : "%.1f %s", value, units[unit]);
    }

    private void updateRedPitayaEstimateFont() {
        if (rpEstimatedSizeLabel != null) {
            float size = Math.max(9.0f, currentFont.getSize2D() - 1.5f);
            rpEstimatedSizeLabel.setFont(currentFont.deriveFont(size));
        }
        if (rpSettingsEstimatedSizeLabel != null) {
            float size = Math.max(10.0f, currentFont.getSize2D() - 1.0f);
            rpSettingsEstimatedSizeLabel.setFont(currentFont.deriveFont(Font.PLAIN, size));
        }
    }

    private void saveRedPitayaConfig(RedPitayaConfig config) {
        try {
            configuration.saveValue(RP_HOST, config.host);
            configuration.saveValue(RP_PORT, Integer.toString(config.port));
            configuration.saveValue(RP_CHANNELS, (String) rpChannelsCombo.getSelectedItem());
            configuration.saveValue(RP_VISUAL_CHANNEL, (String) rpVisualChannelCombo.getSelectedItem());
            configuration.saveValue(RP_GAIN1, config.gainCh1);
            configuration.saveValue(RP_GAIN2, config.gainCh2);
            configuration.saveValue(RP_DECIMATION, Integer.toString(config.decimation));
            configuration.saveValue(RP_AVERAGING, Boolean.toString(config.averaging));
            configuration.saveValue(RP_TRIGGER_SOURCE, config.triggerSource);
            configuration.saveValue(RP_TRIGGER_LEVEL, Double.toString(config.triggerLevel));
            configuration.saveValue(RP_TRIGGER_DELAY, Integer.toString(config.triggerDelay));
            configuration.saveValue(RP_TRIGGER_TIMEOUT, Double.toString(config.triggerTimeoutS));
            configuration.saveValue(RP_MODE, config.durationMode ? "duration" : "frames");
            configuration.saveValue(RP_DURATION, Double.toString(config.durationS));
            configuration.saveValue(RP_FRAME_SIZE, Integer.toString(config.frameSize));
            configuration.saveValue(RP_FRAME_COUNT, Integer.toString(config.frameCount));
            // --- Metadane eksperymentu ---
            configuration.saveValue(RP_DEFECT_TYPE, config.defectType);
            configuration.saveValue(RP_VOLTAGE_KV,  Double.toString(config.voltageKv));
            configuration.saveValue(RP_SENSOR,       config.sensor);
            configuration.saveValue(RP_VARIANT,      config.variant);
            configuration.saveValue(RP_SESSION_LABEL, config.sessionLabel);
        } catch (IOException ex) {
            status.setText(ex.getMessage());
        }
    }

    private void openTriggerDialog(int channel) {
        try {
            RedPitayaConfig config = readRedPitayaConfig();
            saveRedPitayaConfig(config);
            RedPitayaTriggerDialog dialog = new RedPitayaTriggerDialog(this, config, channel);
            dialog.setVisible(true);
            if (dialog.accepted()) {
                double triggerLevel = dialog.triggerLevel();
                rpTriggerCombo.setSelectedItem(dialog.triggerSource());
                rpTriggerLevelSpinner.setValue(triggerLevel);
                RedPitayaConfig updatedConfig = readRedPitayaConfig();
                saveRedPitayaConfig(updatedConfig);
                status.setText(String.format(
                        Locale.US,
                        "IN%d trigger set to %.9f V (%s)",
                        channel,
                        triggerLevel,
                        dialog.triggerSource()
                ));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Red Pitaya trigger", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleVoltageChange() {
        if (rpVoltageKvField == null) return;
        try {
            String text = rpVoltageKvField.getText().trim().replace(',', '.');
            if (!text.isEmpty()) {
                Double.parseDouble(text); // tylko testujemy czy parsuje się poprawnie, jeśli nie, złapie exception
            }
        } catch (Exception ex) {
            // Ignorujemy błędy parsowania podczas wpisywania (np. ktoś wpisał "12.")
        }
        updateOutputPreview();
    }

    private File getOutputBaseDirectory() {
        if (rpOutputDirField != null && !rpOutputDirField.getText().trim().isEmpty()) {
            return new File(rpOutputDirField.getText().trim());
        }
        return receivedSignalsDir;
    }

    private void updateOutputPreview() {
        String defect = rpDefectTypeCombo != null ? (String) rpDefectTypeCombo.getSelectedItem() : "unknown";
        if (defect == null || defect.isBlank()) defect = "unknown";
        String rawLabel = rpSessionLabelField != null ? rpSessionLabelField.getText() : "";
        String label = redpitaya.RedPitayaFileWriter.sanitizeForFilename(rawLabel);

        String dirStr;
        String fileBase;

        double v = 0.0;
        if (rpVoltageKvField != null) {
            try {
                v = Double.parseDouble(rpVoltageKvField.getText().trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {}
        }
        String rawSensor = rpSensorCombo != null ? (String) rpSensorCombo.getSelectedItem() : "";
        String rawVariant = rpVariantCombo != null ? (String) rpVariantCombo.getSelectedItem() : "";
        
        String vStr = (v % 1 == 0) ? String.format(java.util.Locale.US, "%.0fkV", v) : String.format(java.util.Locale.US, "%.1fkV", v);
        
        StringBuilder extrasBuilder = new StringBuilder(vStr);
        if (rawSensor != null && !rawSensor.isBlank() && !"<None>".equals(rawSensor)) {
            extrasBuilder.append("_").append(redpitaya.RedPitayaFileWriter.sanitizeForFilename(rawSensor));
        }
        if (rawVariant != null && !rawVariant.isBlank() && !"<None>".equals(rawVariant)) {
            extrasBuilder.append("_").append(redpitaya.RedPitayaFileWriter.sanitizeForFilename(rawVariant));
        }
        String extras = extrasBuilder.toString();

        File baseDir = getOutputBaseDirectory();
        boolean isDefault = baseDir.getAbsolutePath().equals(receivedSignalsDir.getAbsolutePath());
        String displayBase = isDefault 
                ? "..." + File.separator + "PRPDtool" + File.separator + "data" + File.separator + "received_bin" 
                : baseDir.getAbsolutePath();

        if ("<None / Custom>".equals(defect)) {
            dirStr = displayBase + File.separator;
            String prefix = label.isEmpty() ? "rp" : label;
            fileBase = prefix + "_" + extras + "_[TIME].rppr.bin";
        } else {
            String cleanDefect = redpitaya.RedPitayaFileWriter.sanitizeForFilename(defect);
            dirStr = displayBase + File.separator + "sessions" + File.separator + cleanDefect + File.separator;
            fileBase = label.isEmpty()
                    ? cleanDefect + "_" + extras + "_[TIME].rppr.bin"
                    : cleanDefect + "_" + extras + "_" + label + "_[TIME].rppr.bin";
        }

        if (outputDirLabel != null) outputDirLabel.setText(dirStr);
        if (outputFileLabel != null) outputFileLabel.setText(fileBase);
        if (dialogPreviewDirLabel != null) dialogPreviewDirLabel.setText(dirStr);
        if (dialogPreviewFileLabel != null) dialogPreviewFileLabel.setText(fileBase);
    }

    // ------------- Misc. helpers
    //
    private double roundme(double d, int n) {
        double b = Math.pow(10, n - 1);
        double s = Math.signum(d);
        d = Math.abs(d);
        int p = 0;
        while (d < b) {
            d *= 10;
            p++;
        }
        int i = (int) Math.floor(d) + 1;
        return s * i / Math.pow(10, p);
    }

    private javax.swing.SwingWorker<Void, Void> rescaleWorker = null;

    private void rescaleHistogram() {
        if (rescaleWorker != null && !rescaleWorker.isDone()) {
            rescaleWorker.cancel(true);
        }

        recreateHistogram();
        
        if (applyButton != null) {
            applyButton.setText("Working...");
            applyButton.setEnabled(false);
        }

        rescaleWorker = new javax.swing.SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (!realTimeData && lastDataFile != null) {
                    try {
                        getData(lastDataFile);
                    } catch (Exception ex) {
                        javax.swing.SwingUtilities.invokeLater(() -> status.setText(ex.getMessage()));
                    }
                } else {
                    rebuildHistogramFromCachedSignal();
                }
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                
                if (realTimeData || lastDataFile == null) {
                    if (autoscaleCb != null && autoscaleCb.isSelected()) {
                        applyAutoscale();
                    }
                }
                
                if (applyButton != null) {
                    applyButton.setBackground(UIManager.getColor("Button.background"));
                    applyButton.setText("APPLY");
                    applyButton.setEnabled(true);
                }
                if (paramChange != null) paramChange.setText(" ");
                if (center != null) center.repaint();
            }
        };
        rescaleWorker.execute();
    }

    private void recreateHistogram() {
        double min = bipolarHistogram ? (ampMin == 0 ? -ampMax : ampMin) : ampMin;
        histogram = new DynamicPRPDHistogram(
                center.getWidth(), center.getHeight(),
                360, 200,
                min, ampMax,
                bipolarHistogram
        );
        histogram.drawF0(drawF0);
        ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
        ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
        if (showRawDataCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
        }
        if (showImpulseBarsCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowImpulseBars(showImpulseBarsCb.isSelected());
        }
        ((DynamicPRPDHistogram) histogram).setPalette(currentPalette);
        center.setHistogram((DynamicPRPDHistogram) histogram);
        if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
        center.revalidate();
    }

    private void rebuildHistogramFromCachedSignal() {
        List<CachedBuffer> snapshot = receivedSignalCache.snapshot();
        if (snapshot.isEmpty()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                center.repaint();
                status.setText("No cached signal to refresh PRPD.");
            });
            return;
        }

        PRPDExtractorCore refreshExtractor = new PRPDExtractorCore(
                f0,
                t0,
                threshold,
                deadUs,
                new HighPassFilter(fs, cutF, filterQ, filterOrder)
        );
        boolean useHwRef = useHwPhaseRefCb != null && useHwPhaseRefCb.isSelected();
        for (int i = 0; i < snapshot.size(); i++) {
            CachedBuffer cached = snapshot.get(i);
            Buffer buffer = new Buffer(cached.t, cached.u, cached.u2, cached.used, false);
            buffer.resetForUse(1);
            buffer.setUsed(cached.used);
            if (i == 0) {
                double ph0 = PhaseEstimator.estimateIntialPhase(buffer, f0, useHwRef);
                if (!Double.isNaN(ph0)) {
                    double estt0 = ph0 / (2 * Math.PI * f0);
                    if (estt0 < 0.5 / fs) {
                        estt0 = 0.0;
                    }
                    t0 = estt0;
                    refreshExtractor.setT0(t0);
                }
            }
            Pulses pulses = refreshExtractor.extract(buffer);
            if (pulses.n > 0) {
                histogram.addPulses(pulses);
            }
            if (pulses.fs > 0.0 && Double.isFinite(pulses.fs)) {
                dfs = pulses.fs;
            }
        }
        if (Math.abs((dfs - fs) / fs) > 1e-8) {
            fs = dfs;
            javax.swing.SwingUtilities.invokeLater(() -> setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs)));
        }
        if (histogram instanceof pipeline.DynamicPRPDHistogram) {
            ((pipeline.DynamicPRPDHistogram) histogram).forceRedraw();
        }
        javax.swing.SwingUtilities.invokeLater(() -> center.repaint());
    }

    private String getParamField(String key) {
        for (Param p : params) {
            if (p.name.equals(key)) {
                return p.field.getText();
            }
        }
        throw new IllegalArgumentException("Undefined parameter key \"" + key + "\"");
    }

    private void setParamField(String key, String text) {
        for (Param p : params) {
            if (p.name.equals(key)) {
                p.field.setText(text);
                p.field.repaint();
                return;
            }
        }
        throw new IllegalArgumentException("Undefined parameter key \"" + key + "\"");
    }

    // Helper -sets font
    private void setCurrentFont() {
        setFontRecursively(this, currentFont, 0);
        if (rpSettingsDialog != null) {
            Font largerFont = currentFont.deriveFont(currentFont.getSize2D() + 2.0f);
            setFontRecursively(rpSettingsDialog, largerFont, 0);
            rpSettingsDialog.pack();
        }
        updateRedPitayaEstimateFont();
        UIManager.put("OptionPane.messageFont", currentFont);
        UIManager.put("OptionPane.buttonFont", currentFont);
    }

    private void setFontRecursively(Component comp, Font font, int d) {
        if (comp == null) {
            return;
        }
        comp.setFont(font);
        //
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                setFontRecursively(child, font, d + 1);
            }
        }
        // Needs specific navigation, since JMenu does not show menu components as Components
        if (comp instanceof JMenu menu) {
            for (int i = 0; i < menu.getItemCount(); i++) {
                setFontRecursively(menu.getItem(i), font, d + 1);
            }
        }
    }

    // Helper - retrieves the last used directory from the config file
    private String getLastUsedDirectory() {
        String lsd = configuration.getValue(LAST_DIR);
        if (lsd == null) {
            lsd = ".";
        }
        return lsd;
    }

    // Helper - saves the last used directory
    private void saveLastUsedDirectory(String directory) {
        try {
            configuration.saveValue(LAST_DIR, directory);
        } catch (IOException e) {
            //message.setText(e.getLocalizedMessage());
        }
    }

    // Record data
    private void onExit() {
        stopPipeline();
        try {
            if (pipeline != null) {
                pipeline.awaitFinished(1);
            }
        } catch (Exception ex) {

        } finally {
            System.exit(0);
        }
    }

    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {

                if (f.isDirectory()) {
                    return true;
                }

                String name = f.getName().toLowerCase();

                return !(name.endsWith(".png")
                        || name.endsWith(".jpg")
                        || name.endsWith(".jpeg"));
            }

            @Override
            public String getDescription() {
                return "Non-image files";
            }
        });
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            inBatchMode = false;
            realTimeData = false;
            File file = fileChooser.getSelectedFile();
            status.setText("Loading " + file.getName() + "...");
            
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    getData(file.getAbsolutePath());
                    return null;
                }
                
                @Override
                protected void done() {
                    try {
                        get();
                        saveLastUsedDirectory(file.getParentFile().getAbsolutePath());
                        dataSource.setText("file (" + file.getName() + ")");
                        setLastDataFile(file.getAbsolutePath());
                        status.setText("Loaded " + file.getName());
                    } catch (Exception ex) {
                        System.err.println("Bad file: " + file.getName() + " : " + ex.getMessage());
                        setLastDataFile(null);
                        status.setText("Error loading file.");
                    }
                }
            }.execute();
        }
    }

    private void onParameterChanged() {
        for (Param p : params) {
            p.setFromField();
        }
        try {
            if (pipeline != null) {
                pipeline.setThreshold(threshold);
            }
        } catch (Exception ex) {
            setParamField("Pulse ampl. threshold [mV]", "" + PRPDConstants.DEFAULT_THRESHOLD);
        }
        if (histogram instanceof DynamicPRPDHistogram) {
            ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
        }
        rescaleHistogram();
    }

    private void startRedPitaya(boolean live) {
        currentSessionFiles.clear();
        inBatchMode = false;
        realTimeData = true;
        try {
            RedPitayaConfig config = readRedPitayaConfig();
            saveRedPitayaConfig(config);
            getRedPitayaData(config, live);
            rpStopButton.setEnabled(true);
            dataSource.setText("Red Pitaya (" + config.host + ":" + config.port + ", " + (live ? "live" : "once") + ")");
        } catch (Exception ex) {
            realTimeData = false;
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Red Pitaya", JOptionPane.ERROR_MESSAGE);
            status.setText(ex.getMessage());
            setLastDataFile(null);
        }
    }

    private void getRedPitayaData(RedPitayaConfig config, boolean live) throws Exception {
        boolean tmp = realTimeData;
        realTimeData = false;
        stopPipeline();
        realTimeData = tmp;
        receivedSignalCache.reset();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {

        }
        classifyButton.setEnabled(false);
        for (Classifier c : cResults.keySet()) {
            cResults.get(c).setText("");
        }

        fs = config.sampleRate();
        dfs = fs;
        setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs));
        double tEnd = live ? Math.max(10.0, config.normalizedDurationS()) : config.normalizedDurationS();

        Filter hfFilter = new HighPassFilter(fs, cutF, filterQ, filterOrder);
        Filter signalPlotFilter = new HighPassFilter(fs, cutF, filterQ, filterOrder);
        Filter lfFilter = new LowPassFilter(fs, 10 * f0, filterQ, filterOrder);
        Filter passThrough = new Filter() {
            @Override
            public double[] filter(double[] signal) { return signal.clone(); }
            @Override
            public double[] filter(double[] signal, int n) {
                double[] o = new double[n];
                System.arraycopy(signal, 0, o, 0, n);
                return o;
            }
            @Override
            public void setFs(double fs) {}
        };
        Filter abs = new Filter() {
            @Override
            public double[] filter(double[] signal) {
                double[] o = signal.clone();
                for (int i = 0; i < o.length; i++) {
                    o[i] = Math.abs(o[i]);
                }
                return o;
            }

            @Override
            public double[] filter(double[] signal, int n) {
                double[] o = new double[n];
                for (int i = 0; i < n; i++) {
                    o[i] = Math.abs(signal[i]);
                }
                return o;
            }

            @Override
            public void setFs(double fs) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };

        DynamicPRPDHistogram oldHist = (histogram instanceof DynamicPRPDHistogram) ? (DynamicPRPDHistogram) histogram : null;

        histogram = new DynamicPRPDHistogram(
                center.getWidth(), center.getHeight(),
                360, 200,
                (bipolarHistogram ? -ampMax : 0), ampMax,
                bipolarHistogram
        );
        if (oldHist != null && (autoscaleCb == null || !autoscaleCb.isSelected())) {
            ((DynamicPRPDHistogram) histogram).copyViewFrom(oldHist);
        }
        histogram.drawF0(drawF0);
        ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
        ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
        if (showRawDataCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
        }
        if (showImpulseBarsCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowImpulseBars(showImpulseBarsCb.isSelected());
        }
        ((DynamicPRPDHistogram) histogram).setPalette(currentPalette);
        center.setHistogram((DynamicPRPDHistogram) histogram);
        if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
        center.revalidate();

        interactiveSignalPanel.setShowIn2("IN1 + IN2".equals(rpVisualChannelCombo.getSelectedItem()));
        interactiveSignalPanel.reset(
                topPlotMode == 2 ? "Base signal" : (topPlotMode == 1 ? "Baseline signal" : "Signal envelope"),
                topPlotMode == 2 ? passThrough : (topPlotMode == 1 ? lfFilter : abs),
                "Filtered signal",
                signalPlotFilter,
                realTimeData
        );

        center.repaint();
        interactiveSignalPanel.repaint();

        extractor = new PRPDExtractorCore(
                f0,
                t0,
                threshold,
                deadUs,
                hfFilter
        );

        int bufferSize = BufferFactory.bufferSize();
        RedPitayaConfig readerConfig = config.copy();
        pipeline = new PRPDPipeline(
                "RedPitaya",
                () -> new RedPitayaSignalReader(
                        readerConfig,
                        live,
                        3,
                        bufferSize,
                        getOutputBaseDirectory().toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix
                ),
                3,
                bufferSize,
                2,
                extractor,
                createPipelineListener("Red Pitaya", "redpitaya",
                        useHwPhaseRefCb != null && useHwPhaseRefCb.isSelected())
        );

        pipeline.setOnReaderProgress(n
                -> SwingUtilities.invokeLater(() -> {
                    status.setText("Read " + n + " buffers.");
                })
        );

        setTitle("PRPD Viewer - Red Pitaya");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        signalStart.set(true);
        pipeline.start();
    }

    private void onRedPitayaCaptureSaved(Path path) {
        SwingUtilities.invokeLater(() -> {
            File savedFile = path.toFile();
            currentSessionFiles.add(savedFile);
            if (rpFileLimit > 0) {
                while (currentSessionFiles.size() > rpFileLimit) {
                    File toDelete = currentSessionFiles.poll();
                    if (toDelete != null && toDelete.exists()) {
                        toDelete.delete();
                    }
                }
            }
            refreshReceivedSignals();
            status.setText("Saved Red Pitaya signal: " + path.getFileName());
        });
    }

    private File buildReceivedSignalRecordFile() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        return new File(receivedSignalsDir, "recorded_" + stamp + ".prpdtool.bin");
    }

    private PRPDPipelineListener createPipelineListener(String displayName, String exportName, boolean useHwRef) {
        return new PRPDPipelineListener() {
            private long lastPaintTime = 0;
            @Override
            public void preExtract(Buffer buffer) {
                if (signalStart.compareAndSet(true, false) || buffer.newWindow) {
                    double ph0 = PhaseEstimator.estimateIntialPhase(buffer, f0, useHwRef);
                    if (!Double.isNaN(ph0)) {
                        double estt0 = ph0 / (2 * Math.PI * f0);
                        if (estt0 < 0.5 / fs) {
                            estt0 = 0.0;
                        }
                        t0 = estt0;
                        extractor.setT0(t0);
                    }
                    SwingUtilities.invokeLater(() -> {
                        setParamField("Zero-crossing instant [s]", "" + t0);
                        classifyButton.setEnabled(true);
                    });
                }
            }
            
            @Override
            public void bufferRead(Buffer buffer) {
                if (buffer.newWindow) {
                    receivedSignalCache.reset();
                    interactiveSignalPanel.clearData();
                }
                receivedSignalCache.add(buffer);
                interactiveSignalPanel.addBuffer(buffer);
            }

            @Override
            public void pulsesReady(Pulses pulses) {
                if (pulses.fs != fs) {
                    dfs = pulses.fs;
                }
                if (Math.abs((dfs - fs) / fs) > 1e-8) {
                    fs = dfs;
                    setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs));
                    JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "The sampling frequency estimated from data is " + String.format(Locale.US, "%.12g", fs) + " Hz",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                if (pulses.newWindow) {
                    histogram.clearPulses();
                }
                if (pulses.size > 0) {
                    histogram.addPulses(pulses);
                }
                
                long now = System.currentTimeMillis();
                if (now - lastPaintTime > 100) {
                    if (autoscaleCb.isSelected()) {
                        applyAutoscale();
                    } else {
                        if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                            ((pipeline.DynamicPRPDHistogram) histogram).forceRedraw();
                        }
                        center.setImage(histogram.getImage());
                    }
                    center.repaint();
                    if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                        status.setText(String.format(Locale.US, "Streaming Red Pitaya... [Pulses: %,d]", ((pipeline.DynamicPRPDHistogram) histogram).getSize()));
                    }
                    lastPaintTime = System.currentTimeMillis();
                }
            }

            @Override
            public void finished() {
                // Ensure final redraw completes
                if (autoscaleCb.isSelected()) {
                    applyAutoscale();
                } else {
                    if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                        ((pipeline.DynamicPRPDHistogram) histogram).forceRedraw();
                    }
                    center.setImage(histogram.getImage());
                }
                center.repaint();
                if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                    status.setText(String.format(Locale.US, "Acquisition finished [%s] | Total pulses: %,d", displayName, ((pipeline.DynamicPRPDHistogram) histogram).getSize()));
                }

                if (inBatchMode) {
                    exportPRPD4YOLO(exportName);
                    System.out.println("..." + exportName + " finished.");
                }
                setTitle("PRPD Viewer: " + displayName);
                setCursor(Cursor.getDefaultCursor());
                classifyButton.setEnabled(true);
                if (realTimeData && rpStopButton != null) {
                                    rpStopButton.setEnabled(false);
                    realTimeData = false;
                }
            }

            @Override
            public void error(Throwable ex, String msg) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        PRPDTool.this,
                        ex.getMessage() + msg,
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                if (realTimeData) {
                            if (rpStopButton != null) {
                        rpStopButton.setEnabled(false);
                    }
                    realTimeData = false;
                }
            }
        };
    }

    private void getData(String filename) throws Exception {
        boolean tmp = realTimeData;
        realTimeData = false; // patch - to be corrected!
        stopPipeline();
        realTimeData = tmp;
        receivedSignalCache.reset();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {

        }
        SwingUtilities.invokeLater(() -> {
            classifyButton.setEnabled(false);
            for (Classifier c : cResults.keySet()) {
                cResults.get(c).setText("");
            }
        });

        double tEnd;
        if (realTimeData) {
            tEnd = 10.0;
        } else if (RpprFileSignalReader.isRpprFile(filename)) {
            tEnd = 10.0;
        } else {
            String[] last;
            if (filename.endsWith(".csv")) {
                last = prpdtool.Utils.readLastLineUtf8(filename).trim().split("[,;\\s]+");
            } else {
                last = prpdtool.Utils.readLastPair(filename).trim().split("[,;\\s]+");
            }
            tEnd = Double.parseDouble(last[0]);
        }

        if (RpprFileSignalReader.isRpprFile(filename)) {
            fs = RpprFileSignalReader.sampleRate(filename, fs);
            SwingUtilities.invokeLater(() -> setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs)));
        }
        dfs = fs;
        Filter hfFilter = new HighPassFilter(fs, cutF, filterQ, filterOrder); //???
        Filter signalPlotFilter = new HighPassFilter(fs, cutF, filterQ, filterOrder);
        Filter lfFilter = new LowPassFilter(fs, 10 * f0, filterQ, filterOrder);
        Filter passThrough = new Filter() {
            @Override
            public double[] filter(double[] signal) { return signal.clone(); }
            @Override
            public double[] filter(double[] signal, int n) {
                double[] o = new double[n];
                System.arraycopy(signal, 0, o, 0, n);
                return o;
            }
            @Override
            public void setFs(double fs) {}
        };
        Filter abs = new Filter() {
            @Override
            public double[] filter(double[] signal) {
                double[] o = signal.clone();
                for (int i = 0; i < o.length; i++) {
                    o[i] = Math.abs(o[i]);
                }
                return o;
            }

            @Override
            public double[] filter(double[] signal, int n) {
                double[] o = new double[n];
                for (int i = 0; i < n; i++) {
                    o[i] = Math.abs(signal[i]);
                }
                return o;
            }

            @Override
            public void setFs(double fs) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        };

        DynamicPRPDHistogram oldHist = (histogram instanceof DynamicPRPDHistogram) ? (DynamicPRPDHistogram) histogram : null;

        histogram = new DynamicPRPDHistogram(
                center.getWidth(), center.getHeight(),
                360, 200,
                (bipolarHistogram ? -ampMax : 0), ampMax,
                bipolarHistogram
        );
        if (oldHist != null && (autoscaleCb == null || !autoscaleCb.isSelected())) {
            ((DynamicPRPDHistogram) histogram).copyViewFrom(oldHist);
        }
        histogram.drawF0(drawF0);
        ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
        ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
        if (showRawDataCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
        }
        if (showImpulseBarsCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowImpulseBars(showImpulseBarsCb.isSelected());
        }
        ((DynamicPRPDHistogram) histogram).setPalette(currentPalette);
        SwingUtilities.invokeLater(() -> {
            center.setHistogram((DynamicPRPDHistogram) histogram);
            if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
            center.revalidate();

            interactiveSignalPanel.setShowIn2("IN1 + IN2".equals(rpVisualChannelCombo.getSelectedItem()));
            interactiveSignalPanel.reset(
                    topPlotMode == 2 ? "Base signal" : (topPlotMode == 1 ? "Baseline signal" : "Signal envelope"),
                    topPlotMode == 2 ? passThrough : (topPlotMode == 1 ? lfFilter : abs),
                    "Filtered signal",
                    signalPlotFilter,
                    realTimeData
            );

            center.repaint();
            interactiveSignalPanel.repaint();
        });

        extractor = new PRPDExtractorCore(
                f0,
                t0,
                threshold,
                deadUs,
                hfFilter
        );

        int buffer_size = BufferFactory.bufferSize();

        boolean useHwRefSnapshot = (useHwPhaseRefCb != null && useHwPhaseRefCb.isSelected());

        pipeline = new PRPDPipeline(
                filename,
                3, // 3 konsumentów: extractor, envelope, signal
                buffer_size,
                2,
                extractor,
                new PRPDPipelineListener() {
            private long lastPaintTime = 0;
            @Override
            public void preExtract(Buffer buffer) {
                if (signalStart.compareAndSet(true, false) || buffer.newWindow) {
                    double ph0 = PhaseEstimator.estimateIntialPhase(buffer, f0, useHwRefSnapshot);
                    if (!Double.isNaN(ph0)) {
                        double estt0 = ph0 / (2 * Math.PI * f0);
                        if (estt0 < 0.5 / fs) {
                            estt0 = 0.0;
                        }
                        t0 = estt0;
                        extractor.setT0(t0);
                    }
                    SwingUtilities.invokeLater(() -> {
                        setParamField("Zero-crossing instant [s]", "" + t0);
                        classifyButton.setEnabled(true);
                    });
                }
            }

            @Override
            public void bufferRead(Buffer buffer) {
                if (buffer.newWindow) {
                    receivedSignalCache.reset();
                    interactiveSignalPanel.clearData();
                }
                receivedSignalCache.add(buffer);
                interactiveSignalPanel.addBuffer(buffer);
            }

            @Override
            public void pulsesReady(Pulses pulses) {
                if (pulses.fs != fs) {
                    dfs = pulses.fs;
                }
                if (Math.abs((dfs - fs) / fs) > 1e-8) {
                    fs = dfs;
                    setParamField("Sampling frequency [Hz]", String.format(Locale.US, "%.12g", fs));
                    JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "The sampling frequency estimated from data is " + String.format(Locale.US, "%.12g", fs) + " Hz",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                if (pulses.newWindow) {
                    histogram.clearPulses();
                }
                if (pulses.size > 0) {
                    histogram.addPulses(pulses);
                }
                
                long now = System.currentTimeMillis();
                if (now - lastPaintTime > 100) {
                    if (autoscaleCb.isSelected()) {
                        applyAutoscale();
                    } else {
                        if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                            ((pipeline.DynamicPRPDHistogram) histogram).forceRedraw();
                        }
                        center.setImage(histogram.getImage());
                    }
                    center.repaint();
                    if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                        status.setText(String.format(Locale.US, "Streaming data... [Pulses: %,d]", ((pipeline.DynamicPRPDHistogram) histogram).getSize()));
                    }
                    lastPaintTime = System.currentTimeMillis();
                }
            }

            @Override
            public void finished() {
                // Ensure final redraw completes
                if (autoscaleCb.isSelected()) {
                    applyAutoscale();
                } else {
                    if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                        ((pipeline.DynamicPRPDHistogram) histogram).forceRedraw();
                    }
                    center.setImage(histogram.getImage());
                }
                center.repaint();
                if (histogram instanceof pipeline.DynamicPRPDHistogram) {
                    status.setText(String.format(Locale.US, "Playback finished [%s] | Total pulses: %,d", filename, ((pipeline.DynamicPRPDHistogram) histogram).getSize()));
                }

                if (inBatchMode) {
                    exportPRPD4YOLO(filename);
                    System.out.println("..." + filename + " finished.");
                }
                setTitle("PRPD Viewer: " + Paths.get(filename).getFileName().toString());
                setCursor(Cursor.getDefaultCursor());
                classifyButton.setEnabled(true);
            }

            @Override
            public void error(Throwable ex, String msg) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        PRPDTool.this,
                        ex.getMessage() + msg,
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                if (realTimeData) {
                            realTimeData = false;
                }
            }
        }
        );

        pipeline.setOnReaderProgress(n
                -> SwingUtilities.invokeLater(() -> {
                    status.setText("Read " + n + " buffers.");
                })
        );

        setTitle("PRPD Viewer - " + Paths.get(filename).getFileName().toString());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        signalStart.set(true);
        pipeline.start();
    }

    private void stopPipeline() {
        if (pipeline != null) {
            pipeline.close();
            BufferFactory.reset();
            pipeline = null;
            if (realTimeData) {
                    if (rpStopButton != null) {
                    rpStopButton.setEnabled(false);
                }
                realTimeData = false;
            }
        }
    }

    private void exportHistogram() {
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                int[][] h = histogram.getHistogram();
                PrintWriter w = new PrintWriter(new FileWriter(fileChooser.getSelectedFile()));
                w.println(histogram.getMin() + "," + histogram.getMax());
                for (int[] row : h) {
                    for (int j = 0; j < row.length - 1; j++) {
                        w.print(row[j] + ",");
                    }
                    w.println(row[row.length - 1]);
                }
                status.setText("histogram saved to " + fileChooser.getSelectedFile().getName());
            } catch (IOException ex) {
                status.setText(ex.getMessage());
            }
        }
    }

    private void exportImage() {
        BufferedImage img = histogram.getImage();
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) {
                file = new File(path + ".png");
            }
            try {
                ImageIO.write(img, "png", file);
                status.setText("image saved to " + fileChooser.getSelectedFile().getName());
            } catch (IOException ex) {
                status.setText(ex.getMessage());
            }
        }
    }

    private void exportRawPulsesCSV() {
        if (histogram instanceof pipeline.DynamicPRPDHistogram dynHist) {
            int size = dynHist.getSize();
            if (size == 0) {
                status.setText("No pulses to export");
                return;
            }

            JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
            setFontRecursively(fileChooser, currentFont, 0);
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                String path = file.getAbsolutePath();
                if (!path.toLowerCase().endsWith(".csv")) {
                    file = new File(path + ".csv");
                }
                
                try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
                    double[] times = dynHist.getTimes();
                    double[] phases = dynHist.getPhases();
                    double[] amps = dynHist.getAmps();
                    
                    w.println("Time [s],Phase [deg],Amplitude [V]");
                    for (int i = 0; i < size; i++) {
                        w.println(String.format(Locale.US, "%g,%.2f,%g", times[i], phases[i], amps[i]));
                    }
                    status.setText("Raw pulses exported to " + file.getName());
                } catch (IOException ex) {
                    status.setText(ex.getMessage());
                }
            }
        } else {
            status.setText("Exporting raw pulses is not supported for this histogram type");
        }
    }

    private void exportPRPD4YOLODialog() {
        if (histogram == null) return;
        
        prpd4YOLO = histogram.getPRPD(224, 224);
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        
        if (lastDataFile != null) {
            String defaultName = new File(lastDataFile).getName().replaceAll("\\..*$", ".png");
            fileChooser.setSelectedFile(new File(getLastUsedDirectory(), defaultName));
        }

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String path = file.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) {
                file = new File(path + ".png");
            }
            try {
                javax.imageio.ImageIO.write(prpd4YOLO, "png", file);
                status.setText("YOLO IMG saved to " + file.getName());
            } catch (Exception ex) {
                ex.printStackTrace();
                status.setText("Failed to save YOLO image: " + ex.getMessage());
            }
        }
    }

    private void exportPRPD4YOLO(String fileName) {
        if (histogram != null) {
            prpd4YOLO = histogram.getPRPD(224, 224);
            try {
                String o = fileName.replaceAll("\\..*$", ".png");
                ImageIO.write(prpd4YOLO, "png", new File(o));
                status.setText("YOLO IMG:" + o);
            } catch (IOException ex) {
                ex.printStackTrace();
                status.setText(ex.getMessage());
            }
        }
    }

    private void classifyPRPD(Map<Classifier, JLabel> map) {
        if (histogram != null) {
            prpd4YOLO = histogram.getPRPD(448, 448);
            for (Classifier c : map.keySet()) {
                classifyPRPD(c, map.get(c));
            }
        }
    }

    private void classifyPRPD(Classifier classifier, JLabel resultView) {
        if (prpd4YOLO != null) {
            histogram.clearLabels();
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            resultView.setText("..working...");
            resultView.repaint();
            SwingUtilities.invokeLater(() -> {
                try {
                    ImageIO.write(prpd4YOLO, "png", new File("prpd4YOLO.png"));
                    Prediction[] result = classifier.classify(prpd4YOLO);
                    System.out.println(classifier.name() + ":");
                    resultView.setBackground(UIManager.getColor("Label.background"));
                    resultView.setText("");
                    String sep = "";
                    for (Prediction p : result) {
                        resultView.setText(resultView.getText() + sep + p.toString());
                        sep = ",";
                        if (p.box != null) {
                            float[] b = p.box;
                            histogram.addLabel(b[0], b[1], b[2], b[3], p.toString(), PRPDConstants.CLASS_COLORS.get(p.className()));
                        }
                    }
                    if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
                    center.revalidate();
                    center.repaint();
                    resultView.repaint();
                    setCursor(Cursor.getDefaultCursor());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        }
    }

    private void dir2prpd() {
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File dir = fileChooser.getSelectedFile();
            if (dir.isDirectory()) {
                processDirectoryBatch(dir);
            }
        }
    }

    private void processDirectoryBatch(File dir) {
        String[] files = Arrays.stream(dir.list((d, name) -> {
            File f = new File(d, name);
            return f.isFile() && name.toLowerCase().endsWith(".bin");
        })).sorted()
                .toArray(String[]::new);

        if (files == null) {
            return;
        }

        Thread runner = new Thread(() -> {
            inBatchMode = true;

            for (String s : files) {
                try {
                    while (pipeline != null && pipeline.isRunning()) {
                        Thread.sleep(250);
                    }

                    String ds = dir.getAbsolutePath() + File.separator + s;
                    System.out.println("Processing " + ds);

                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            System.out.println(" ...started");
                            getData(ds);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    pipeline.awaitFinished();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            inBatchMode = false;
        }, "PRPD-batch-thread");
        runner.start();
    }

    private void applyAutoscale() {
        if (histogram instanceof DynamicPRPDHistogram) {
            DynamicPRPDHistogram dh = (DynamicPRPDHistogram) histogram;
            dh.autoscale();
            center.setImage(dh.getImage());
        }
    }

    private void saveAllGraphsToPng() {
        if (center == null || interactiveSignalPanel == null) {
            javax.swing.JOptionPane.showMessageDialog(this, "Panels are not initialized.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        String defName = center.getDefaultExportName();
        if (defName == null || defName.isEmpty()) defName = "PRPD";
        fc.setSelectedFile(new java.io.File(defName + ".png"));
        fc.setDialogTitle("Save All Graphs as PNG");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));
        if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new java.io.File(file.getAbsolutePath() + ".png");
            }
            try {
                java.awt.image.BufferedImage img1 = center.getRenderedImage();
                java.awt.image.BufferedImage img2 = interactiveSignalPanel.getRenderedImage();
                
                int cropBottom = 5;
                if (img1.getHeight() > cropBottom + 10) {
                    img1 = img1.getSubimage(0, 0, img1.getWidth(), img1.getHeight() - cropBottom);
                }
                
                int targetWidth = Math.max(img1.getWidth(), img2 != null ? img2.getWidth() : 0);
                
                int h1 = img1.getHeight() * targetWidth / Math.max(1, img1.getWidth());
                int h2 = (img2 != null) ? img2.getHeight() * targetWidth / Math.max(1, img2.getWidth()) : 0;
                
                java.awt.image.BufferedImage combined = new java.awt.image.BufferedImage(targetWidth, h1 + h2, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2 = combined.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setColor(PRPDConstants.isDarkTheme() ? new java.awt.Color(40, 44, 52) : java.awt.Color.WHITE);
                g2.fillRect(0, 0, targetWidth, h1 + h2);
                g2.drawImage(img1, 0, 0, targetWidth, h1, null);
                if (img2 != null) g2.drawImage(img2, 0, h1, targetWidth, h2, null);
                g2.dispose();
                
                javax.imageio.ImageIO.write(combined, "PNG", file);
                javax.swing.JOptionPane.showMessageDialog(this, "All graphs saved successfully.", "Success", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            } catch (Throwable ex) {
                ex.printStackTrace();
                javax.swing.JOptionPane.showMessageDialog(this, "Failed to save image: " + ex, "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setLastDataFile(String filename) {
        this.lastDataFile = filename;
        updateExportNames(filename);
    }

    private void updateExportNames(String filename) {
        String name = "PRPD";
        if (filename != null) {
            name = new java.io.File(filename).getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            if (name.endsWith(".rppr") || name.endsWith(".prpdtool")) {
                int dot2 = name.lastIndexOf('.');
                if (dot2 > 0) name = name.substring(0, dot2);
            }
        }
        if (center != null) center.setDefaultExportName(name);
        if (interactiveSignalPanel != null) interactiveSignalPanel.setDefaultExportName(name);
    }

    public static void main(String[] args) {
        try {
            Configuration config = new Configuration(CONFIG_FILE);
            String darkModeStr = config.getValue(PRPDConstants.DARK_MODE);
            if ("true".equalsIgnoreCase(darkModeStr)) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize FlatLaf");
        }
        
        SwingUtilities.invokeLater(() -> {
            new PRPDTool().setVisible(true);
        });
    }
}
