package prpdtool;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
            }

            @Override
            public void mouseDragged(MouseEvent e) {
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
    }

    public void setHistogram(DynamicPRPDHistogram histogram) {
        this.histogram = histogram;
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
