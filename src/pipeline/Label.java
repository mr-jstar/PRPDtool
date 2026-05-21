package pipeline;

import java.awt.Color;

/**
 *
 * @author jstar
 */
public class Label {

    private final double xc, yc, w, h;
    private final String label;
    private final Color color;

    public Label(double xc, double yc, double w, double h, String label, Color color) {
        this.xc = xc;
        this.yc = yc;
        this.w = w;
        this.h = h;
        this.label = label;
        this.color = color;
    }

    public int leftx(int plotW) {
        return (int) ((xc - w / 2) * plotW);
    }

    public int rightx(int plotW) {
        return (int) ((xc - w / 2) * plotW);
    }

    public int boty(int plotH) {
        return plotH - (int) ((yc - h / 2) * plotH);
    }

    public int topy(int plotH) {
        return plotH - (int) ((yc - h / 2) * plotH);
    }

    public int getW(int plotW) {
        return (int) Math.round(w * plotW);
    }

    public int getH(int plotH) {
        return (int) Math.round(h * plotH);
    }

    public String getLabel() {
        return label;
    }

    public Color getColor() {
        return color;
    }
}
