package prpdtool;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Diagnostic Cheat Sheet Dialog for High-Voltage Partial Discharge Engineers.
 * Displays normative physical criteria, golden CIGRE rules, and AI chart interpretation guide.
 */
public class DiagnosticCheatSheetDialog extends JDialog {

    private final boolean isDarkMode;

    public DiagnosticCheatSheetDialog(Window parent, boolean isDarkMode) {
        super(parent, "Ściągawka diagnostyczna inżyniera WNZ (IEC 60270 / CIGRE 226)", ModalityType.MODELESS);
        this.isDarkMode = isDarkMode;

        setSize(1080, 760);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(parent);

        Color bg = isDarkMode ? new Color(18, 22, 28) : new Color(245, 247, 250);
        Color borderC = isDarkMode ? new Color(45, 52, 64) : new Color(215, 220, 228);

        getContentPane().setBackground(bg);
        getContentPane().setLayout(new BorderLayout(0, 0));

        // --- TOP HEADER ---
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderC),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLbl = new JLabel("ŚCIĄGAWKA DIAGNOSTYCZNA WNZ (IEC 60270 / CIGRE WG 21.03)");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(isDarkMode ? new Color(0, 210, 255) : new Color(0, 102, 204));

        JLabel subLbl = new JLabel("Złote reguły rozpoznawania defektów, normatywne wskaźniki asymetrii i instrukcja interpretacji modeli AI");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(isDarkMode ? new Color(170, 180, 195) : new Color(100, 110, 125));

        titleBox.add(titleLbl);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subLbl);
        header.add(titleBox, BorderLayout.WEST);

        JButton closeHeaderBtn = new JButton("Zamknij");
        closeHeaderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        closeHeaderBtn.addActionListener(e -> setVisible(false));
        header.add(closeHeaderBtn, BorderLayout.EAST);

        getContentPane().add(header, BorderLayout.NORTH);

        // --- CONTENT PANE (HTML Viewer) ---
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setText(DiagnosticCheatSheetDoc.getHtmlContent(isDarkMode));
        editorPane.setCaretPosition(0);
        editorPane.setBackground(isDarkMode ? new Color(22, 26, 34) : Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(new LineBorder(borderC, 1));
        scrollPane.getViewport().setBackground(isDarkMode ? new Color(22, 26, 34) : Color.WHITE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(centerPanel, BorderLayout.CENTER);

        // --- BOTTOM BAR ---
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(isDarkMode ? new Color(24, 29, 36) : new Color(235, 240, 246));
        bottomBar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderC),
                new EmptyBorder(10, 16, 10, 16)
        ));

        JLabel infoLbl = new JLabel("Zgodność z wytycznymi IEC 60270, IEC 62478 oraz raportem technicznym CIGRE 226 TF 15.33.02.");
        infoLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        infoLbl.setForeground(isDarkMode ? new Color(160, 175, 190) : new Color(110, 125, 140));
        bottomBar.add(infoLbl, BorderLayout.WEST);

        JButton okBtn = new JButton("Rozumiem / Zamknij");
        okBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        okBtn.setBackground(new Color(2, 132, 199));
        okBtn.setForeground(Color.WHITE);
        okBtn.setFocusPainted(false);
        okBtn.addActionListener(e -> setVisible(false));
        bottomBar.add(okBtn, BorderLayout.EAST);

        getContentPane().add(bottomBar, BorderLayout.SOUTH);
    }
}
