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
            
            Prediction p = clf.classify(img);

            int best = 0;

            System.out.println("Predicted class = " + p.clsId);
        } catch( Exception ex ) {
            ex.printStackTrace();
        }
    }
}
