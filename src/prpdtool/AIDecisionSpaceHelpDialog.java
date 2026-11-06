package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Interactive Guide and Help Dialog for AI Decision Space & XAI Inspector
 * (CIGRE 226 / IEC 60270).
 */
public class AIDecisionSpaceHelpDialog extends JDialog {

    private final JList<AIDecisionSpaceDoc.Entry> entryList;
    private final JEditorPane htmlPane;
    private final boolean isDarkMode;

    public AIDecisionSpaceHelpDialog(Dialog owner, boolean isDarkMode) {
        super(owner, "Przewodnik po Przestrzeni Decyzyjnej AI i Wyjaśnialnym AI (XAI)", false);
        this.isDarkMode = isDarkMode;

        setSize(1060, 680);
        setMinimumSize(new Dimension(820, 520));
        setLocationRelativeTo(owner);

        // List model
        List<AIDecisionSpaceDoc.Entry> entries = AIDecisionSpaceDoc.getAll();
        DefaultListModel<AIDecisionSpaceDoc.Entry> listModel = new DefaultListModel<>();
        for (AIDecisionSpaceDoc.Entry e : entries) {
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
                if (value instanceof AIDecisionSpaceDoc.Entry) {
                    AIDecisionSpaceDoc.Entry e = (AIDecisionSpaceDoc.Entry) value;
                    lbl.setText("<html><b style='color:" + (isSelected ? "#ffffff" : (isDarkMode ? "#66d4ff" : "#0066cc")) + ";'>[?] "
                            + e.titlePl + "</b><br><span style='font-size:10px; color:" + (isSelected ? "#dddddd" : "#888888") + ";'>"
                            + e.titleEn + " (" + e.category + ")</span></html>");
                    lbl.setBorder(new EmptyBorder(6, 8, 6, 8));
                }
                return lbl;
            }
        });

        JScrollPane listScroll = new JScrollPane(entryList);
        listScroll.setPreferredSize(new Dimension(380, 500));
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
                AIDecisionSpaceDoc.Entry selected = entryList.getSelectedValue();
                if (selected != null) {
                    htmlPane.setText(selected.getFullHtml(isDarkMode));
                    htmlPane.setCaretPosition(0);
                }
            }
        });

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, htmlScroll);
        splitPane.setDividerLocation(380);
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

    public void showTopic(String key) {
        if (key != null) {
            AIDecisionSpaceDoc.Entry target = AIDecisionSpaceDoc.get(key);
            if (target != null) {
                entryList.setSelectedValue(target, true);
            }
        }
        setVisible(true);
    }
}
