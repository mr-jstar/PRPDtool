package classifiers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 *
 * @author jstar
 */
public class PreprocessorRTDETR implements Preprocessor {

    public float[] preprocess(BufferedImage src, int w, int h) {

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        float[] chw = new float[3 * w * h];

        int plane = w * h;
        int rIndex = 0;
        int gIndex = plane;
        int bIndex = 2 * plane;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                chw[rIndex++] = r / 255.0f;
                chw[gIndex++] = gr / 255.0f;
                chw[bIndex++] = b / 255.0f;
            }
        }

        return chw;
    }
}
