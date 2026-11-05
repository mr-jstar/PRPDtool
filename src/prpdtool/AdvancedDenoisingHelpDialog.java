package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Interactive Guide and Documentation Dialog for Advanced DSP Denoising & Notch Filters.
 * Explains architecture, physical meaning, field tuning, optimal parameter baselines,
 * and step-by-step diagnostic workflow (IEC 62478 / CIGRE WG D1.33).
 */
public class AdvancedDenoisingHelpDialog extends JDialog {

    private final JList<AdvancedDenoisingDoc.Chapter> chapterList;
    private final JEditorPane htmlPane;
    private final boolean isDarkMode;

    public AdvancedDenoisingHelpDialog(Dialog owner, boolean isDarkMode) {
        super(owner, "Przewodnik po Odszumianiu DSP i Filtrach Notch (IEC 62478 / CIGRE)", false);
        this.isDarkMode = isDarkMode;

        setSize(880, 640);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(owner);

        // List model
        List<AdvancedDenoisingDoc.Chapter> chapters = AdvancedDenoisingDoc.getChapters();
        DefaultListModel<AdvancedDenoisingDoc.Chapter> listModel = new DefaultListModel<>();
        for (AdvancedDenoisingDoc.Chapter c : chapters) {
            listModel.addElement(c);
        }

        chapterList = new JList<>(listModel);
        chapterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chapterList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chapterList.setBackground(isDarkMode ? new Color(26, 28, 33) : new Color(245, 247, 250));
        chapterList.setForeground(isDarkMode ? new Color(230, 235, 240) : new Color(20, 20, 20));
        chapterList.setSelectionBackground(isDarkMode ? new Color(45, 75, 110) : new Color(205, 225, 255));
        chapterList.setSelectionForeground(Color.WHITE);

        chapterList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AdvancedDenoisingDoc.Chapter) {
                    AdvancedDenoisingDoc.Chapter c = (AdvancedDenoisingDoc.Chapter) value;
                    lbl.setText("<html><b style='color:" + (isSelected ? "#ffffff" : (isDarkMode ? "#66d4ff" : "#0066cc")) + "; font-size:11px;'>[?] "
                            + c.titlePl + "</b><br><span style='font-size:10px; color:" + (isSelected ? "#dddddd" : "#888888") + ";'>"
                            + c.subtitle + "</span></html>");
                    lbl.setBorder(new EmptyBorder(6, 8, 6, 8));
                }
                return lbl;
            }
        });

        JScrollPane listScroll = new JScrollPane(chapterList);
        listScroll.setPreferredSize(new Dimension(280, 500));
        listScroll.setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(55, 60, 68) : new Color(200, 205, 215)));

        // HTML Content Pane
        htmlPane = new JEditorPane();
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);
        htmlPane.setBackground(isDarkMode ? new Color(22, 24, 28) : Color.WHITE);
        htmlPane.setCaretPosition(0);

        JScrollPane htmlScroll = new JScrollPane(htmlPane);
        htmlScroll.setBorder(BorderFactory.createLineBorder(isDarkMode ? new Color(55, 60, 68) : new Color(200, 205, 215)));

        chapterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                AdvancedDenoisingDoc.Chapter selected = chapterList.getSelectedValue();
                if (selected != null) {
                    htmlPane.setText(selected.getFullHtml(isDarkMode));
                    htmlPane.setCaretPosition(0);
                }
            }
        });

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, htmlScroll);
        splitPane.setDividerLocation(280);
        splitPane.setDividerSize(6);
        splitPane.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Bottom button panel
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomBar.setBackground(isDarkMode ? new Color(28, 30, 34) : new Color(240, 242, 245));
        JButton closeBtn = new JButton("Zamknij");
        closeBtn.addActionListener(e -> setVisible(false));
        bottomBar.add(closeBtn);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        if (!listModel.isEmpty()) {
            chapterList.setSelectedIndex(0);
        }
    }

    /**
     * Shows dialog and scrolls to specific topic.
     */
    public void showTopic(String topicKey) {
        if (topicKey != null) {
            AdvancedDenoisingDoc.Chapter target = AdvancedDenoisingDoc.get(topicKey);
            if (target != null) {
                chapterList.setSelectedValue(target, true);
            }
        }
        setVisible(true);
    }
}
