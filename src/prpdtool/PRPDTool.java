package prpdtool;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import parallelprpd.pipeline.Buffer;
import parallelprpd.pipeline.DynamicPRPDHistogram;
import parallelprpd.pipeline.DynamicSignalImage;
import parallelprpd.pipeline.Filter;
import parallelprpd.pipeline.HighPassFilter;
import parallelprpd.pipeline.PRPDExtractorCore;
import parallelprpd.pipeline.PRPDPipeline;
import parallelprpd.pipeline.PRPDPipelineListener;
import parallelprpd.pipeline.Pulses;

public class PRPDTool extends JFrame {

    // GUI config
    private final static Font[] fonts = {
        new Font("Courier", Font.PLAIN, 12),
        new Font("Courier", Font.PLAIN, 18),
        new Font("Courier", Font.PLAIN, 24)
    };
    private static Font currentFont = fonts[1];

    private static final String CONFIG_FILE = ".prpd_config";
    private final Configuration configuration = new Configuration(CONFIG_FILE);
    private final String LAST_DIR = "PRPDMonitor.last.dir";

    private JPanel left;
    private JPanel center;
    private JPanel right;
    private JPanel bottom;

    private final JLabel status = new JLabel("");

    // PRPD config
    private double f0 = 50;
    private double t0 = 0;
    private double fs = 1_000_000;  // próbkowanie 
    private double threshold = 0.012; //próg detekcji impulsu po odjęciu tła
    private double deadUs = 30; //martwy czas po wykryciu impulsu [µs]
    private double filterQ = 0.707; // Q filtra
    private int filterOrder = 4; // rząd filtra
    private int cutH = 20; // harmoniczna odcięcia

    // Data
    private ImagePanel prpdPanel;
    private ImagePanel signalPanel;
    private ImagePanel envelopePanel;

    private DynamicPRPDHistogram histogram;
    private DynamicSignalImage envelope;
    private DynamicSignalImage signal;
    private PRPDPipeline pipeline;

    private JLabel dataSource;

    public PRPDTool() {
        super("PRPDtool");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 1024);
        setLocationRelativeTo(null);

