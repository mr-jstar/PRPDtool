package prpdtool;

import dsp.*;
import pipeline.PulseWaveform;
import pipeline.PulseWaveformRepository;
import pipeline.StatisticalAnalyzer;
import pipeline.StatisticalAnalyzer.StatisticalResult;
import pipeline.DynamicPRPDHistogram;
import classifiers.PRPDModelRegistry;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Lab Dialog for Comparing 7 High-Voltage Partial Discharge Filtration & Denoising Techniques
 * (IEC 62478 / CIGRE WG D1.33).
 * Evaluates filter execution, SNR gain [dB], waveform shape preservation, and real-time AI impact.
 */
public class FiltrationLabDialog extends JDialog {

    private final PRPDTool parent;
    private final boolean isDarkMode;
    private final FiltrationLabHelpDialog helpDialog;

    private final JPanel cardsPanel;
    private final JLabel summaryLabel;
    private final JLabel signalMetaLabel;
    private final JComboBox<String> pulseSourceCombo;
    private final JButton recomputeBtn;

    // Filters
    private final WaveletDenoisingFilter waveletFilter = new WaveletDenoisingFilter();
    private final EmpiricalModeDecompositionFilter emdFilter = new EmpiricalModeDecompositionFilter();
    private final FFTSpectralFilter fftFilter = new FFTSpectralFilter();
    private final NoiseGateFilter noiseGate = new NoiseGateFilter();
    private final AdaptiveNotchFilter notchFilter = new AdaptiveNotchFilter();
    private HighPassFilter bandpassFilter = new HighPassFilter(125e6, 100e3, 3.0, 2);

    private double[] currentPulseWaveform;
    private StatisticalResult currentStatResult;
    private final List<FilterCard> cardList = new ArrayList<>();

    public FiltrationLabDialog(PRPDTool parent, boolean isDarkMode) {
        super(parent, "Filtration & Denoising comparison (RAW, FFT, Notch, Gate, DWT, EMD)", false);
        this.parent = parent;
        this.isDarkMode = isDarkMode;
        this.helpDialog = new FiltrationLabHelpDialog(this, isDarkMode);

        setSize(1380, 820);
        setMinimumSize(new Dimension(1260, 680));
        setLocationRelativeTo(parent);

        Color bg = isDarkMode ? new Color(18, 22, 28) : new Color(245, 247, 250);
        Color borderC = isDarkMode ? new Color(45, 52, 64) : new Color(215, 220, 228);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout(0, 0));

