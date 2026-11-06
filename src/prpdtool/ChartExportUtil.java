package prpdtool;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * High-quality PNG export utility for scientific and diagnostic charts.
 * Renders Swing components with anti-aliasing and optional 2x supersampling for publications.
 */
public class ChartExportUtil {

    public static void exportComponentToPng(JComponent component, Window parent, String defaultFileName) {
        if (component == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Eksportuj wykres do pliku PNG (High-Resolution)");
        chooser.setSelectedFile(new File(defaultFileName.endsWith(".png") ? defaultFileName : (defaultFileName + ".png")));
        chooser.setFileFilter(new FileNameExtensionFilter("Obrazy PNG (*.png)", "png"));

        int userSelection = chooser.showSaveDialog(parent);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File fileToSave = chooser.getSelectedFile();
        if (!fileToSave.getName().toLowerCase().endsWith(".png")) {
            fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".png");
        }

        try {
            int w = Math.max(component.getWidth(), 800);
            int h = Math.max(component.getHeight(), 500);

            // Render at 2x resolution for crystal-clear publication and report quality
            int scale = 2;
            BufferedImage image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            g2.scale(scale, scale);

            // Fill background with component background or dark slate
            Color bg = component.getBackground();
            if (bg == null || bg.getAlpha() == 0) {
                bg = new Color(20, 24, 30);
            }
            g2.setColor(bg);
            g2.fillRect(0, 0, w, h);

            component.paint(g2);
            g2.dispose();

            ImageIO.write(image, "png", fileToSave);

            JOptionPane.showMessageDialog(
                    parent,
                    "<html><b>Wykres został pomyślnie wyeksportowany do pliku PNG!</b><br><br>"
                            + "Lokalizacja: <span style='color:#00d2ff;'>" + fileToSave.getAbsolutePath() + "</span><br>"
                            + "Rozdzielczość: <b>" + (w * scale) + " x " + (h * scale) + " px</b> (Retina / 300 DPI)</html>",
                    "Eksport Zakończony Sukcesem",
                    JOptionPane.INFORMATION_MESSAGE
            );

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    parent,
                    "Błąd podczas zapisywania obrazu PNG:\n" + ex.getMessage(),
                    "Błąd Eksportu",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
