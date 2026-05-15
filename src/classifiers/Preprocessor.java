package classifiers;

import java.awt.image.BufferedImage;

/**
 *
 * @author jstar
 */
public interface Preprocessor {

    float[] preprocess(BufferedImage src, int w, int h);
}
