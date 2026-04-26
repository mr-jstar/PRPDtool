package parallelprpd.pipeline;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class PRPDFrame extends JFrame {

    private ImagePanel prpdPanel;
    private ImagePanel envelopePanel;
    private JPanel infoPanel;

    private DynamicPRPDHistogram histogram;
    private DynamicEnvelopeImage envelope;
    private PRPDPipeline pipeline;

    public PRPDFrame() {
        super("PRPD Viewer");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1250, 1100);
        setLocationRelativeTo(null);

        createMenu();
        createLayout();
    }

    private void createLayout() {
        histogram = new DynamicPRPDHistogram(
                1200, 650,
                360, 200,
                0.0, 0.12
        );

        envelope = new DynamicEnvelopeImage(
                600, 350,
                0.0, 10.0
        );

        prpdPanel = new ImagePanel(histogram.getImage());
        prpdPanel.setPreferredSize(new Dimension(1200, 650));

        infoPanel = new JPanel(new BorderLayout());
        infoPanel.setPreferredSize(new Dimension(600, 350));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Info"));

        envelopePanel = new ImagePanel(envelope.getImage());
        envelopePanel.setPreferredSize(new Dimension(600, 350));
        envelopePanel.setBorder(BorderFactory.createTitledBorder("Envelope"));

        JPanel bottomPanel = new JPanel(new GridLayout(1, 2));
        bottomPanel.setPreferredSize(new Dimension(1200, 350));
        bottomPanel.add(infoPanel);
        bottomPanel.add(envelopePanel);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(prpdPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private void createMenu() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open");
        JMenuItem exitItem = new JMenuItem("Exit");

        openItem.addActionListener(e -> openFile());

        exitItem.addActionListener(e -> {
            stopPipeline();
            dispose();
            System.exit(0);
        });

        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();

        try {
            String[] lasttu = prpdtool.PRPDTools.readLastLineUtf8(file.getAbsolutePath()).trim().split("[,;\\s]+");

            stopPipeline();

            histogram = new DynamicPRPDHistogram(
                    1200, 650,
                    360, 200,
                    0.0, 0.12
            );

            // Na razie zakres czasu przykładowy.
            // Docelowo tMax możesz wziąć z ostatniej linii pliku.
            envelope = new DynamicEnvelopeImage(
                    600, 350,
                    0.0, Double.parseDouble(lasttu[0])
            );

            prpdPanel.setImage(histogram.getImage());
            envelopePanel.setImage(envelope.getImage());

            prpdPanel.repaint();
            envelopePanel.repaint();

            PRPDExtractorCore extractor = new PRPDExtractorCore(
                    50.0,
                    0.0,
                    0.012,
                    30.0,
                    200.0
            );

            pipeline = new PRPDPipeline(
                    file.getAbsolutePath(),
                    5_000_000,
                    2,
                    extractor,
                    new PRPDPipelineListener() {
                @Override
                public void bufferRead(Buffer buffer) {
                    envelope.addBuffer(buffer);
                    envelopePanel.repaint();
                }

                @Override
                public void pulsesReady(Pulses pulses) {
                    histogram.addPulses(pulses);
                    prpdPanel.repaint();
                }

                @Override
                public void finished() {
                    setTitle("PRPD Viewer - finished: " + file.getName());
                }

                @Override
                public void error(Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(
                            PRPDFrame.this,
                            ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
            );

            setTitle("PRPD Viewer - " + file.getName());
            pipeline.start();
        } catch (Exception ex) {
            System.err.println("Bad file");
        }
    }

    private void stopPipeline() {
        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }
    }

    static class ImagePanel extends JPanel {

        private BufferedImage image;

        public ImagePanel(BufferedImage image) {
            this.image = image;
            setBackground(Color.WHITE);
        }

        public void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (image == null) {
                return;
            }

            g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PRPDFrame().setVisible(true));
    }
}
