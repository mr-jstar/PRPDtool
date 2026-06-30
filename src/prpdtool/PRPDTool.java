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
import pipeline.PRPDExtractorCore;
import pipeline.PRPDHistogram;
import pipeline.PRPDPipeline;
import pipeline.PRPDPipelineListener;
import pipeline.Pulses;
import redpitaya.RedPitayaConfig;
import redpitaya.RedPitayaSignalReader;
import redpitaya.RpprFileSignalReader;

public class PRPDTool extends JFrame {

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
    private final String DAQ = "PRPDMonitor.daq.address";
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
    private double ampMax = 0.12; // maximum histogramu
    private double deadUs = 0; //martwy czas po wykryciu impulsu [µs]
    private double filterQ = 0.707; // Q filtra
    private int filterOrder = 4; // rząd filtra

    private AtomicBoolean signalStart = new AtomicBoolean(false);

    Param<?>[] params = {
        Param.dbl("Basic frequency [Hz]", () -> f0, v -> f0 = v),
        Param.dbl("Zero-crossing instant", () -> t0, v -> t0 = v),
        Param.dbl("Sampling frequency [Hz]", () -> fs, v -> fs = v),
        Param.dbl("Pulse ampl. threshold", () -> threshold, v -> threshold = v),
        Param.dbl("Dead time [us]", () -> deadUs, v -> deadUs = v),
        Param.dbl("HPF cutoff frequency [Hz]", () -> cutF, v -> cutF = v),
        Param.dbl("Filter Q", () -> filterQ, v -> filterQ = v),
        Param.integer("Filter Order", () -> filterOrder, v -> filterOrder = v),
        Param.dbl("Histogram min", () -> ampMin, v -> ampMin = v),
        Param.dbl("Histogram max", () -> ampMax, v -> ampMax = v)
    };

    // Misc options
    private boolean drawF0 = true;
    private boolean bipolarHistogram;
    private boolean fastRendering = true;

    private int topPlotMode = 2; // 0 = Envelope, 1 = Baseline, 2 = Base signal (Raw)

    // Data
    private InteractiveSignalPanel interactiveSignalPanel;
    private static final int MAX_CACHED_SIGNAL_SAMPLES = 3_000_000;
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

    private JTextField dataServer;
    private JButton startBtn;
    private JButton stopBtn;
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
    private JComboBox<String> rpModeCombo;
    private JSpinner rpDurationSpinner;
    private JSpinner rpFrameSizeSpinner;
    private JSpinner rpFrameCountSpinner;
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
    private JButton startRecordButton;
    private JButton stopRecordButton;
    private FileChannel recordedData;
    private File recordedFile;
    private int recordLimit = 1024; // Maximal size of the registerd signal (in MB == 1 GB)
    private int recordedMB;
    private JLabel recordSizeLabel;

    private JButton classifyButton;
    private DefaultListModel<File> receivedSignalsModel;
    private JList<File> receivedSignalsList;

    private static final class CachedBuffer {

        final double[] t;
        final double[] u;
        final int used;

        CachedBuffer(double[] t, double[] u, int used) {
            this.t = t;
            this.u = u;
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
            buffers.add(new CachedBuffer(
                    Arrays.copyOf(buffer.t, n),
                    Arrays.copyOf(buffer.u, n),
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
        JMenuItem socketMI = new JMenuItem("Read (t,u) from socket");
        socketMI.addActionListener(e -> {
            openSocket();
        });
        fileM.add(socketMI);
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
        prpdMI.addActionListener(e -> exportPRPD4YOLO(lastDataFile));
        fileM.add(prpdMI);

        fileM.addSeparator();

        JMenuItem exitMI = new JMenuItem("Exit");
        exitMI.addActionListener(e -> onExit());
        fileM.add(exitMI);
        mb.add(fileM);

        JMenu scriptsM = new JMenu("Scripts");
        JMenuItem batchMI = new JMenuItem("Dir->PRPD");
        batchMI.addActionListener(e -> dir2prpd());
        scriptsM.add(batchMI);
        mb.add(scriptsM);

        JMenu profilesM = new JMenu("Profiles");
        buildProfilesMenu(profilesM);
        mb.add(profilesM);

        JMenu optM = new JMenu("Options");
        JMenuItem fontMI = new JMenuItem("Font size");
        optM.add(fontMI);
        ButtonGroup fgroup = new ButtonGroup();
        for (Font f : PRPDConstants.FONTS) {
            JRadioButtonMenuItem fontOpt = new JRadioButtonMenuItem("\t\t" + String.valueOf(f.getSize()));
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
            optM.add(fontOpt);
        }
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
                setParamField("Histogram min", "-" + roundme(histogram.getDataMax(), 3));
            } else {
                bipolarHistogram = false;
                setParamField("Histogram min", "0");
            }
            onParameterChanged();
        });
        optM.add(bipolarMB);