        createMenuBar();
        initGui();
    }

    private void createMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu fileM = new JMenu("File");
        JMenuItem fileMI = new JMenuItem("Open");
        fileMI.addActionListener(e -> loadFile());
        fileM.add(fileMI);
        JMenuItem exitMI = new JMenuItem("Exit");
        exitMI.addActionListener(e -> System.exit(0));
        fileM.add(exitMI);
        mb.add(fileM);

        JMenu optM = new JMenu("Options");
        JMenuItem fontMI = new JMenuItem("Font size");
        ButtonGroup fgroup = new ButtonGroup();
        for (Font f : fonts) {
            JRadioButtonMenuItem fontOpt = new JRadioButtonMenuItem("\t\t\t" + String.valueOf(f.getSize()));
            final Font cf = f;
            fontOpt.addActionListener(e -> {
                currentFont = cf;
                setFontRecursively(this, currentFont, 0);
                UIManager.put("OptionPane.messageFont", currentFont);
                UIManager.put("OptionPane.buttonFont", currentFont);
                UIManager.put("OptionPane.messageFont", currentFont);
            });
            fontOpt.setSelected(f == currentFont);
            fgroup.add(fontOpt);
            optM.add(fontOpt);
        }
        optM.add(fontMI);
        mb.add(optM);
        setJMenuBar(mb);
    }

    private void initGui() {

        left = new JPanel();
        right = new JPanel();
        bottom = new JPanel();
        center = new JPanel();

        JSplitPane splitCenterRight = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                center,
                right
        );
        splitCenterRight.setResizeWeight(0.85);

        // --- lewy + reszta ---
        JSplitPane splitLeft = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                left,
                splitCenterRight
        );
        splitLeft.setResizeWeight(0.05);

        // --- góra (80%) + dół (20%) ---
        JSplitPane verticalSplit = new JSplitPane(
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
                    0.0, 0.12
            );

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

            prpdPanel = new ImagePanel(histogram.getImage());
            int[] padding = histogram.padding();
            prpdPanel.setPreferredSize(new Dimension(center.getWidth() - padding[0], center.getHeight() - padding[1]));
            center.add(prpdPanel);

            envelopePanel = new ImagePanel(envelope.getImage());
            envelopePanel.setPreferredSize(new Dimension(bottom.getWidth() / 2 - 5, bottom.getHeight()));
            envelopePanel.setBorder(BorderFactory.createTitledBorder("Envelope"));

            signalPanel = new ImagePanel(signal.getImage());
            signalPanel.setPreferredSize(new Dimension(bottom.getWidth() / 2 - 5, bottom.getHeight()));
            signalPanel.setBorder(BorderFactory.createTitledBorder("Signal"));
            bottom.setLayout(new GridLayout(1, 2));
            bottom.add(envelopePanel);
            bottom.add(signalPanel);

            right.setLayout(new GridLayout(0, 1));
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            dataSource = new JLabel("Data source:");
            right.add(dataSource);
            right.add(new JLabel("Frequency:"));
            right.add(new JLabel("Sampling frequency:"));
            right.add(new JLabel("Filter:"));
            right.add(new JLabel("   cut-off freq:"));
            right.add(new JLabel("   quality factor::"));
            right.add(new JLabel("   order:"));
            right.add(new JLabel("Detection thresh:"));
            right.add(new JLabel("Dead time [us]:"));
            right.add(Box.createVerticalGlue());
            right.add(status);         
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                histogram.resize(center.getWidth(), center.getHeight());
                envelope.resize(bottom.getWidth() / 2, bottom.getHeight());
                int[] padding = histogram.padding();
                prpdPanel.setPreferredSize(new Dimension(center.getWidth() - padding[0], center.getHeight() - padding[1]));
                envelopePanel.setPreferredSize(new Dimension(bottom.getWidth() / 2, bottom.getHeight()));
            }
        });
    }

    // ------------- Misc. helpers
    // Helper -sets font
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
            File file = fileChooser.getSelectedFile();
            try {
                String[] lasttu = prpdtool.Utils.readLastLineUtf8(file.getAbsolutePath()).trim().split("[,;\\s]+");

                stopPipeline();

                Filter filter = new HighPassFilter(fs, cutH * f0, filterQ, filterOrder);
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
                        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
                    }

                };

                histogram = new DynamicPRPDHistogram(
                        center.getWidth(), center.getHeight(),
                        360, 200,
                        0.0, 0.12
                );

                envelope = new DynamicSignalImage(
                        "Signal envelope", Color.BLUE,
                        bottom.getWidth() / 2 - 5, bottom.getHeight(),
                        0.0, Double.parseDouble(lasttu[0]), abs
                );

                signal = new DynamicSignalImage(
                        "Filtered signal", Color.GREEN,
                        bottom.getWidth() / 2 - 5, bottom.getHeight(),
                        0.0, Double.parseDouble(lasttu[0]), filter
                );

                prpdPanel.setImage(histogram.getImage());
                envelopePanel.setImage(envelope.getImage());
                signalPanel.setImage(signal.getImage());

                prpdPanel.repaint();
                envelopePanel.repaint();
                signalPanel.repaint();

                PRPDExtractorCore extractor = new PRPDExtractorCore(
                        f0,
                        t0,
                        threshold,
                        deadUs,
                        filter
                );

                pipeline = new PRPDPipeline(
                        file.getAbsolutePath(),
                        500_000,
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
                        histogram.addPulses(pulses);
                        prpdPanel.repaint();
                    }

                    @Override
                    public void finished() {
                        setTitle("PRPD Viewer - finished: " + file.getName());
                        setCursor(Cursor.getDefaultCursor());
                    }

                    @Override
                    public void error(Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(
                                PRPDTool.this,
                                ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
                );

                pipeline.setOnReaderProgress(n
                        -> SwingUtilities.invokeLater(() -> {
                            status.setText("Read " + n + " buffers." );
                        })
                );

                setTitle("PRPD Viewer - " + file.getName());
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                pipeline.start();
                saveLastUsedDirectory(file.getParentFile().getAbsolutePath());
                dataSource.setText("Data source: file (" + file.getName() + ")");
            } catch (Exception ex) {
                System.err.println("Bad file");
            }
        }
    }

    private void stopPipeline() {
        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PRPDTool().setVisible(true);
        });
    }
}
/*
    //---------------- PRPD ------
    public static BufferedImage createPrpdImage(double[][] pulses, int width, int height) {
        int left = 70;
        int right = 25;
        int top = 30;
        int bottom = 55;

        int plotW = width - left - right;
        int plotH = height - top - bottom;

        int binsPhase = plotW;
        int binsAmp = plotH;

        if (plotW <= 0 || plotH <= 0) {
            throw new IllegalArgumentException("Image too small");
        }

        if (pulses == null || pulses.length == 0) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }

        double ampMax = percentileAbsAmplitude(pulses, 99.5);
        if (ampMax <= 0.0) {
            ampMax = 1.0;
        }

        int[][] hist = new int[binsPhase][binsAmp];
        int maxCount = 1;

        for (double[] p : pulses) {
            if (p.length < 3) {
                continue;
            }

            double phase = p[1] % 360.0;
            if (phase < 0.0) {
                phase += 360.0;
            }

            double amp = Math.abs(p[2]);

            int xb = (int) Math.floor(phase / 360.0 * binsPhase);
            int yb = (int) Math.floor(amp / ampMax * binsAmp);

            if (xb < 0 || xb >= binsPhase) {
                continue;
            }
            if (yb < 0 || yb >= binsAmp) {
                continue;
            }

            hist[xb][yb]++;
            if (hist[xb][yb] > maxCount) {
                maxCount = hist[xb][yb];
            }
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background
        g.setColor(Color.WHITE);
        g.fillRect(left, top, plotW, plotH);

        // grid
        g.setColor(new Color(225, 225, 225));
        for (int deg = 0; deg <= 360; deg += 45) {
            int x = left + (int) Math.round(deg / 360.0 * plotW);
            g.drawLine(x, top, x, top + plotH);
        }

        for (int i = 0; i <= 5; i++) {
            int y = top + (int) Math.round(i / 5.0 * plotH);
            g.drawLine(left, y, left + plotW, y);
        }

        // histogram
        for (int xb = 0; xb < binsPhase; xb++) {
            for (int yb = 0; yb < binsAmp; yb++) {
                int c = hist[xb][yb];
                if (c == 0) {
                    continue;
                }

                double v = Math.log1p(c) / Math.log1p(maxCount);

                int x0 = left + (int) Math.floor(xb * plotW / (double) binsPhase);
                int x1 = left + (int) Math.floor((xb + 1) * plotW / (double) binsPhase);

                int y0 = top + plotH - (int) Math.floor((yb + 1) * plotH / (double) binsAmp);
                int y1 = top + plotH - (int) Math.floor(yb * plotH / (double) binsAmp);

                int w = Math.max(1, x1 - x0);
                int h = Math.max(1, y1 - y0);

                g.setColor(colorMap(v));
                g.fillRect(x0, y0, w, h);
            }
        }

        // axes
        g.setColor(Color.GRAY);
        g.drawRect(left, top, plotW, plotH);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 13));

        for (int deg = 0; deg <= 360; deg += 45) {
            int x = left + (int) Math.round(deg / 360.0 * plotW);
            g.drawString(Integer.toString(deg), x - 10, top + plotH + 20);
        }

        for (int i = 0; i <= 5; i++) {
            double a = i * ampMax / 5.0;
            int y = top + plotH - (int) Math.round(i / 5.0 * plotH);
            g.drawString(String.format(Locale.US, "%.3f", a), 8, y + 5);
        }

        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("PRPD", left, 20);

        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString("Phase [deg]", left + plotW / 2 - 40, height - 15);

        g.rotate(-Math.PI / 2);
        g.drawString("|Amplitude|", -top - plotH / 2 - 40, 20);
        g.rotate(Math.PI / 2);

        g.dispose();
        return img;
    }

    private static double percentileAbsAmplitude(double[][] pulses, double percentile) {
        double[] a = new double[pulses.length];

        int n = 0;
        for (double[] p : pulses) {
            if (p != null && p.length >= 3) {
                a[n++] = Math.abs(p[2]);
            }
        }

        if (n == 0) {
            return 1.0;
        }

        java.util.Arrays.sort(a, 0, n);

        double pos = percentile / 100.0 * (n - 1);
        int i = (int) Math.floor(pos);
        int j = Math.min(i + 1, n - 1);

        double frac = pos - i;
        return a[i] * (1.0 - frac) + a[j] * frac;
    }

    private static Color colorMap(double v) {
        v = Math.max(0.0, Math.min(1.0, v));

        int r, g, b;

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

        return new Color(r, g, b);
    }
 */
