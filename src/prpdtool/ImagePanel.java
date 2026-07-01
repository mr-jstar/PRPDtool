package prpdtool;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import pipeline.DynamicPRPDHistogram;

/**
 *
 * @author jstar
 */
public class ImagePanel extends JPanel {

    private BufferedImage image;
    private DynamicPRPDHistogram histogram;
    private int lastMouseX;
    private int lastMouseY;
    private Runnable resetAction;
    private boolean showCursors = false;
    private double cursorPhase1 = 90.0;
    private double cursorPhase2 = 270.0;
    private double cursorAmp1 = 0.0;
    private double cursorAmp2 = 0.0;
    private int draggingCursor = 0; // 0=none, 1=P1, 2=P2, 3=A1, 4=A2
    private Runnable saveAllAction;

    public void setSaveAllAction(Runnable action) {
        this.saveAllAction = action;
    }

    public void setShowCursors(boolean show) {
        this.showCursors = show;
        if (show && histogram != null) {
            int w = getWidth();
            int h = getHeight();
            cursorPhase1 = histogram.getPhaseAt((int)(w * 0.25), w);
            cursorPhase2 = histogram.getPhaseAt((int)(w * 0.75), w);
            cursorAmp1 = histogram.getAmpAt((int)(h * 0.75), h); // Bottom 25%
            cursorAmp2 = histogram.getAmpAt((int)(h * 0.25), h); // Top 25%
        }
        repaint();
    }

    public boolean isShowCursors() {
        return showCursors;
    }

    public void setResetAction(Runnable resetAction) {
        this.resetAction = resetAction;
    }

