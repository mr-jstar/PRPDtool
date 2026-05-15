package classifiers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 *
 * @author jstar
 */
public class PreprocessorYOLO implements Preprocessor {

    public float[] preprocess(BufferedImage src, int w, int h) {
        // src to Gray3CHW
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        float[] chw = new float[3 * w * h];

        int plane = w * h;
        int r = 0;
        int gch = plane;
        int b = 2 * plane;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = gray.getRGB(x, y);
                int v = rgb & 0xFF;

                // ToTensor + Normalize([0.5]*3,[0.5]*3)
                float f = (v / 255.0f - 0.5f) / 0.5f;

                chw[r++] = f;
                chw[gch++] = f;
                chw[b++] = f;
            }
        }

        return chw;
    }
}
