package prpdtool;

/**
 *
 * @author jstar
 */
import javax.swing.*;
import java.awt.*;

public class PRPDTool extends JFrame {

    public PRPDTool() {
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
        fileM.add(fileMI);
        JMenuItem exitMI = new JMenuItem("Exit");
        exitMI.addActionListener(e -> System.exit(0));
        fileM.add(exitMI);
        mb.add(fileM);
        setJMenuBar(mb);
    }

    private void initGui() {

        JPanel left = new JPanel();
        JPanel center = new JPanel();
        JPanel right = new JPanel();
        JPanel bottom = new JPanel();

        // kolory poglądowe
        left.setBackground(Color.LIGHT_GRAY);
        center.setBackground(Color.WHITE);
        right.setBackground(Color.GRAY);
        bottom.setBackground(Color.DARK_GRAY);

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PRPDTool().setVisible(true);
        });
    }
}