    public ImagePanel(BufferedImage image) {
        this.image = image;
        setBackground(Color.WHITE);
        setLayout(null);
        
        JButton resetBtn = new JButton("Reset");
        resetBtn.setFocusable(false);
        resetBtn.addActionListener(e -> {
            if (resetAction != null) {
                resetAction.run();
            } else if (histogram != null) {
                histogram.setZoomX(1.0);
                histogram.setZoomY(1.0);
                histogram.setOffsetX(0);
                histogram.setOffsetY(0);
                setImage(histogram.getImage());
            }
        });
        add(resetBtn);
        
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent evt) {
                resetBtn.setBounds(getWidth() - 95, getHeight() - 30, 85, 25);
            }
        });

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                
                if (showCursors && histogram != null) {
                    int w = getWidth();
                    int h = getHeight();
                    int x1 = Math.max(0, Math.min(w, histogram.getXForPhase(cursorPhase1, w)));
                    int x2 = Math.max(0, Math.min(w, histogram.getXForPhase(cursorPhase2, w)));
                    int y1 = Math.max(0, Math.min(h, histogram.getYForAmp(cursorAmp1, h)));
                    int y2 = Math.max(0, Math.min(h, histogram.getYForAmp(cursorAmp2, h)));
                    
                    int distP1 = Math.abs(lastMouseX - x1);
                    int distP2 = Math.abs(lastMouseX - x2);
                    int distA1 = Math.abs(lastMouseY - y1);
                    int distA2 = Math.abs(lastMouseY - y2);
                    
                    if (distP1 < 8 && distP1 <= distP2 && distP1 <= distA1 && distP1 <= distA2) draggingCursor = 1;
                    else if (distP2 < 8 && distP2 <= distA1 && distP2 <= distA2) draggingCursor = 2;
                    else if (distA1 < 8 && distA1 <= distA2) draggingCursor = 3;
                    else if (distA2 < 8) draggingCursor = 4;
                    else draggingCursor = 0;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (showCursors && draggingCursor > 0 && histogram != null) {
                    int w = getWidth();
                    int h = getHeight();
                    if (draggingCursor == 1) cursorPhase1 = histogram.getPhaseAt(Math.max(0, Math.min(w, e.getX())), w);
                    else if (draggingCursor == 2) cursorPhase2 = histogram.getPhaseAt(Math.max(0, Math.min(w, e.getX())), w);
                    else if (draggingCursor == 3) cursorAmp1 = histogram.getAmpAt(Math.max(0, Math.min(h, e.getY())), h);
                    else if (draggingCursor == 4) cursorAmp2 = histogram.getAmpAt(Math.max(0, Math.min(h, e.getY())), h);
                    repaint();
                    return;
                }

                if (histogram != null) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    
                    if (SwingUtilities.isRightMouseButton(e)) {
                        histogram.setOffsetY(histogram.getOffsetY() + dy);
                    } else {
                        histogram.setOffsetX(histogram.getOffsetX() + dx);
                        histogram.setOffsetY(histogram.getOffsetY() + dy);
                    }
                    
                    setImage(histogram.getImage());
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (histogram != null) {
                    double rot = e.getPreciseWheelRotation();
                    if (rot == 0) return;
                    
                    double scale = Math.pow(1.1, -rot);
                    
                    if (e.isAltDown()) {
                        int mouseX = e.getX();
                        int left = 70;
                        
                        double zoomX = histogram.getZoomX();
                        double baseOffsetX = histogram.getOffsetX();
                        double newZoomX = zoomX * scale;
                        
                        double xOldDiff = (mouseX - left - baseOffsetX) / zoomX;
                        double newOffsetX = mouseX - left - (xOldDiff * newZoomX);
                        
                        histogram.setZoomX(newZoomX);
                        histogram.setOffsetX(newOffsetX);
                    } else {
                        int mouseY = e.getY();
                        int top = 30; 
                        int plotH = getHeight() - 30 - 55; 
                        
                        double zoomY = histogram.getZoomY();
                        double baseOffset = histogram.getOffsetY();
                        double newZoom = zoomY * scale;
                        
                        double base = top + plotH;
                        double yOldDiff = (mouseY - base - baseOffset) / zoomY;
                        double newOffset = mouseY - base - (yOldDiff * newZoom);
                        
                        histogram.setZoomY(newZoom);
                        histogram.setOffsetY(newOffset);
                    }
                    
                    setImage(histogram.getImage());
                }
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem saveItem = new JMenuItem("Save PRPD as PNG");
        saveItem.addActionListener(e -> saveAsPng());
        popupMenu.add(saveItem);
        
        JMenuItem saveAllItem = new JMenuItem("Save All graphs as PNG");
        saveAllItem.addActionListener(e -> {
            if (saveAllAction != null) {
                saveAllAction.run();
            } else {
                saveAsPng();
            }
        });
        popupMenu.add(saveAllItem);
        
        setComponentPopupMenu(popupMenu);
    }

    private void saveAsPng() {
        if (image == null) {
            JOptionPane.showMessageDialog(this, "No image to save.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save PRPD as PNG");
        fc.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }
            try {
                ImageIO.write(image, "PNG", file);
                JOptionPane.showMessageDialog(this, "PRPD image saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save image: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void setHistogram(DynamicPRPDHistogram histogram) {
        this.histogram = histogram;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    public BufferedImage getRenderedImage() {
        BufferedImage img = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = img.createGraphics();
        paintComponent(g2d);
        paintBorder(g2d);
        g2d.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image == null) {
            return;
        }

        g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
        
        if (showCursors && histogram != null) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
            int w = getWidth();
            int h = getHeight();
            int x1 = Math.max(0, Math.min(w, histogram.getXForPhase(cursorPhase1, w)));
            int x2 = Math.max(0, Math.min(w, histogram.getXForPhase(cursorPhase2, w)));
            int y1 = Math.max(0, Math.min(h, histogram.getYForAmp(cursorAmp1, h)));
            int y2 = Math.max(0, Math.min(h, histogram.getYForAmp(cursorAmp2, h)));
            
            g2.setStroke(new java.awt.BasicStroke(1.0f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f));
            
            g2.setColor(new Color(255, 165, 0)); // Orange
            g2.drawLine(x1, 0, x1, h);
            g2.setColor(new Color(255, 215, 0)); // Gold
            g2.drawLine(x2, 0, x2, h);
            
            g2.setColor(new Color(255, 100, 100)); // Red-ish
            g2.drawLine(0, y1, w, y1);
            g2.setColor(new Color(255, 150, 150)); // Light red
            g2.drawLine(0, y2, w, y2);
            
            // OSD
            g2.setStroke(new java.awt.BasicStroke(1.0f));
            int boxW = 110;
            int boxH = 105;
            int boxX = w - boxW - 35; // Moved more to left because PRPD has right padding
            int boxY = 45;
            
            boolean isDark = PRPDConstants.isDarkTheme();
            g2.setColor(isDark ? new Color(30, 30, 30, 200) : new Color(255, 255, 255, 200));
            g2.fillRect(boxX, boxY, boxW, boxH);
            g2.setColor(isDark ? Color.LIGHT_GRAY : Color.BLACK);
            g2.drawRect(boxX, boxY, boxW, boxH);
            
            g2.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
            int ty = boxY + 15;
            double dP = Math.abs(cursorPhase2 - cursorPhase1);
            g2.drawString(String.format(java.util.Locale.US, "P1: %.1f°", cursorPhase1), boxX + 10, ty); ty += 15;
            g2.drawString(String.format(java.util.Locale.US, "P2: %.1f°", cursorPhase2), boxX + 10, ty); ty += 15;
            g2.drawString(String.format(java.util.Locale.US, "ΔP: %.1f°", dP), boxX + 10, ty); ty += 20;
            
            double dA = Math.abs(cursorAmp2 - cursorAmp1);
            g2.drawString(String.format(java.util.Locale.US, "A1: %.3f", cursorAmp1), boxX + 10, ty); ty += 15;
            g2.drawString(String.format(java.util.Locale.US, "A2: %.3f", cursorAmp2), boxX + 10, ty); ty += 15;
            g2.drawString(String.format(java.util.Locale.US, "ΔA: %.3f", dA), boxX + 10, ty);
        }
    }
}
