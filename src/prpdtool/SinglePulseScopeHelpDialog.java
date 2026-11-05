package prpdtool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Guide and Documentation Dialog for Single Pulse Waveform & FFT Scope.
 * Features a search filter, category-styled topic list, and rich HTML viewer.
 */
public class SinglePulseScopeHelpDialog extends JDialog {

    private final JList<SinglePulseScopeDoc.Entry> topicList;
    private final DefaultListModel<SinglePulseScopeDoc.Entry> listModel;
    private final JEditorPane htmlPane;
    private final JTextField searchField;
    private final boolean isDarkMode;
    private final List<SinglePulseScopeDoc.Entry> allEntries;

    public SinglePulseScopeHelpDialog(Window owner, boolean isDarkMode) {
        super(owner, "Przewodnik i interpretacja parametrów impulsu WNZ (IEC 60270 / CIGRE)", ModalityType.MODELESS);
        this.isDarkMode = isDarkMode;

        setSize(880, 640);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(owner);

        allEntries = SinglePulseScopeDoc.getAll();
        listModel = new DefaultListModel<>();
        for (SinglePulseScopeDoc.Entry e : allEntries) {
            listModel.addElement(e);
        }

        Color bg = isDarkMode ? new Color(24, 26, 30) : new Color(245, 247, 250);
        Color panelBg = isDarkMode ? new Color(30, 33, 38) : Color.WHITE;
        Color borderCol = isDarkMode ? new Color(55, 60, 68) : new Color(210, 215, 225);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout());

        // LEFT PANEL (SEARCH + LIST)
        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(280, 500));

        // Search Bar
        JPanel searchBox = new JPanel(new BorderLayout(6, 0));
        searchBox.setOpaque(false);
        searchBox.setBorder(new EmptyBorder(8, 8, 4, 8));

        JLabel searchIcon = new JLabel("Szukaj: ");
        searchIcon.setFont(new Font("Segoe UI", Font.BOLD, 11));
        searchIcon.setForeground(isDarkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Szukaj parametru, wykresu, normy...");
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override
            public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override
            public void changedUpdate(DocumentEvent e) { filterList(); }
        });

        searchBox.add(searchIcon, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);
        leftPanel.add(searchBox, BorderLayout.NORTH);

        // Topic List
        topicList = new JList<>(listModel);
        topicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        topicList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        topicList.setBackground(isDarkMode ? new Color(28, 30, 34) : new Color(250, 252, 255));
        topicList.setForeground(isDarkMode ? new Color(230, 235, 240) : new Color(20, 20, 20));
        topicList.setSelectionBackground(isDarkMode ? new Color(45, 75, 110) : new Color(210, 230, 255));
        topicList.setSelectionForeground(isDarkMode ? Color.WHITE : new Color(0, 50, 120));

        topicList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SinglePulseScopeDoc.Entry) {
                    SinglePulseScopeDoc.Entry e = (SinglePulseScopeDoc.Entry) value;
                    String titleCol = isSelected ? (isDarkMode ? "#ffffff" : "#003366") : (isDarkMode ? "#00e1ff" : "#0284c7");
                    String catCol = isSelected ? (isDarkMode ? "#e0e0e0" : "#555555") : (isDarkMode ? "#94a3b8" : "#64748b");

                    lbl.setText("<html><b style='color:" + titleCol + "; font-size: 11px;'>[?] "
                            + e.titlePl + "</b><br><span style='font-size: 10px; color:" + catCol + ";'>"
                            + e.category + " &bull; " + e.subtitleEn + "</span></html>");
                    lbl.setBorder(new EmptyBorder(6, 8, 6, 8));
                }
                return lbl;
            }
        });

        JScrollPane listScroll = new JScrollPane(topicList);
        listScroll.setBorder(BorderFactory.createLineBorder(borderCol));
        leftPanel.add(listScroll, BorderLayout.CENTER);

        // RIGHT PANEL (HTML VIEWER)
        htmlPane = new JEditorPane();
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);
        htmlPane.setBackground(isDarkMode ? new Color(26, 29, 34) : Color.WHITE);
        htmlPane.setCaretPosition(0);

        JScrollPane htmlScroll = new JScrollPane(htmlPane);
        htmlScroll.setBorder(BorderFactory.createLineBorder(borderCol));
        htmlScroll.getVerticalScrollBar().setUnitIncrement(16);

        topicList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SinglePulseScopeDoc.Entry selected = topicList.getSelectedValue();
                if (selected != null) {
                    htmlPane.setText(selected.getFullHtml(isDarkMode));
                    htmlPane.setCaretPosition(0);
                }
            }
        });

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, htmlScroll);
        splitPane.setDividerLocation(280);
        splitPane.setDividerSize(6);
        splitPane.setBorder(new EmptyBorder(8, 8, 8, 8));

        getContentPane().add(splitPane, BorderLayout.CENTER);

        // BOTTOM BAR
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        bottomBar.setBackground(panelBg);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderCol));

        JLabel infoLbl = new JLabel("Wskazówka: Możesz trzymać to okno otwarte obok wykresu podczas inspekcji impulsów.");
        infoLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        infoLbl.setForeground(isDarkMode ? new Color(150, 165, 180) : new Color(100, 110, 120));

        JButton closeBtn = new JButton("Zamknij");
        closeBtn.addActionListener(e -> setVisible(false));

        JPanel botContainer = new JPanel(new BorderLayout());
        botContainer.setBackground(panelBg);
        botContainer.setBorder(new EmptyBorder(0, 10, 0, 0));
        botContainer.add(infoLbl, BorderLayout.WEST);
        botContainer.add(bottomBar, BorderLayout.EAST);
        bottomBar.add(closeBtn);

        getContentPane().add(botContainer, BorderLayout.SOUTH);

        if (!listModel.isEmpty()) {
            topicList.setSelectedIndex(0);
        }
    }

    private void filterList() {
        String filter = searchField.getText().trim().toLowerCase();
        listModel.clear();
        for (SinglePulseScopeDoc.Entry e : allEntries) {
            if (filter.isEmpty()
                    || e.titlePl.toLowerCase().contains(filter)
                    || e.subtitleEn.toLowerCase().contains(filter)
                    || e.category.toLowerCase().contains(filter)
                    || e.shortSummary.toLowerCase().contains(filter)
                    || e.key.toLowerCase().contains(filter)) {
                listModel.addElement(e);
            }
        }
        if (!listModel.isEmpty()) {
            topicList.setSelectedIndex(0);
        }
    }

    /**
     * Shows dialog and scrolls to the selected topic.
     * @param topicKey key matching Entry.key or null for overview.
     */
    public void showTopic(String topicKey) {
        if (topicKey != null) {
            String targetKey = topicKey.trim().toUpperCase();
            // Clear filter first to make sure target is visible in listModel
            searchField.setText("");
            filterList();

            SinglePulseScopeDoc.Entry target = SinglePulseScopeDoc.get(targetKey);
            if (target != null) {
                topicList.setSelectedValue(target, true);
                htmlPane.setText(target.getFullHtml(isDarkMode));
                htmlPane.setCaretPosition(0);
            }
        } else if (topicList.getSelectedIndex() < 0 && !listModel.isEmpty()) {
            topicList.setSelectedIndex(0);
        }

        setVisible(true);
        toFront();
        requestFocus();
    }
}
