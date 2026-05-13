package prpdtool;

/**
 *
 * @author jstar
 */
import classifiers.*;
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
import parallelprpd.pipeline.Buffer;
import parallelprpd.pipeline.BufferFactory;
import parallelprpd.pipeline.DynamicPRPDHistogram;
import parallelprpd.pipeline.DynamicSignalImage;
import parallelprpd.pipeline.Filter;
import parallelprpd.pipeline.HighPassFilter;
import parallelprpd.pipeline.PRPDExtractorCore;
import parallelprpd.pipeline.PRPDPipeline;
import parallelprpd.pipeline.PRPDPipelineListener;
import parallelprpd.pipeline.Pulses;

public class PRPDTool extends JFrame {

    private boolean inBatchMode;
    
    private String[] classes = {
            "floating",
            "corona -",
            "noise",
            "corona +",
            "surface",
            "void"
    };
    
    private final Classifier[] classifiers;

    private Map<Classifier, JLabel> cResults;

    private BufferedImage prpd4YOLO;

    // GUI config
    private final static Font[] fonts = {
        new Font("Courier", Font.PLAIN, 12),
        new Font("Courier", Font.PLAIN, 18),
        new Font("Courier", Font.PLAIN, 24)
    };
    private Font currentFont = fonts[1];

    private static final String CONFIG_FILE = ".prpd_config";
    private final Configuration configuration = new Configuration(CONFIG_FILE);

    private final String LAST_DIR = "PRPDMonitor.last.dir";
    private final String HOME_DIR = "PRPDMonitor.home.dir";
    private final String MODELS_ROOT = "PRPDMonitor.models.root";
    private final String FONTSIZE = "PRPDMonitor.font.size";
    private final String FRAMESIZE = "PRPDMonitor.frame.size";

    /* Example configuration
    PRPDMonitor.font.size=24
    PRPDMonitor.last.dir=/media/jstar/VIDEO/PRPDtool/data
    PRPDMonitor.home.dir=/home/jstar/
    PRPDMonitor.python=/usr/bin/python
    PRPDMonitor.models.root=oer
    PRPDMonitor.frame.size=2963x1452
     */
    private JPanel left;
    private ImagePanel center;
    private JPanel right;
    private JPanel bottom;
    private JPanel paramPanel;

    private JSplitPane splitCenterRight;
    private JSplitPane splitLeft;
    private JSplitPane verticalSplit;

    private final JLabel status = new JLabel("");

    // PRPD config
    private double f0 = 50;
    private double t0 = 0;
    private double fs = 1_000_000;  // próbkowanie 
    private double dfs = fs;  // próbkowanie z danych
    private double threshold = 0.012; //próg detekcji impulsu po odjęciu tła
    private double ampMax = 0.12; // max amplituda immpulsu
    private double deadUs = 30; //martwy czas po wykryciu impulsu [µs]
    private double filterQ = 0.707; // Q filtra
    private int filterOrder = 4; // rząd filtra
    private double cutF = 50_000; // f odcięcia

    Param<?>[] params = {
        Param.dbl("Basic frequency [Hz]", () -> f0, v -> f0 = v),
        Param.dbl("Zero-crossing instant", () -> t0, v -> t0 = v),
        Param.dbl("Sampling frequency [Hz]", () -> fs, v -> fs = v),
        Param.dbl("Pulse ampl. threshold", () -> threshold, v -> threshold = v),
        Param.dbl("Max pulse amplitude", () -> ampMax, v -> ampMax = v),
        Param.dbl("Dead time [us]", () -> deadUs, v -> deadUs = v),
        Param.dbl("HPF cutoff frequency [Hz]", () -> cutF, v -> cutF = v),
        Param.dbl("Filter Q", () -> filterQ, v -> filterQ = v),
        Param.integer("Filter Order", () -> filterOrder, v -> filterOrder = v)
    };

    // Misc options
    private boolean drawF0 = true;
    private boolean bipolarHistogram;

    // Data
    private ImagePanel signalPanel;
    private ImagePanel envelopePanel;

    private String lastDataFile;
    private DynamicPRPDHistogram histogram;
    private DynamicSignalImage envelope;
    private DynamicSignalImage signal;
    private PRPDPipeline pipeline;

    private JLabel dataSource;

    private JLabel paramChange;
    private JButton applyButton;

