package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Interactive Guide and Help Dialog for Statistical Descriptors of Partial Discharges.
 * Explains mathematical definitions, physical meaning, and practical defect interpretations.
 */
public class DescriptorHelpDialog extends JDialog {

    private final JList<StatisticalDescriptorDoc.Entry> entryList;
    private final JEditorPane htmlPane;
    private final boolean isDarkMode;

    public DescriptorHelpDialog(Dialog owner, boolean isDarkMode) {
        super(owner, "Objaśnienie parametrów statystycznych WNZ (IEC 60270 / CIGRE)", false);
        this.isDarkMode = isDarkMode;

        setSize(880, 640);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(owner);

        // Model & List
        List<StatisticalDescriptorDoc.Entry> entries = StatisticalDescriptorDoc.getAll();
        DefaultListModel<StatisticalDescriptorDoc.Entry> listModel = new DefaultListModel<>();
        for (StatisticalDescriptorDoc.Entry e : entries) {
            listModel.addElement(e);
        }

        entryList = new JList<>(listModel);
        entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        entryList.setBackground(isDarkMode ? new Color(28, 30, 34) : new Color(245, 247, 250));
        entryList.setForeground(isDarkMode ? new Color(230, 235, 240) : new Color(20, 20, 20));
        entryList.setSelectionBackground(isDarkMode ? new Color(45, 75, 110) : new Color(205, 225, 255));
        entryList.setSelectionForeground(Color.WHITE);

        entryList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof StatisticalDescriptorDoc.Entry) {
                    StatisticalDescriptorDoc.Entry e = (StatisticalDescriptorDoc.Entry) value;
                    lbl.setText("<html><b style='color:" + (isSelected ? "#ffffff" : (isDarkMode ? "#66d4ff" : "#0066cc")) + ";'>[?] "
                            + e.nameEn + "</b><br><span style='font-size:10px; color:" + (isSelected ? "#dddddd" : "#888888") + ";'>"
                            + e.namePl + "</span></html>");
                    lbl.setBorder(new EmptyBorder(6, 8, 6, 8));
                }
                return lbl;
            }
        });

        JScrollPane listScroll = new JScrollPane(entryList);
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

        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                StatisticalDescriptorDoc.Entry selected = entryList.getSelectedValue();
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
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        bottomBar.setBackground(isDarkMode ? new Color(28, 30, 34) : new Color(240, 242, 245));
        JButton closeBtn = new JButton("Zamknij");
        closeBtn.addActionListener(e -> setVisible(false));
        bottomBar.add(closeBtn);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        if (!listModel.isEmpty()) {
            entryList.setSelectedIndex(0);
        }
    }

    /**
     * Shows dialog with a specific descriptor selected.
     */
    public void showDescriptor(String keyOrName) {
        if (keyOrName != null) {
            StatisticalDescriptorDoc.Entry target = StatisticalDescriptorDoc.get(keyOrName);
            if (target == null) {
                target = StatisticalDescriptorDoc.findByRowName(keyOrName);
            }
            if (target != null) {
                entryList.setSelectedValue(target, true);
            }
        }
        setVisible(true);
    }
}