        JMenuItem ftHistMB = new JMenuItem("Fit histogram to data");
        ftHistMB.addActionListener(e -> {
            if (lastDataFile != null) {
                setParamField("Histogram min", "" + roundme(histogram.getDataMin(), 3));
                setParamField("Histogram max", "" + roundme(histogram.getDataMax(), 3));
                bipolarHistogram = bipolarMB.isSelected();
                onParameterChanged();
            }
        });
        optM.add(ftHistMB);
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
                SwingUtilities.updateComponentTreeUI(this);
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
                rpSettingsDialog.setVisible(true);
            }
        });
        rpMenu.add(rpSettingsMI);
        mb.add(rpMenu);

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

    private void buildProfilesMenu(JMenu profilesM) {
        profilesM.removeAll();
        
        JMenuItem saveProfileMI = new JMenuItem("Save current profile...");
        saveProfileMI.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter profile name:");
            if (name != null && !name.trim().isEmpty()) {
                File dir = new File("profiles");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File f = new File(dir, name.trim() + ".cfg");
                saveProfile(f);
                buildProfilesMenu(profilesM);
            }
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

    private void saveProfile(File file) {
        try {
            Configuration prof = new Configuration(file.getAbsolutePath());
            prof.saveValue(RP_HOST, rpHostField.getText());
            prof.saveValue(RP_PORT, rpPortSpinner.getValue().toString());
            prof.saveValue(RP_CHANNELS, rpChannelsCombo.getSelectedItem().toString());
            prof.saveValue(RP_VISUAL_CHANNEL, rpVisualChannelCombo.getSelectedItem().toString());
            prof.saveValue(RP_GAIN1, rpGain1Combo.getSelectedItem().toString());
            prof.saveValue(RP_GAIN2, rpGain2Combo.getSelectedItem().toString());
            prof.saveValue(RP_DECIMATION, rpDecimationSpinner.getValue().toString());
            prof.saveValue(RP_AVERAGING, Boolean.toString(rpAveragingBox.isSelected()));
            prof.saveValue(RP_TRIGGER_SOURCE, rpTriggerCombo.getSelectedItem().toString());
            prof.saveValue(RP_TRIGGER_LEVEL, rpTriggerLevelSpinner.getValue().toString());
            prof.saveValue(RP_TRIGGER_DELAY, rpTriggerDelaySpinner.getValue().toString());
            prof.saveValue(RP_TRIGGER_TIMEOUT, rpTriggerTimeoutSpinner.getValue().toString());
            prof.saveValue(RP_MODE, rpModeCombo.getSelectedItem().toString());
            prof.saveValue(RP_DURATION, rpDurationSpinner.getValue().toString());
            prof.saveValue(RP_FRAME_SIZE, rpFrameSizeSpinner.getValue().toString());
            prof.saveValue(RP_FRAME_COUNT, rpFrameCountSpinner.getValue().toString());

            for (Param<?> p : params) {
                prof.saveValue("Param." + p.name, p.getText());
            }

            status.setText("Profile saved to " + file.getName());
        } catch (IOException ex) {
            status.setText(ex.getMessage());
        }
    }

    private void loadProfile(File file) {
        Configuration prof = new Configuration(file.getAbsolutePath());
        
        try { String v = prof.getValue(RP_HOST); if(v != null) rpHostField.setText(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_PORT); if(v != null) rpPortSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_CHANNELS); if(v != null) rpChannelsCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_VISUAL_CHANNEL); if(v != null) rpVisualChannelCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_GAIN1); if(v != null) rpGain1Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_GAIN2); if(v != null) rpGain2Combo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_DECIMATION); if(v != null) rpDecimationSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_AVERAGING); if(v != null) rpAveragingBox.setSelected(Boolean.parseBoolean(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_SOURCE); if(v != null) rpTriggerCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_LEVEL); if(v != null) rpTriggerLevelSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_DELAY); if(v != null) rpTriggerDelaySpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_TRIGGER_TIMEOUT); if(v != null) rpTriggerTimeoutSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_MODE); if(v != null) rpModeCombo.setSelectedItem(v); } catch(Exception e) {}
        try { String v = prof.getValue(RP_DURATION); if(v != null) rpDurationSpinner.setValue(Double.parseDouble(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_FRAME_SIZE); if(v != null) rpFrameSizeSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}
        try { String v = prof.getValue(RP_FRAME_COUNT); if(v != null) rpFrameCountSpinner.setValue(Integer.parseInt(v)); } catch(Exception e) {}

        for (Param<?> p : params) {
            String v = prof.getValue("Param." + p.name);
            if (v != null) {
                p.setFromText(v);
                if (p.field != null) {
                    p.field.setText(p.getText());
                }
            }
        }
        
        onParameterChanged();
        status.setText("Profile loaded from " + file.getName());
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
            JPanel legacyPanel = initLegacySocketControls();

            JPanel recordPanel = new JPanel();
            recordPanel.setLayout(new GridLayout(0, 1, 5, 5));
            recordPanel.setBorder(BorderFactory.createTitledBorder("Signal recording"));
            recordPanel.add(new JLabel("Limit [MB]"));
            JTextField limitTF = new JTextField("" + recordLimit);
            limitTF.addActionListener(e -> {
                recordLimit = Integer.parseInt(limitTF.getText());
            });
            recordPanel.add(limitTF);
            
            recordSizeLabel = new JLabel("0 MB used");
            recordPanel.add(recordSizeLabel);

            JPanel recBtns = new JPanel(new GridLayout(1, 2, 3, 3));
            startRecordButton = new JButton("Start recording");
            startRecordButton.addActionListener(e -> {
                try {
                    Files.createDirectories(receivedSignalsDir.toPath());
                    recordedFile = buildReceivedSignalRecordFile();
                    recordedData = new FileOutputStream(recordedFile).getChannel();
                    recordedMB = 0;
                    stopRecordButton.setEnabled(true);
                    status.setText("Recording to " + recordedFile.getName());
                } catch (IOException ex) {
                    status.setText(ex.getMessage());
                }
            });
            startRecordButton.setEnabled(false);
            recBtns.add(startRecordButton);

            stopRecordButton = new JButton("Stop recording");
            stopRecordButton.setEnabled(false);
            stopRecordButton.addActionListener(e -> stopRecorder());
            recBtns.add(stopRecordButton);
            
            recordPanel.add(recBtns);

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
            center.setHistogram((DynamicPRPDHistogram) histogram);
            if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
            center.revalidate();

            interactiveSignalPanel = new InteractiveSignalPanel();
            interactiveSignalPanel.reset(
                    topPlotMode == 2 ? "Base signal" : (topPlotMode == 1 ? "Baseline signal" : "Signal envelope"),
                    null,
                    "Filtered signal",
                    null,
                    false
            );
            bottom.setLayout(new BorderLayout());
            bottom.add(createReceivedSignalsPanel(), BorderLayout.WEST);
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
                label.setToolTipText(tooltipText);
                
                JLabel help = new JLabel(new HelpIcon());
                help.setToolTipText(htmlTooltip(tooltipText));
                help.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                help.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                
                labelPanel.add(label, BorderLayout.CENTER);
                labelPanel.add(help, BorderLayout.EAST);

                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.setToolTipText(tooltipText);
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
                paramPanel.add(labelPanel);
                paramPanel.add(field);
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
            paramPanel.add(autoscaleCb);
            
            showRawDataCb = new JCheckBox("Show Raw Data", false);
            showRawDataCb.addActionListener(e -> {
                if (histogram instanceof DynamicPRPDHistogram) {
                    ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
                    center.setImage(histogram.getImage());
                }
            });
            paramPanel.add(showRawDataCb);

            center.setResetAction(() -> {
                autoscaleCb.setSelected(true);
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
            classifyPanel.setBorder(BorderFactory.createTitledBorder("Classification"));

            modelPanel = new JPanel();
            cResults = new HashMap<>();
            updateModelPanel();

            classifyPanel.add(modelPanel, BorderLayout.CENTER);

            classifyButton = new JButton("CLASIFY");
            classifyButton.addActionListener(e -> classifyPRPD(cResults));
            classifyButton.setEnabled(false);
            classifyPanel.add(classifyButton, BorderLayout.SOUTH);

            setFontRecursively(classifyPanel, currentFont, 0);

            left.add(paramPanel);
            left.add(rpPanel);
            left.add(legacyPanel);
            left.add(recordPanel);
            left.add(classifyPanel);
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

    private JPanel initLegacySocketControls() {
        try {
            String sp = configuration.getValue(DAQ).trim();
            if (sp != null) {
                serverPort = sp;
            }
        } catch (Exception ex) {

        }

        JPanel legacyPanel = new JPanel(new GridLayout(0, 1, 3, 3));
        legacyPanel.setBorder(BorderFactory.createTitledBorder("Legacy t,u socket"));
        legacyPanel.add(new JLabel("DAQ Server host:port"));
        dataServer = new JTextField(serverPort);
        dataServer.addActionListener(e -> {
            try {
                configuration.saveValue(DAQ, dataServer.getText());
            } catch (IOException ex) {
            }
        });
        legacyPanel.add(dataServer);
        JPanel btns = new JPanel(new GridLayout(1, 2, 3, 3));
        startBtn = new JButton("Start DAQ aquisition");
        startBtn.addActionListener(e -> openSocket());
        btns.add(startBtn);

        stopBtn = new JButton("Stop DAQ aquisition");
        stopBtn.addActionListener(e -> closeSocket());
        stopBtn.setEnabled(false);
        btns.add(stopBtn);
        legacyPanel.add(btns);
        return legacyPanel;
    }

    private JPanel createReceivedSignalsPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Received signals"));
        panel.setPreferredSize(new Dimension(330, 1));
        panel.setMinimumSize(new Dimension(260, 1));
        
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
        if (!receivedSignalsDir.exists() || rpFileLimit <= 0) return;

        File[] files = receivedSignalsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.US);
            return lower.startsWith("rp_") && lower.endsWith(".rppr.bin");
        });

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
        if (!receivedSignalsDir.exists()) {
            receivedSignalsDir.mkdirs();
        }
        File[] files = receivedSignalsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.US);
            return lower.endsWith(".rppr.bin")
                    || lower.endsWith(".prpdtool.bin")
                    || (lower.endsWith(".bin") && !lower.startsWith("."));
        });
        if (files == null) {
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            if (file.isFile()) {
                receivedSignalsModel.addElement(file);
            }
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
            lastDataFile = filename;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Load received signal", JOptionPane.ERROR_MESSAGE);
            status.setText(ex.getMessage());
            lastDataFile = null;
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
        JPanel rpPanel = new JPanel(new BorderLayout(4, 4));
        rpPanel.setBorder(BorderFactory.createTitledBorder("Red Pitaya"));
        JPanel formPanel = new JPanel(new GridLayout(0, 2, 3, 3));

        rpHostField = new JTextField(configValue(RP_HOST, "rp-f0f84e.local"));
        rpPortSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_PORT, 9999), 1, 65535, 1));
        rpChannelsCombo = new JComboBox<>(new String[]{"IN1", "IN2", "IN1+IN2"});
        rpChannelsCombo.setSelectedItem(configValue(RP_CHANNELS, "IN1"));
        rpVisualChannelCombo = new JComboBox<>(new String[]{"IN1", "IN2"});
        rpVisualChannelCombo.setSelectedItem(configValue(RP_VISUAL_CHANNEL, "IN1"));
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
        rpDurationSpinner = new JSpinner(new SpinnerNumberModel(configDouble(RP_DURATION, 0.01), 0.000001, 3600.0, 0.000001));
        rpFrameSizeSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_FRAME_SIZE, 65_536), 2, BufferFactory.bufferSize(), 2));
        rpFrameCountSpinner = new JSpinner(new SpinnerNumberModel(configInt(RP_FRAME_COUNT, 1), 1, Integer.MAX_VALUE, 1));
        rpEstimatedSizeLabel = new JLabel(" ");
        rpEstimatedSizeLabel.setForeground(Color.DARK_GRAY);
        rpSettingsEstimatedSizeLabel = new JLabel(" ");
        rpSettingsEstimatedSizeLabel.setForeground(Color.DARK_GRAY);

        setIntegerSpinnerEditor(rpPortSpinner);
        setIntegerSpinnerEditor(rpDecimationSpinner);
        setDecimalSpinnerEditor(rpTriggerLevelSpinner);
        setIntegerSpinnerEditor(rpTriggerDelaySpinner);
        setDecimalSpinnerEditor(rpTriggerTimeoutSpinner);
        setDecimalSpinnerEditor(rpDurationSpinner);
        setIntegerSpinnerEditor(rpFrameSizeSpinner);
        setIntegerSpinnerEditor(rpFrameCountSpinner);

        addFormRow(formPanel, "Host", rpHostField, helpText("host"));
        addFormRow(formPanel, "Port", rpPortSpinner, helpText("port"));
        addFormRow(formPanel, "Channels", rpChannelsCombo, helpText("channels"));
        addFormRow(formPanel, "Visual", rpVisualChannelCombo, helpText("visual"));
        addFormRow(formPanel, "IN1 range", rpGain1Combo, helpText("gain1"));
        addFormRow(formPanel, "IN2 range", rpGain2Combo, helpText("gain2"));
        addFormRow(formPanel, "Decimation", rpDecimationSpinner, helpText("decimation"));
        addFormRow(formPanel, "Averaging", rpAveragingBox, helpText("averaging"));
        addFormRow(formPanel, "Trigger", rpTriggerCombo, helpText("trigger"));
        addFormRow(formPanel, "Trigger [V]", rpTriggerLevelSpinner, helpText("triggerLevel"));
        addFormRow(formPanel, "Delay [samples]", rpTriggerDelaySpinner, helpText("triggerDelay"));
        addFormRow(formPanel, "Timeout [s]", rpTriggerTimeoutSpinner, helpText("triggerTimeout"));
        addFormRow(formPanel, "Mode", rpModeCombo, helpText("mode"));
        addFormRow(formPanel, "Duration [s]", rpDurationSpinner, helpText("duration"));
        addFormRow(formPanel, "Frame size", rpFrameSizeSpinner, helpText("frameSize"));
        addFormRow(formPanel, "Frame count", rpFrameCountSpinner, helpText("frameCount"));
        updateRedPitayaEstimateFont();

        rpTriggerIn1Button = new JButton("Auto trigger IN1");
        rpTriggerIn1Button.setToolTipText(htmlTooltip(helpText("autoTriggerIn1")));
        rpTriggerIn1Button.addActionListener(e -> openTriggerDialog(1));
        rpTriggerIn2Button = new JButton("Auto trigger IN2");
        rpTriggerIn2Button.setToolTipText(htmlTooltip(helpText("autoTriggerIn2")));
        rpTriggerIn2Button.addActionListener(e -> openTriggerDialog(2));
        rpStartOnceButton = new JButton("Start once");
        rpStartOnceButton.setToolTipText(htmlTooltip(helpText("startOnce")));
        rpStartOnceButton.addActionListener(e -> startRedPitaya(false));
        rpStartLiveButton = new JButton("Start live");
        rpStartLiveButton.setToolTipText(htmlTooltip(helpText("startLive")));
        rpStartLiveButton.addActionListener(e -> startRedPitaya(true));
        rpStopButton = new JButton("Stop RP");
        rpStopButton.setToolTipText(htmlTooltip(helpText("stopRp")));
        rpStopButton.addActionListener(e -> closeSocket());
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

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        actions.add(rpStartOnceButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        actions.add(rpStartLiveButton, gbc);
        gbc.gridx = 1;
        actions.add(rpStopButton, gbc);

        JPanel fileConfigPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        fileConfigPanel.add(new JLabel("Prefix"));
        JLabel prefixHelp = new JLabel(new HelpIcon());
        prefixHelp.setToolTipText(htmlTooltip("Enter a custom prefix for the files. It will be used to generate file names in the format: [PREFIX]_[DATE_TIME].rppr.bin"));
        prefixHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        prefixHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        fileConfigPanel.add(prefixHelp);

        rpFilePrefixField = new JTextField(rpFilePrefix, 10);
        rpFilePrefixField.addActionListener(e -> {
            rpFilePrefix = rpFilePrefixField.getText().trim();
            if (rpFilePrefix.isEmpty()) rpFilePrefix = "rp_";
            rpFilePrefixField.setText(rpFilePrefix);
            try { configuration.saveValue(RP_FILE_PREFIX, rpFilePrefix); } catch (IOException ex) {}
        });
        rpFilePrefixField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                rpFilePrefix = rpFilePrefixField.getText().trim();
                if (rpFilePrefix.isEmpty()) rpFilePrefix = "rp_";
                rpFilePrefixField.setText(rpFilePrefix);
                try { configuration.saveValue(RP_FILE_PREFIX, rpFilePrefix); } catch (IOException ex) {}
            }
        });
        fileConfigPanel.add(rpFilePrefixField);
        
        fileConfigPanel.add(new JLabel("Max files"));
        JLabel limitHelp = new JLabel(new HelpIcon());
        limitHelp.setToolTipText(htmlTooltip("File limit for the active session. Files are created after clicking 'Start live'. Once the limit is reached, the oldest files from the current session are overwritten. The pool resets when you click 'Stop RP' and then 'Start live' again. Set to 0 to disable."));
        limitHelp.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        limitHelp.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        fileConfigPanel.add(limitHelp);

        JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(rpFileLimit, 0, 9999, 1));
        limitSpinner.addChangeListener(e -> {
            rpFileLimit = (Integer) limitSpinner.getValue();
            try { configuration.saveValue(RP_FILE_LIMIT, "" + rpFileLimit); } catch (IOException ex) {}
        });
        fileConfigPanel.add(limitSpinner);

        rpSettingsDialog = new JDialog(this, "Red Pitaya Settings", false);
        rpSettingsDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        JPanel dialogPanel = new JPanel(new BorderLayout(4, 4));
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        dialogPanel.add(formPanel, BorderLayout.CENTER);
        
        rpSettingsEstimatedSizeLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        dialogPanel.add(rpSettingsEstimatedSizeLabel, BorderLayout.SOUTH);
        
        rpSettingsDialog.add(dialogPanel);
        rpSettingsDialog.pack();
        rpSettingsDialog.setLocationRelativeTo(this);

        rpEstimatedSizeLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JPanel actionsAndFiles = new JPanel(new BorderLayout());
        actionsAndFiles.add(actions, BorderLayout.CENTER);
        actionsAndFiles.add(fileConfigPanel, BorderLayout.SOUTH);
        rpPanel.add(actionsAndFiles, BorderLayout.CENTER);
        rpPanel.add(rpEstimatedSizeLabel, BorderLayout.SOUTH);

        rpChannelsCombo.addActionListener(e -> updateRedPitayaChannelControls());
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
        rpFrameSizeSpinner.addChangeListener(e -> updateRedPitayaDerivedControls());
        rpFrameCountSpinner.addChangeListener(e -> updateRedPitayaDerivedControls());
        updateRedPitayaChannelControls();
        updateRedPitayaModeControls();
        return rpPanel;
    }

    private void addFormRow(JPanel panel, String label, JComponent component, String tooltip) {
        JPanel labelPanel = new JPanel(new BorderLayout(4, 0));
        labelPanel.setOpaque(false);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setToolTipText(tooltip);
        JLabel help = new JLabel(new HelpIcon());
        help.setToolTipText(htmlTooltip(tooltip));
        help.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        help.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        component.setToolTipText(tooltip);
        labelPanel.add(labelComponent, BorderLayout.CENTER);
        labelPanel.add(help, BorderLayout.EAST);
        panel.add(labelPanel);
        panel.add(component);
    }

    private void setDecimalSpinnerEditor(JSpinner spinner) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.###############"));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setColumns(12);
        }
    }

    private void setIntegerSpinnerEditor(JSpinner spinner) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setColumns(10);
        }
    }

    private String helpText(String key) {
        return switch (key) {
            case "host" ->
                "IP address or DNS name of the Red Pitaya board.\n"
                + "\n"
                + "The rp_prpd_agent.py agent must be running on this board.";
            case "port" ->
                "TCP port of the agent running on Red Pitaya.\n"
                + "\n"
                + "It must be the same as the agent's --port parameter.";
            case "channels" ->
                "ADC input channels used for acquisition.\n"
                + "\n"
                + "Options:\n"
                + "- IN1\n"
                + "- IN2\n"
                + "- IN1+IN2";
            case "visual" ->
                "Channel forwarded to the existing Java PRPD visualization.\n"
                + "\n"
                + "The Red Pitaya acquisition may contain IN1 and IN2, but the main PRPD viewer displays one selected channel.";
            case "gain1" ->
                "Red Pitaya input range for channel IN1.\n"
                + "\n"
                + "LV: for small signals.\n"
                + "HV: for larger input voltages.\n"
                + "\n"
                + "Board: STEMlab 125-14 Pro Z7020 Gen 2.";
            case "gain2" ->
                "Red Pitaya input range for channel IN2.\n"
                + "\n"
                + "LV: for small signals.\n"
                + "HV: for larger input voltages.\n"
                + "\n"
                + "Board: STEMlab 125-14 Pro Z7020 Gen 2.";
            case "decimation" ->
                "Decimation reduces the effective ADC sampling frequency.\n"
                + "Only powers of 2 are allowed.\n"
                + "\n"
                + "Formula:\n"
                + "fs = 125 MS/s / decimation\n"
                + "\n"
                + "Examples:\n"
                + "- 1 -> 125 MS/s\n"
                + "- 2 -> 62.5 MS/s\n"
                + "- 4 -> 31.25 MS/s\n"
                + "- 8 -> 15.625 MS/s\n"
                + "- 1024 -> about 122.07 kS/s\n"
                + "\n"
                + "Higher decimation gives a longer possible acquisition time and a smaller file, but worsens pulse time resolution.";
            case "averaging" ->
                "Red Pitaya hardware averaging, if it is supported by the API used on the board.";
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
                + "CH1_PE with level 0.1 V starts acquisition when IN1 crosses about 0.1 V on a rising edge.";
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
                "Maximum time to wait for the trigger and for the DMA buffer to fill.\n"
                + "\n"
                + "If this time elapses, acquisition is interrupted with an error.";
            case "mode" ->
                "Mode used to determine acquisition length:\n"
                + "\n"
                + "- by duration,\n"
                + "- by number of frames.";
            case "duration" ->
                "Time used to collect data in duration mode.";
            case "frameSize" ->
                "Number of samples per channel in one TCP frame.\n"
                + "\n"
                + "The same size is used when writing frames to an RPPR file.";
            case "frameCount" ->
                "Number of frames collected in frame-count mode.";
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
                "Base frequency of the AC power system (e.g., 50.0 Hz or 60.0 Hz).\n"
                + "Used to determine the period for phase-resolved partial discharge patterns.";
            case "Zero-crossing instant" ->
                "Phase shift or time offset to align the start of the PRPD pattern\n"
                + "with the true zero-crossing of the AC voltage wave.";
            case "Sampling frequency [Hz]" ->
                "The rate at which the signal is sampled by the ADC.\n"
                + "Must match the actual acquisition rate (e.g., 1000000.0 for 1 MS/s).";
            case "Pulse ampl. threshold" ->
                "Minimum amplitude for a peak to be considered a valid partial discharge pulse.\n"
                + "Peaks below this threshold are ignored as background noise.";
            case "Dead time [us]" ->
                "Minimum time required between consecutive pulses.\n"
                + "Prevents multiple detections from a single oscillating or ringing pulse.";
            case "HPF cutoff frequency [Hz]" ->
                "Cutoff frequency for the High-Pass Filter (HPF).\n"
                + "Filters out the low-frequency AC voltage component and baseline wander.";
            case "Filter Q" ->
                "Quality factor of the filter.\n"
                + "Determines the sharpness and damping of the filter's frequency response.";
            case "Filter Order" ->
                "The number of poles in the filter.\n"
                + "Higher order means steeper roll-off but requires more computational power.";
            case "Histogram min" ->
                "Minimum amplitude displayed on the Y-axis of the PRPD histogram.";
            case "Histogram max" ->
                "Maximum amplitude displayed on the Y-axis of the PRPD histogram.";
            default ->
                "";
        };
    }

    private static class HelpIcon implements javax.swing.Icon {
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
        public int getIconWidth() { return 18; }
        @Override
        public int getIconHeight() { return 18; }
    }

    private String htmlTooltip(String text) {
        StringBuilder html = new StringBuilder("<html><div style='width: 340px; white-space: normal;'>");
        String separator = "";
        for (String line : text.split("\\R", -1)) {
            String escaped = line.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
            if (escaped.startsWith("- ")) {
                escaped = "&bull; " + escaped.substring(2);
            }
            html.append(separator).append(escaped);
            separator = "<br>";
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
        if (selectedVisual != null) {
            rpVisualChannelCombo.setSelectedItem(selectedVisual);
        }
        updateRedPitayaDerivedControls();
    }

    private void updateRedPitayaModeControls() {
        boolean duration = "duration".equals(rpModeCombo.getSelectedItem());
        rpDurationSpinner.setEnabled(duration);
        rpFrameCountSpinner.setEnabled(!duration);
        updateRedPitayaDerivedControls();
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
            int frameSize = normalizedEven(Math.max(1, currentSpinnerIntValue(rpFrameSizeSpinner)));
            long frameCount;
            long totalSamples;
            double durationS;

            if (durationMode) {
                durationS = Math.max(0.0, currentSpinnerDoubleValue(rpDurationSpinner));
                totalSamples = normalizedEven(Math.max(1L, Math.round(durationS * sampleRate)));
                frameCount = ceilDiv(totalSamples, frameSize);
                setSpinnerLongValue(rpFrameCountSpinner, frameCount);
            } else {
                frameCount = Math.max(1L, currentSpinnerIntValue(rpFrameCountSpinner));
                totalSamples = normalizedEven((long) frameSize * frameCount);
                durationS = totalSamples / sampleRate;
                setSpinnerDoubleValue(rpDurationSpinner, durationS);
            }

            int channelCount = redPitayaChannelCount();
            long payloadBytes = multiplySaturating(multiplySaturating(totalSamples, channelCount), 2L);
            long frameHeaderBytes = multiplySaturating(frameCount, 34L);
            long approximateFileBytes = addSaturating(addSaturating(payloadBytes, frameHeaderBytes), 4096L);

            rpEstimatedSizeLabel.setText(String.format(
                    Locale.US,
                    "<html>Approx. file/data: %s<br>Samples: %s | Frames: %s | %d ch | fs=%.6g Hz</html>",
                    formatBytes(approximateFileBytes),
                    formatInteger(totalSamples),
                    formatInteger(frameCount),
                    channelCount,
                    sampleRate
            ));
            if (rpSettingsEstimatedSizeLabel != null) {
                rpSettingsEstimatedSizeLabel.setText(rpEstimatedSizeLabel.getText());
            }
            updateRedPitayaEstimateFont();
        } catch (Exception ex) {
            rpEstimatedSizeLabel.setText("Approx. file/data size: unavailable");
            if (rpSettingsEstimatedSizeLabel != null) {
                rpSettingsEstimatedSizeLabel.setText(rpEstimatedSizeLabel.getText());
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
            case "IN1+IN2" ->
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
        config.frameSize = spinnerIntValue(rpFrameSizeSpinner);
        config.frameCount = spinnerIntValue(rpFrameCountSpinner);
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
            rpFrameSizeSpinner,
            rpFrameCountSpinner
        };
        for (JSpinner spinner : spinners) {
            commitSpinnerEdit(spinner);
        }
    }

    private void commitSpinnerEdit(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            String text = editor.getTextField().getText().trim().replace(',', '.');
            SpinnerModel model = spinner.getModel();
            if (model instanceof SpinnerNumberModel numberModel) {
                Number current = numberModel.getNumber();
                try {
                    if (current instanceof Integer || current instanceof Long) {
                        spinner.setValue(Integer.parseInt(text));
                    } else {
                        spinner.setValue(Double.parseDouble(text));
                    }
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
        return ((Number) spinner.getValue()).doubleValue();
    }

    private int currentSpinnerIntValue(JSpinner spinner) {
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
        return "IN1+IN2".equals(channels) ? 2 : 1;
    }

    private void setSpinnerLongValue(JSpinner spinner, long value) {
        long bounded = Math.max(1L, Math.min(Integer.MAX_VALUE, value));
        spinner.setValue((int) bounded);
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
            float size = Math.max(9.0f, currentFont.getSize2D() - 3.0f);
            rpEstimatedSizeLabel.setFont(currentFont.deriveFont(size));
            if (rpSettingsEstimatedSizeLabel != null) {
                rpSettingsEstimatedSizeLabel.setFont(currentFont.deriveFont(size));
            }
        }
    }

    private void saveRedPitayaConfig(RedPitayaConfig config) {
        try {
            configuration.saveValue(RP_HOST, config.host);
            configuration.saveValue(RP_PORT, Integer.toString(config.port));
            configuration.saveValue(RP_CHANNELS, (String) rpChannelsCombo.getSelectedItem());
            configuration.saveValue(RP_VISUAL_CHANNEL, config.visualChannel == 2 ? "IN2" : "IN1");
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
                0.0,
                deadUs,
                new HighPassFilter(fs, cutF, filterQ, filterOrder)
        );
        for (CachedBuffer cached : snapshot) {
            Buffer buffer = new Buffer(cached.t, cached.u, cached.used, false);
            buffer.resetForUse(1);
            buffer.setUsed(cached.used);
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
    public static double writeBuffer(FileChannel channel, Buffer buf) throws IOException {

        int samples = buf.used;

        int bytesToWrite = samples * 2 * Double.BYTES;

        ByteBuffer byteBuffer
                = ByteBuffer.allocateDirect(bytesToWrite);

        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        DoubleBuffer db = byteBuffer.asDoubleBuffer();

        for (int i = 0; i < samples; i++) {
            db.put(buf.t[i]);
            db.put(buf.u[i]);
        }

        byteBuffer.position(0);
        byteBuffer.limit(bytesToWrite);

        while (byteBuffer.hasRemaining()) {
            channel.write(byteBuffer);
        }

        return bytesToWrite / (1024.0 * 1024.0);
    }

    //---------------- Actions ------
    private void onExit() {
        closeSocket();
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
                        lastDataFile = file.getAbsolutePath();
                        status.setText("Loaded " + file.getName());
                    } catch (Exception ex) {
                        System.err.println("Bad file: " + file.getName() + " : " + ex.getMessage());
                        lastDataFile = null;
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
                pipeline.setThreshold(0.0);
            }
        } catch (Exception ex) {
            setParamField("Pulse ampl. threshold", "" + PRPDConstants.DEFAULT_THRESHOLD);
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
            stopBtn.setEnabled(false);
            dataSource.setText("Red Pitaya (" + config.host + ":" + config.port + ", " + (live ? "live" : "once") + ")");
            startRecordButton.setEnabled(true);
            stopRecordButton.setEnabled(true);
        } catch (Exception ex) {
            realTimeData = false;
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Red Pitaya", JOptionPane.ERROR_MESSAGE);
            status.setText(ex.getMessage());
            lastDataFile = null;
        }
    }

    private void openSocket() {
        inBatchMode = false;
        realTimeData = true;
        String daqSocketAddr = dataServer.getText();
        try {
            getData(daqSocketAddr);
            stopBtn.setEnabled(true);
            dataSource.setText("socket (" + daqSocketAddr + ")");
            startRecordButton.setEnabled(true);
            stopRecordButton.setEnabled(true);
        } catch (Exception ex) {
            System.err.println("Bad socket: " + daqSocketAddr + " : " + ex.getMessage());
            lastDataFile = null;
            //ex.printStackTrace();
        }
    }

    private void closeSocket() {
        if (realTimeData) {
            startRecordButton.setEnabled(false);
            stopRecordButton.setEnabled(false);
            if (pipeline != null) {
                pipeline.stop();
            }
            stopBtn.setEnabled(false);
            if (rpStopButton != null) {
                rpStopButton.setEnabled(false);
            }
            realTimeData = false;
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

        histogram = new DynamicPRPDHistogram(
                center.getWidth(), center.getHeight(),
                360, 200,
                (bipolarHistogram ? -ampMax : 0), ampMax,
                bipolarHistogram
        );
        histogram.drawF0(drawF0);
        ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
        ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
        if (showRawDataCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
        }
        center.setHistogram((DynamicPRPDHistogram) histogram);
        if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
        center.revalidate();

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
                0.0,
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
                        receivedSignalsDir.toPath(),
                        this::onRedPitayaCaptureSaved,
                        RP_LIVE_RESTART_DELAY_MS,
                        rpFilePrefix
                ),
                3,
                bufferSize,
                2,
                extractor,
                createPipelineListener("Red Pitaya", "redpitaya")
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

    private PRPDPipelineListener createPipelineListener(String displayName, String exportName) {
        return new PRPDPipelineListener() {
            private long lastPaintTime = 0;
            
            @Override
            public void preExtract(Buffer buffer) {
                if (signalStart.compareAndSet(true, false)) {
                    double ph0 = PhaseEstimator.estimateIntialPhase(buffer, f0);
                    double estt0 = ph0 / (2 * Math.PI * f0);
                    if (estt0 < 0.5 / fs) {
                        estt0 = 0.0;
                    }
                    if (realTimeData) {
                        t0 = estt0;
                        extractor.setT0(t0);
                        SwingUtilities.invokeLater(() -> {
                            setParamField("Zero-crossing instant", "" + t0);
                            classifyButton.setEnabled(true);
                        });
                    }
                }
            }
            
            @Override
            public void bufferRead(Buffer buffer) {
                receivedSignalCache.add(buffer);
                if (realTimeData && recordedData != null && recordedData.isOpen()) {
                    if (recordedMB < recordLimit) {
                        try {
                            recordedMB += (int) writeBuffer(recordedData, buffer);
                            recordSizeLabel.setText(recordedMB + " MB used");
                        } catch (IOException ex) {
                            JOptionPane.showConfirmDialog(
                                    PRPDTool.this,
                                    "Error while recording",
                                    "Warning",
                                    JOptionPane.WARNING_MESSAGE
                            );
                            stopRecorder();
                        }
                    } else {
                        JOptionPane.showConfirmDialog(
                                PRPDTool.this,
                                "Record size limit reached",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE
                        );
                        stopRecorder();
                        recordSizeLabel.setText(recordedMB + " MB used");
                    }
                }
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
                histogram.addPulses(pulses);
                
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

                if (inBatchMode) {
                    exportPRPD4YOLO(exportName);
                    System.out.println("..." + exportName + " finished.");
                }
                setTitle("PRPD Viewer: " + displayName);
                setCursor(Cursor.getDefaultCursor());
                classifyButton.setEnabled(true);
                if (realTimeData && rpStopButton != null) {
                    startRecordButton.setEnabled(false);
                    stopRecordButton.setEnabled(false);
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
                    stopBtn.setEnabled(false);
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

        histogram = new DynamicPRPDHistogram(
                center.getWidth(), center.getHeight(),
                360, 200,
                (bipolarHistogram ? -ampMax : 0), ampMax,
                bipolarHistogram
        );
        histogram.drawF0(drawF0);
        ((DynamicPRPDHistogram) histogram).setDisplayThreshold(threshold);
        ((DynamicPRPDHistogram) histogram).setFastRendering(fastRendering);
        if (showRawDataCb != null) {
            ((DynamicPRPDHistogram) histogram).setShowRawData(showRawDataCb.isSelected());
        }
        SwingUtilities.invokeLater(() -> {
            center.setHistogram((DynamicPRPDHistogram) histogram);
            if (autoscaleCb != null && autoscaleCb.isSelected()) { applyAutoscale(); } else { center.setImage(histogram.getImage()); }
            center.revalidate();

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
                0.0,
                deadUs,
                hfFilter
        );

        int buffer_size = BufferFactory.bufferSize();

        pipeline = new PRPDPipeline(
                filename,
                3, // 3 konsumentów: extractor, envelope, signal
                buffer_size,
                2,
                extractor,
                new PRPDPipelineListener() {
            private long lastPaintTime = 0;
            
            @Override
            public void bufferRead(Buffer buffer) {
                receivedSignalCache.add(buffer);
                if (realTimeData && recordedData != null && recordedData.isOpen()) {
                    if (recordedMB < recordLimit) {
                        try {
                            recordedMB += (int) writeBuffer(recordedData, buffer);
                            recordSizeLabel.setText(recordedMB + " MB used");
                        } catch (IOException ex) {
                            JOptionPane.showConfirmDialog(
                                    PRPDTool.this,
                                    "Error while recording",
                                    "Warning",
                                    JOptionPane.WARNING_MESSAGE
                            );
                            stopRecorder();
                        }
                    } else {
                        JOptionPane.showConfirmDialog(
                                PRPDTool.this,
                                "Record size limit reached",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE
                        );
                        stopRecorder();
                        recordSizeLabel.setText(recordedMB + " MB used");
                    }
                }
                if (signalStart.compareAndSet(true, false)) {
                    double ph0 = PhaseEstimator.estimateIntialPhase(buffer, f0);
                    double estt0 = ph0 / (2 * Math.PI * f0);
                    if (estt0 < 0.5 / fs) {
                        estt0 = 0.0;
                    }
                    if (realTimeData) {
                        t0 = estt0;
                        extractor.setT0(t0);
                        setParamField("Zero-crossing instant", "" + t0);
                    }
                    classifyButton.setEnabled(true);
                }
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
                histogram.addPulses(pulses);
                
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
                    stopBtn.setEnabled(false);
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
                stopBtn.setEnabled(false);
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

    private void stopRecorder() {
        try {
            if (recordedData != null) {
                recordedData.close();
            }
            recordedData = null;
            stopRecordButton.setEnabled(false);
            startRecordButton.setEnabled(true);
            if (recordedFile != null) {
                status.setText("Saved recording: " + recordedFile.getName());
            }
            refreshReceivedSignals();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    PRPDTool.this,
                    ex.getMessage(),
                    "Warning",
                    JOptionPane.WARNING_MESSAGE
            );
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
