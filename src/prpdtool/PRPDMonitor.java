package prpdtool;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.List;

public class PRPDMonitor extends JFrame {

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

    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("");

    // PRPD config
    private double f0 = 50;
    private double threshold = 0.012; //próg detekcji impulsu po odjęciu tła
    private double deadUs = 30; //martwy czas po wykryciu impulsu [µs]
    private double smoothUs = 200; // szerokość okna wygładzania tła [µs]

    // Data
    private List<Sample> tu;
    private double[][] pulses;
    private DynamicPRPDImageV1 prpd;

    public PRPDMonitor() {
        super("PRPDtool");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 1280);
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
        center = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                if (prpd != null) {
                    g.drawImage(prpd, 0, 0, this);
                }
            }
        };

        right.add(status);
        right.add(progress);

        // kolory poglądowe
        /*left.setBackground(Color.LIGHT_GRAY);
        center.setBackground(Color.WHITE);
        right.setBackground(Color.GRAY);
        bottom.setBackground(Color.DARK_GRAY);*/
        // --- środek + prawy (80% / 15%) ---
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
            verticalSplit.setDividerLocation(0.80);
            splitLeft.setDividerLocation(0.05);
            splitCenterRight.setDividerLocation(0.85);
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

    private double[][] listOfSamplesToArray(List<Sample> list) {
        double[][] out = new double[list.size()][3];

        for (int k = 0; k < list.size(); k++) {
            out[k] = list.get(k).s;
        }
        return out;
    }

    //---------------- Actions ------
    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser(getLastUsedDirectory());
        setFontRecursively(fileChooser, currentFont, 0);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File circuitFile = fileChooser.getSelectedFile();
            try {
                progress.setVisible(true);
                progress.setIndeterminate(true);
                progress.setVisible(true);
                progress.revalidate();
                progress.repaint();
                status.setText("Reading...");
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        tu = PRPDTools.readCsv(circuitFile.getAbsolutePath());
                        if (tu.get(0).s.length == 2) {
                            System.err.println("Read " + tu.size() + " samples");
                            pulses = PRPDTools.extractPulses(tu, f0, threshold, deadUs, smoothUs);
                        } else {
                            pulses = listOfSamplesToArray(tu);
                            tu = null;
                        }
                        System.err.println("Have " + pulses.length + " pulses");
                        prpd = new DynamicPRPDImageV1(pulses, center.getWidth(), center.getHeight());
                        center.repaint();
                        System.err.println("PRPD " + prpd.getWidth() + "x" + prpd.getHeight() + " created.");
                        return null;
                    }

                    @Override
                    protected void done() {
                        progress.setVisible(false);
                        setCursor(Cursor.getDefaultCursor());

                        try {
                            get(); // odbiera wyjątki z doInBackground()
                            status.setText("Done!");
                        } catch (Exception ex) {
                            status.setText("Error: " + ex.getMessage());
                        }
                    }
                };

                worker.execute();
                saveLastUsedDirectory(circuitFile.getParent());
                //message.setText("Circuit loaded from: " + circuitFile.getAbsolutePath() + "\n" + circ.noNodes() + " nodes");
            } catch (Exception e) {
                tu = null;
                pulses = null;
                JOptionPane.showMessageDialog(this, "Unable to load data from: " + circuitFile.getAbsolutePath());
            }
        }
    }

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PRPDMonitor().setVisible(true);
        });
    }
}