    private JButton classifyButton;

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

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
            for (Font f : fonts) {
                if (Math.abs(f.getSize() - size) < Math.abs(currentFont.getSize() - size)) {
                    currentFont = f;
                }
            }
        } catch (Exception e) {
        }

        String homeDir = configuration.getValue(HOME_DIR);
        String modelsRoot = configuration.getValue(MODELS_ROOT);

        String mobileYOLO = homeDir + "NetBeansProjects/PRPDtool/models/" + modelsRoot + "_mobileYOLO.onnx";
        String squeezeYOLO = homeDir + "NetBeansProjects/PRPDtool/models/" + modelsRoot + "_squeezeYOLO.onnx";

        classifiers = new Classifier[2];
        classifiers[0] = new ONNXClassifier(
                mobileYOLO, 
                new MobileYOLOParser(7, 9, classes, 0.5f, 0.55f)
        );
        classifiers[1] = new classifiers.ONNXClassifier(
                squeezeYOLO, 
                new SqueezeYOLOParser(7, 9, classes, 0.25f)
        );
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

        JMenuItem prpdMI = new JMenuItem("Export YOLO image");
        prpdMI.addActionListener(e -> exportPRPD4YOLO(lastDataFile));
        fileM.add(prpdMI);

        fileM.addSeparator();

        JMenuItem exitMI = new JMenuItem("Exit");
        exitMI.addActionListener(e -> System.exit(0));
        fileM.add(exitMI);
        mb.add(fileM);

        JMenu scriptsM = new JMenu("Scripts");
        JMenuItem batchMI = new JMenuItem("Dir->PRPD");
        batchMI.addActionListener(e -> dir2prpd());
        scriptsM.add(batchMI);
        mb.add(scriptsM);

        JMenu optM = new JMenu("Options");
        JMenuItem fontMI = new JMenuItem("Font size");
        optM.add(fontMI);
        ButtonGroup fgroup = new ButtonGroup();
        for (Font f : fonts) {
            JRadioButtonMenuItem fontOpt = new JRadioButtonMenuItem("\t\t\t" + String.valueOf(f.getSize()));
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
            center.repaint();
        });
        optM.add(sinMB);

        JCheckBoxMenuItem bipolarMB = new JCheckBoxMenuItem("Bipolar histogram", bipolarHistogram);
        bipolarMB.addActionListener(e -> {
            bipolarHistogram = bipolarMB.isSelected();
            histogram = new DynamicPRPDHistogram(
                    center.getWidth(), center.getHeight(),
                    360, 200,
                    (bipolarHistogram ? -ampMax : 0), ampMax,
                    bipolarHistogram
            );
            histogram.drawF0(drawF0);
            center.setImage(histogram.getImage());
            center.revalidate();
            if (lastDataFile != null) {
                try {
                    readDataFile(lastDataFile);
                } catch (Exception ex) {
                    status.setText(ex.getMessage());
                }
            }
            center.repaint();
        });
        optM.add(bipolarMB);

        mb.add(optM);
        setJMenuBar(mb);
    }

    private void initGui() {

        left = new JPanel();
        right = new JPanel();
        bottom = new JPanel();
        center = new ImagePanel(new BufferedImage(640, 480, BufferedImage.TYPE_BYTE_GRAY));

        splitCenterRight = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                center,
                right
        );
        splitCenterRight.setResizeWeight(0.85);

        // --- lewy + reszta ---
        splitLeft = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                left,
                splitCenterRight
        );
        splitLeft.setResizeWeight(0.05);

        // --- góra (80%) + dół (20%) ---
        verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                splitLeft,
                bottom
        );
        verticalSplit.setResizeWeight(0.80);

        // Cieńsze dzielniki
        splitLeft.setDividerSize(1);
        splitCenterRight.setDividerSize(1);
        verticalSplit.setDividerSize(1);

        setContentPane(verticalSplit);

        // ustawienie początkowych proporcji
        SwingUtilities.invokeLater(() -> {
            verticalSplit.setDividerLocation(0.75);
            splitLeft.setDividerLocation(0.05);
            splitCenterRight.setDividerLocation(0.8);

            histogram = new DynamicPRPDHistogram(
                    center.getWidth(), center.getHeight(),
                    360, 200,
                    (bipolarHistogram ? -ampMax : 0), ampMax,
                    bipolarHistogram
            );
            histogram.drawF0(drawF0);

            envelope = new DynamicSignalImage(
                    "Signal envelope", Color.BLUE,
                    bottom.getWidth() / 2 - 5, bottom.getHeight(),
                    -10.0, 10.0, null
            );
            signal = new DynamicSignalImage(
                    "Filtered signal", Color.GREEN,
                    bottom.getWidth() / 2 - 5, bottom.getHeight(),
                    -10.0, 10.0, null
            );

            center.setImage(histogram.getImage());
            center.revalidate();

            envelopePanel = new ImagePanel(envelope.getImage());
            envelopePanel.setPreferredSize(new Dimension(bottom.getWidth() / 2 - 5, bottom.getHeight()));
            envelopePanel.setBorder(BorderFactory.createTitledBorder("Envelope"));

            signalPanel = new ImagePanel(signal.getImage());
            signalPanel.setPreferredSize(new Dimension(bottom.getWidth() / 2 - 5, bottom.getHeight()));
            signalPanel.setBorder(BorderFactory.createTitledBorder("Signal"));
            bottom.setLayout(new GridLayout(1, 2));
            bottom.add(envelopePanel);
            bottom.add(signalPanel);

            paramPanel = new JPanel();
            paramPanel.setLayout(new GridLayout(20, 2));
            paramPanel.add(new JLabel("Data source: "));
            dataSource = new JLabel("none");
            paramPanel.add(dataSource);

            paramPanel.add(new JLabel("Status:"));
            paramPanel.add(status);

            for (Param p : params) {
                JLabel label = new JLabel(p.name);
                JTextField field = new JTextField(p.getText(), 8);
                p.setField(field);
                field.addActionListener(e -> {
                    try {
                        p.setFromText(field.getText());
                        field.setBackground(Color.WHITE);
                        status.setText("Some parameter changes may not have been applied!");
                        paramChange.setText("Param(s) change!");
                        applyButton.setVisible(true);
                        applyButton.setBackground(Color.red);
                    } catch (NumberFormatException ex) {
                        field.setBackground(new Color(255, 200, 200));
                    }
                });

                field.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        status.setText("Some parameter changes may not have been applied!");
                        paramChange.setText("Param(s) change!");
                        applyButton.setVisible(true);
                    }
                });
                paramPanel.add(label);
                paramPanel.add(field);
            }
            paramChange = new JLabel(" ");
            applyButton = new JButton("APPLY");
            applyButton.addActionListener(e -> onParameterChanged());
            applyButton.setVisible(false);
            paramPanel.add(paramChange);
            paramPanel.add(applyButton);
            setFontRecursively(paramPanel, currentFont, 0);

            right.setLayout(new BorderLayout());
            right.add(paramPanel, BorderLayout.CENTER);

            JPanel classifyPanel = new JPanel(new BorderLayout());

            JPanel modelPanel = new JPanel(new GridLayout(classifiers.length + 1, 2, 3, 3));
            //modelPanel.setBackground(Color.black);
            cResults = new HashMap<>();
            JLabel c1 = new JLabel("Classifier");
            c1.setBorder(BorderFactory.createLineBorder(Color.black));
            JLabel c2 = new JLabel("Result");
            c2.setBorder(BorderFactory.createLineBorder(Color.black));
            modelPanel.add(c1);
            modelPanel.add(c2);
            for (Classifier c : classifiers) {
                if (c.ok()) {
                    JLabel name = new JLabel(c.name());
                    name.setBackground(Color.gray);
                    JLabel result = new JLabel("           ");
                    result.setBackground(Color.gray);
                    modelPanel.add(name);
                    modelPanel.add(result);
                    cResults.put(c, result);
                }
            }
            classifyPanel.add(modelPanel, BorderLayout.CENTER);

            classifyButton = new JButton("CLASIFY");
            classifyButton.setBackground(Color.white);
            classifyButton.addActionListener(e -> classifyPRPD(cResults));
            classifyButton.setEnabled(false);
            classifyPanel.add(classifyButton, BorderLayout.SOUTH);

            setFontRecursively(classifyPanel, currentFont, 0);

            right.add(classifyPanel, BorderLayout.SOUTH);
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                verticalSplit.setDividerLocation(0.75);
                splitLeft.setDividerLocation(0.05);
                splitCenterRight.setDividerLocation(0.8);
                histogram.resize(center.getWidth(), center.getHeight());
                envelope.resize(bottom.getWidth() / 2, bottom.getHeight());
                //prpdPanel.setPreferredSize(new Dimension(center.getWidth(), center.getHeight()));
                envelopePanel.setPreferredSize(new Dimension(bottom.getWidth() / 2, bottom.getHeight()));
                try {
                    configuration.saveValue(FRAMESIZE, getWidth() + "x" + getHeight());
                } catch (IOException ex) {
                }
            }
        });
    }

    // ------------- Misc. helpers
    private void onParameterChanged() {
        for (Param p : params) {
            p.setFromField();
        }

        if (lastDataFile != null) {
            try {
                readDataFile(lastDataFile);
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        }
        paramChange.setText(" ");
        applyButton.setVisible(false);
    }

    // Helper -sets font
    private void setCurrentFont() {
        setFontRecursively(this, currentFont, 0);
        UIManager.put("OptionPane.messageFont", currentFont);
        UIManager.put("OptionPane.buttonFont", currentFont);
        UIManager.put("OptionPane.messageFont", currentFont);
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

    //---------------- Actions ------
    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            inBatchMode = false;
            File file = fileChooser.getSelectedFile();
            try {
                String filename = file.getAbsolutePath();
                readDataFile(filename);
                saveLastUsedDirectory(file.getParentFile().getAbsolutePath());
                dataSource.setText("file (" + file.getName() + ")");
                lastDataFile = filename;
            } catch (Exception ex) {
                System.err.println("Bad file: " + file.getName() + " : " + ex.getMessage());
                lastDataFile = null;
                //ex.printStackTrace();
            }
        }
    }

    private void readDataFile(String filename) throws Exception {

        stopPipeline();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {

        }
        classifyButton.setEnabled(false);
        for (Classifier c : cResults.keySet()) {
            cResults.get(c).setText("");
        }

        double[] lasttu = new double[2];
        String[] last;
        if (filename.endsWith(".csv")) {
            last = prpdtool.Utils.readLastLineUtf8(filename).trim().split("[,;\\s]+");
        } else {
            last = prpdtool.Utils.readLastPair(filename).trim().split("[,;\\s]+");
        }
        lasttu[0] = Double.parseDouble(last[0]);

        dfs = fs;
        Filter filter = new HighPassFilter(fs, cutF, filterQ, filterOrder);
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

        envelope = new DynamicSignalImage(
                "Signal envelope", Color.BLUE,
                bottom.getWidth() / 2 - 5, bottom.getHeight(),
                0.0, lasttu[0], abs
        );

        signal = new DynamicSignalImage(
                "Filtered signal", Color.GREEN,
                bottom.getWidth() / 2 - 5, bottom.getHeight(),
                0.0, lasttu[0], filter
        );

        center.setImage(histogram.getImage());
        envelopePanel.setImage(envelope.getImage());
        signalPanel.setImage(signal.getImage());

        center.repaint();
        envelopePanel.repaint();
        signalPanel.repaint();

        PRPDExtractorCore extractor = new PRPDExtractorCore(
                f0,
                t0,
                threshold,
                deadUs,
                filter
        );

        int buffer_size = BufferFactory.bufferSize();

        pipeline = new PRPDPipeline(
                filename,
                3, // 3 konsumentów: extractor, envelope, signal
                buffer_size,
                2,
                extractor,
                new PRPDPipelineListener() {
            @Override
            public void bufferRead(Buffer buffer) {
                envelope.addBuffer(buffer);
                envelopePanel.repaint();
                signal.addBuffer(buffer);
                signalPanel.repaint();
            }

            @Override
            public void pulsesReady(Pulses pulses) {
                if (pulses.fs != fs) {
                    dfs = pulses.fs;
                }
                histogram.addPulses(pulses);
                center.repaint();
            }

            @Override
            public void finished() {
                setTitle("PRPD Viewer: " + Paths.get(filename).getFileName().toString());
                setCursor(Cursor.getDefaultCursor());
                classifyButton.setEnabled(true);
                if (inBatchMode) {
                    exportPRPD4YOLO(filename);
                    System.out.println("..." + filename + " finished.");
                }
                if (Math.abs((dfs - fs) / fs) > 1e-8) {
                    JOptionPane.showMessageDialog(
                            PRPDTool.this,
                            "The sampling frequency estimated from data is " + String.format(Locale.US, "%.6g", dfs) + " Hz",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
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
        pipeline.start();
    }

    private void stopPipeline() {
        if (pipeline != null) {
            pipeline.close();
            BufferFactory.reset();
            pipeline = null;
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
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                ImageIO.write(histogram.getImage(), "png", fileChooser.getSelectedFile());
                status.setText("image saved to " + fileChooser.getSelectedFile().getName());
            } catch (IOException ex) {
                status.setText(ex.getMessage());
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
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            resultView.setText("..working...");
            resultView.repaint();
            SwingUtilities.invokeLater(() -> {
                try {
                    ImageIO.write(prpd4YOLO, "png", new File("prpd4YOLO.png"));
                    Prediction result = classifier.classify(prpd4YOLO);
                    resultView.setBackground(Color.white);
                    resultView.setText(result.toString());
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
        String[] files = dir.list((d, name) -> {
            File f = new File(d, name);
            return f.isFile() && name.toLowerCase().endsWith(".bin");
        });

        if (files == null) {
            return;
        }

        new Thread(() -> {
            inBatchMode = true;

            for (String s : files) {
                try {
                    while (pipeline != null && pipeline.isRunning()) {
                        Thread.sleep(250);
                    }

                    String ds = dir.getAbsolutePath() + File.separator + s;
                    System.out.print("Processing " + ds);

                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            readDataFile(ds);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });

                    System.out.println(" ...started");

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            inBatchMode = false;

        }, "PRPD-batch-thread").start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PRPDTool().setVisible(true);
        });
    }
}
