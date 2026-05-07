package classifier;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 *
 * @author jstar
 */
public class ONNXTest {

    public static void main(String[] args) {
        

        try (ONNXClassifier clf
                = new ONNXClassifier("/Users/jstar/NetBeansProjects/PRPDtool/klasyfikator/classprpd.onnx")) {

            BufferedImage img = ImageIO.read(new File("/Users/jstar/NetBeansProjects/PRPDtool/klasyfikator/test_image.png"));;
            
            float[] scores = clf.classify(img);

            int best = 0;

            for (int i = 1; i < scores.length; i++) {
                if (scores[i] > scores[best]) {
                    best = i;
                }
            }

            System.out.println("Predicted class = " + best);

            for (int i = 0; i < scores.length; i++) {
                System.out.println(i + " -> " + scores[i]);
            }
        } catch( Exception ex ) {
            ex.printStackTrace();
        }
    }
}