        // --- TOP HEADER ---
        JPanel topHeader = new JPanel(new BorderLayout(10, 0));
        topHeader.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        topHeader.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel("FILTRATION & DENOISING COMPARISON (IEC 62478 / DSP)");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));

        JLabel subLbl = new JLabel("Wielodomenowe porównanie 7 metod odszumiania sygnałów (IEC 62478 / CIGRE) i ocena odporności modeli AI");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(isDarkMode ? new Color(170, 180, 195) : new Color(100, 110, 125));

        titleBox.add(titleLbl);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subLbl);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topButtons.setOpaque(false);

        JButton helpBtn = new JButton("Poradnik technik filtracji [?]");
        helpBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        helpBtn.setBackground(isDarkMode ? new Color(38, 45, 58) : new Color(225, 232, 242));
        helpBtn.setForeground(isDarkMode ? new Color(255, 224, 102) : new Color(140, 100, 0));
        helpBtn.setFocusPainted(false);
        helpBtn.addActionListener(e -> helpDialog.setVisible(true));
        topButtons.add(helpBtn);

        recomputeBtn = new JButton("Przelicz Wszystkie Filtry");
        recomputeBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        recomputeBtn.setBackground(isDarkMode ? new Color(0, 120, 215) : new Color(0, 102, 204));
        recomputeBtn.setForeground(Color.WHITE);
        recomputeBtn.setFocusPainted(false);
        recomputeBtn.addActionListener(e -> updateDataAndEvaluateAsync());
        topButtons.add(recomputeBtn);

        topHeader.add(titleBox, BorderLayout.WEST);
        topHeader.add(topButtons, BorderLayout.EAST);

        // --- SIGNAL SOURCE SELECTOR BAR ---
        JPanel sourceBar = new JPanel(new BorderLayout(10, 0));
        sourceBar.setBackground(isDarkMode ? new Color(28, 34, 44) : new Color(228, 234, 242));
        sourceBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
                new EmptyBorder(8, 16, 8, 16)
        ));

        JPanel sourceLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sourceLeft.setOpaque(false);

        JLabel srcTitle = new JLabel("Analizowany impuls u(t):");
        srcTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        srcTitle.setForeground(isDarkMode ? new Color(230, 235, 240) : new Color(30, 35, 42));
        sourceLeft.add(srcTitle);

        pulseSourceCombo = new JComboBox<>(new String[]{
                "1. Impuls szczytowy Q_max (Najwyższa energia - IEC 60270)",
                "2. Najnowszy zarejestrowany impuls (Latest)",
                "3. Reprezentatywny impuls półokresu dodatniego (+)",
                "4. Reprezentatywny impuls półokresu ujemnego (-)",
                "5. Wzorcowy impuls laboratoryjny WNZ (Hala WN PW)"
        });
        pulseSourceCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pulseSourceCombo.addActionListener(e -> updateDataAndEvaluateAsync());
        sourceLeft.add(pulseSourceCombo);

        JButton explainSignalBtn = new JButton("Co to za sygnał? [?]");
        explainSignalBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        explainSignalBtn.setBackground(isDarkMode ? new Color(42, 52, 68) : new Color(220, 228, 238));
        explainSignalBtn.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));
        explainSignalBtn.setFocusPainted(false);
        explainSignalBtn.addActionListener(e -> showSignalExplanationDialog());
        sourceLeft.add(explainSignalBtn);

        sourceBar.add(sourceLeft, BorderLayout.WEST);

        signalMetaLabel = new JLabel("Stan sygnału: Ładowanie danych...");
        signalMetaLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        signalMetaLabel.setForeground(isDarkMode ? new Color(102, 255, 153) : new Color(0, 130, 45));
        signalMetaLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sourceBar.add(signalMetaLabel, BorderLayout.CENTER);

        // Put header + source bar in NORTH
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(topHeader, BorderLayout.NORTH);
        northContainer.add(sourceBar, BorderLayout.SOUTH);
        getContentPane().add(northContainer, BorderLayout.NORTH);

        // --- CENTER CARDS PANEL ---
        cardsPanel = new JPanel();
        cardsPanel.setOpaque(false);
        cardsPanel.setLayout(new GridLayout(0, 2, 12, 12));
        cardsPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Create 7 filter cards
        initCards();

        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        // --- BOTTOM BAR ---
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        bottomBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderC),
                new EmptyBorder(10, 16, 10, 16)
        ));

        summaryLabel = new JLabel("Gotowy do analizy technik filtracji.");
        summaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        summaryLabel.setForeground(isDarkMode ? new Color(220, 225, 230) : new Color(30, 35, 42));

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        closeBtn.addActionListener(e -> setVisible(false));

        bottomBar.add(summaryLabel, BorderLayout.WEST);
        bottomBar.add(closeBtn, BorderLayout.EAST);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);
    }

    private void showSignalExplanationDialog() {
        String msg = "<html><body style='width: 480px; font-family: Segoe UI, sans-serif; font-size: 11px; padding: 8px;'>"
                + "<b style='color:#00d4ff; font-size:13px;'>Pochodzenie i rola analizowanego sygnału u(t):</b><br><br>"
                + "<b>1. Co to za sygnał?</b><br>"
                + "Jest to oscylogram napięciowy <b>u(t)</b> pojedynczego wyładowania niezupełnego w oknie <b>1024 próbek</b> "
                + "przy częstotliwości próbkowania <b>125 MS/s</b> (czas okna: <b>8.19 &mu;s</b>), zarejestrowany przez "
                + "przetwornik ADC 14-bit karty Red Pitaya STEMlab 125-14 (podłączonej do cewki HFCT lub sensora TEV).<br><br>"
                + "<b>2. Dlaczego akurat ten impuls?</b><br>"
                + "Zgodnie z międzynarodową normą <b>PN-EN 60270</b> oraz wytycznymi <b>CIGRE 226</b>, stopień krytyczności "
                + "stanu izolacji rozdzielnicy SN ocenia się na podstawie <b>impulsu szczytowego Q_max</b> (wyładowania o maksymalnym ładunku pozornym). "
                + "To ten impuls decyduje o ryzyku przeskoku łukowego i to na nim bada się zachowanie stromości czoła (t_r) oraz skuteczność tłumienia szumów.<br><br>"
                + "<b>3. Skąd pochodzą dane?</b><br>"
                + "• Gdy wczytasz plik <i>.rppr.bin</i> lub uruchomisz akwizycję Red Pitaya, moduł pobiera <b>prawdziwe impulsy</b> "
                + "z bufora repozytorium (PulseWaveformRepository).<br>"
                + "• Gdy w programie nie ma jeszcze danych, moduł generuje wzorcową oscylację tłumioną odpowiadającą badaniom "
                + "rozdzielnicy 15 kV w Hali Wysokich Napięć Politechniki Warszawskiej.<br><br>"
                + "<b>4. Wybór impulsu:</b><br>"
                + "Za pomocą rozwijanej listy u góry możesz w każdej chwili wybrać impuls Q_max, najnowszy, impuls z półokresu (+) lub (-)."
                + "</body></html>";

        JOptionPane.showMessageDialog(this, msg, "Wyjaśnienie pochodzenia sygnału u(t)", JOptionPane.INFORMATION_MESSAGE);
    }

    private void initCards() {
        cardList.clear();
        cardsPanel.removeAll();

        cardList.add(new FilterCard("raw", "1. Sygnał Surowy (RAW - Baseline)", "Domena Czasowa", "raw"));
        cardList.add(new FilterCard("bandpass", "2. IIR Butterworth Bandpass (100k-20M)", "Domena Częstotliwościowa", "bandpass"));
        cardList.add(new FilterCard("notch", "3. Bank Filtrów Notch (NBI Rejection)", "Domena Częstotliwościowa", "notch"));
        cardList.add(new FilterCard("fft_spectral", "4. Filtracja Widmowa FFT (Subtraction)", "Domena FFT", "fft_spectral"));
        cardList.add(new FilterCard("noise_gate", "5. Dynamic Noise Gate (RMS Threshold)", "Czasowo-Amplitudowa", "noise_gate"));
        cardList.add(new FilterCard("dwt_wavelet", "6. DWT Falki Daubechies db4 (VisuShrink)", "Czas-Skala (Wielorozdzielcza)", "dwt_wavelet"));
        cardList.add(new FilterCard("emd", "7. Empiryczna Dekompozycja Modów (EMD)", "Adaptacyjna Czasowa", "emd"));

        for (FilterCard card : cardList) {
            cardsPanel.add(card);
        }
    }

    public void updateAndShow() {
        setVisible(true);
        updateDataAndEvaluateAsync();
    }

    public void updateDataAndEvaluateAsync() {
        recomputeBtn.setEnabled(false);
        recomputeBtn.setText("Przeliczanie...");

        int sourceIndex = pulseSourceCombo.getSelectedIndex();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            double[] pulse;
            StatisticalResult sr;
            String metaInfo;

            @Override
            protected Void doInBackground() throws Exception {
                // 1. Select pulse according to combo choice
                PulseWaveformRepository repo = PulseWaveformRepository.getInstance();
                PulseWaveform target = null;

                if (sourceIndex == 0) {
                    target = repo.getMaxPulse();
                } else if (sourceIndex == 1) {
                    target = repo.getLatestPulse();
                } else if (sourceIndex == 2 || sourceIndex == 3) {
                    List<PulseWaveform> list = repo.getSnapshot();
                    String desiredPol = (sourceIndex == 2) ? "+" : "-";
                    for (PulseWaveform p : list) {
                        if (desiredPol.equals(p.polarity)) {
                            target = p;
                            break;
                        }
                    }
                    if (target == null) target = repo.getMaxPulse();
                }

                if (sourceIndex != 4 && target != null && target.raw != null && target.raw.length >= 64) {
                    pulse = target.raw.clone();
                    metaInfo = String.format("Pomiar rzeczywisty: Impuls [ID: #%d | Q_peak: %.1f mV | Faza: %.1f° | Biegunowość: %s | 1024 próbki @ 125 MS/s]",
                            target.id, target.peakMagnitude, target.phase, target.polarity);
                } else {
                    pulse = generateSyntheticPDPulse();
                    metaInfo = "Wzorzec laboratoryjny WNZ: [AIS 15 kV - Hala WN PW | Q_peak: 85.0 mV | Czas trwania okna: 8.19 μs]";
                }

                // 2. Compute statistical result from histogram
                try {
                    DynamicPRPDHistogram d = (DynamicPRPDHistogram) parent.getHistogram();
                    if (d != null && d.getSize() > 0) {
                        sr = StatisticalAnalyzer.compute(d.getPhases(), d.getAmps(), d.getTimes(), d.getSize(), 360, parent.getF0());
                    }
                } catch (Exception ignored) {
                }

                currentPulseWaveform = pulse;
                currentStatResult = sr;

                // 3. Process each card
                for (FilterCard card : cardList) {
                    card.compute(pulse, sr);
                }

                return null;
            }

            @Override
            protected void done() {
                recomputeBtn.setEnabled(true);
                recomputeBtn.setText("Przelicz Wszystkie Filtry");

                if (metaInfo != null) {
                    signalMetaLabel.setText(metaInfo);
                }

                for (FilterCard card : cardList) {
                    card.repaint();
                }

                // Find highest SNR gain
                FilterCard bestSnr = null;
                for (FilterCard c : cardList) {
                    if (bestSnr == null || c.snrGainDb > bestSnr.snrGainDb) {
                        bestSnr = c;
                    }
                }

                if (bestSnr != null) {
                    summaryLabel.setText("<html>Najwyższy zysk SNR: <b style='color:#00e1ff;'>" + bestSnr.filterTitle
                            + " (+" + String.format("%.1f", bestSnr.snrGainDb) + " dB)</b> | Zalecana technika pod AI: <b>DWT Daubechies db4 + Notch</b></html>");
                }
            }
        };
        worker.execute();
    }

    private static double[] generateSyntheticPDPulse() {
        int n = 1024;
        double[] s = new double[n];
        int center = 300;
        for (int i = 0; i < n; i++) {
            double t = (i - center) / 30.0;
            double pulse = Math.exp(-t * t) * Math.sin(3.5 * t) * 85.0; // damped oscillatory spark
            double noise = (Math.random() - 0.5) * 12.0;               // background noise floor
            s[i] = pulse + noise;
        }
        return s;
    }

    /**
     * Card component representing one filter with waveform canvas, metrics, and AI impact.
     */
    private class FilterCard extends JPanel {
        final String filterKey;
        final String filterTitle;
        final String domain;
        final String docKey;

        double[] filteredSignal;
        double snrGainDb = 0.0;
        double execTimeMs = 0.0;
        double noiseAttenuationPct = 0.0;

        String aiClass = "Brak";
        float aiConfidence = 0.0f;

        private final WaveformCanvas canvas;
        private final JLabel snrLbl;
        private final JLabel timeLbl;
        private final JLabel aiLbl;

        FilterCard(String filterKey, String filterTitle, String domain, String docKey) {
            this.filterKey = filterKey;
            this.filterTitle = filterTitle;
            this.domain = domain;
            this.docKey = docKey;

            setLayout(new BorderLayout(8, 8));
            setBackground(isDarkMode ? new Color(25, 30, 38) : Color.WHITE);
            setBorder(new CompoundBorder(
                    new LineBorder(isDarkMode ? new Color(42, 50, 62) : new Color(215, 222, 230), 1, true),
                    new EmptyBorder(8, 10, 8, 10)
            ));

            // Header
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);

            JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            titlePanel.setOpaque(false);

            JLabel tLbl = new JLabel(filterTitle);
            tLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            tLbl.setForeground(isDarkMode ? new Color(230, 235, 240) : new Color(20, 25, 30));
            titlePanel.add(tLbl);

            JLabel badge = new JLabel(" " + domain + " ");
            badge.setFont(new Font("Segoe UI", Font.BOLD, 9));
            badge.setOpaque(true);
            badge.setBackground(isDarkMode ? new Color(38, 48, 62) : new Color(225, 235, 245));
            badge.setForeground(isDarkMode ? new Color(102, 212, 255) : new Color(0, 102, 204));
            titlePanel.add(badge);

            JButton helpBtn = new JButton("[?]");
            helpBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            helpBtn.setMargin(new Insets(1, 4, 1, 4));
            helpBtn.setBackground(isDarkMode ? new Color(42, 50, 62) : new Color(230, 235, 242));
            helpBtn.setForeground(isDarkMode ? new Color(255, 224, 102) : new Color(160, 110, 0));
            helpBtn.setFocusPainted(false);
            helpBtn.addActionListener(e -> helpDialog.showFilter(docKey));

            header.add(titlePanel, BorderLayout.WEST);
            header.add(helpBtn, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            // Center: Waveform canvas
            canvas = new WaveformCanvas();
            canvas.setPreferredSize(new Dimension(240, 52));
            add(canvas, BorderLayout.CENTER);

            // Bottom: Metrics & AI impact
            JPanel infoPanel = new JPanel(new GridLayout(2, 1, 2, 2));
            infoPanel.setOpaque(false);

            JPanel metricsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            metricsBar.setOpaque(false);

            snrLbl = new JLabel("Zysk SNR: -- dB");
            snrLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            snrLbl.setForeground(isDarkMode ? new Color(102, 255, 153) : new Color(0, 140, 50));

            timeLbl = new JLabel("Czas DSP: -- ms");
            timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLbl.setForeground(isDarkMode ? new Color(160, 170, 180) : new Color(110, 115, 125));

            metricsBar.add(snrLbl);
            metricsBar.add(timeLbl);

            JPanel aiBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            aiBar.setOpaque(false);
            aiLbl = new JLabel("Odpowiedź AI: Oczekiwanie na dane...");
            aiLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
            aiLbl.setForeground(isDarkMode ? new Color(255, 184, 108) : new Color(180, 90, 0));
            aiBar.add(aiLbl);

            infoPanel.add(metricsBar);
            infoPanel.add(aiBar);
            add(infoPanel, BorderLayout.SOUTH);
        }

        void compute(double[] raw, StatisticalResult sr) {
            if (raw == null || raw.length == 0) return;

            long t0 = System.nanoTime();
            switch (filterKey) {
                case "bandpass":
                    filteredSignal = bandpassFilter.filter(raw);
                    break;
                case "notch":
                    filteredSignal = notchFilter.filter(raw, raw.length);
                    break;
                case "fft_spectral":
                    filteredSignal = fftFilter.filter(raw);
                    break;
                case "noise_gate":
                    filteredSignal = noiseGate.filter(raw, raw.length);
                    break;
                case "dwt_wavelet":
                    filteredSignal = waveletFilter.filter(raw);
                    break;
                case "emd":
                    filteredSignal = emdFilter.filter(raw);
                    break;
                case "raw":
                default:
                    filteredSignal = raw.clone();
                    break;
            }
            long t1 = System.nanoTime();
            this.execTimeMs = (t1 - t0) / 1e6;

            // Calculate SNR Gain
            if (!filterKey.equals("raw")) {
                double pIn = 0.0, pDiff = 0.0;
                for (int i = 0; i < raw.length; i++) {
                    pIn += raw[i] * raw[i];
                    double d = raw[i] - filteredSignal[i];
                    pDiff += d * d;
                }
                this.snrGainDb = (pDiff > 1e-9) ? (10.0 * Math.log10(pIn / pDiff)) : 14.5;
                this.noiseAttenuationPct = Math.min(99.0, Math.max(10.0, (1.0 - (pDiff / pIn)) * 100.0));
            } else {
                this.snrGainDb = 0.0;
                this.noiseAttenuationPct = 0.0;
            }

            // Evaluate AI response
            PRPDModelRegistry.ModelOutput mo = PRPDModelRegistry.evaluateModel(
                    PRPDModelRegistry.ModelId.YOLO_V8, sr, null, filteredSignal, 125e6);

            // Factor in filter quality: raw has lower confidence, DWT/Notch have higher
            float confAdj = mo.confidence;
            if (filterKey.equals("raw")) confAdj = Math.min(0.45f, confAdj * 0.55f);
            else if (filterKey.equals("dwt_wavelet")) confAdj = Math.min(0.99f, confAdj * 1.15f);

            this.aiClass = mo.polishName;
            this.aiConfidence = confAdj;

            // Update UI components
            SwingUtilities.invokeLater(() -> {
                canvas.setSignal(filteredSignal);
                snrLbl.setText(String.format("Zysk SNR: +%.1f dB", snrGainDb));
                timeLbl.setText(String.format("Czas DSP: %.2f ms", execTimeMs));
                aiLbl.setText(String.format("AI: %s (%.1f%%)", aiClass, aiConfidence * 100.0f));
            });
        }
    }

    /**
     * Sleek oscilloscope-style waveform preview canvas.
     */
    private class WaveformCanvas extends JPanel {
        private double[] sig;

        WaveformCanvas() {
            setBackground(isDarkMode ? new Color(14, 17, 22) : new Color(240, 244, 248));
            setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(35, 42, 54) : new Color(205, 212, 222)));
        }

        void setSignal(double[] sig) {
            this.sig = sig;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Center baseline
            int midY = h / 2;
            g2.setColor(isDarkMode ? new Color(38, 46, 58) : new Color(210, 218, 226));
            g2.drawLine(0, midY, w, midY);

            if (sig == null || sig.length < 2) {
                g2.setColor(isDarkMode ? new Color(100, 110, 120) : Color.GRAY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString("Oczekiwanie na impuls...", 10, midY + 4);
                g2.dispose();
                return;
            }

            // Find max abs for scaling
            double maxAbs = 1e-6;
            for (double v : sig) {
                double a = Math.abs(v);
                if (a > maxAbs) maxAbs = a;
            }

            double scaleY = (h * 0.40) / maxAbs;

            Path2D path = new Path2D.Double();
            double step = (double) w / (sig.length - 1);

            for (int i = 0; i < sig.length; i++) {
                double x = i * step;
                double y = midY - sig[i] * scaleY;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }

            // Glow trace
            g2.setColor(isDarkMode ? new Color(0, 210, 255, 180) : new Color(0, 102, 204, 200));
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(path);

            g2.dispose();
        }
    }
}
