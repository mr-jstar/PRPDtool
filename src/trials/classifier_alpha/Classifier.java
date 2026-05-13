package trials.classifier_alpha;

import java.awt.image.BufferedImage;

/**
 *
 * @author jstar
 */
public interface Classifier {

    Prediction classify(BufferedImage image) throws Exception;
    boolean ok();
    String name();
}
