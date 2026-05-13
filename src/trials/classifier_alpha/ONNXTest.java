package trials.classifier_alpha;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 *
 * @author jstar
 */
public class ONNXTest {

    public static void main(String[] args) {
        String home = "/home/jstar/";
        //String home = "/Users/jstar/";
        String host = "oer";
        //String host = "oer";

        String model = home + "NetBeansProjects/PRPDtool/klasyfikator/" + host + "_classprpd.onnx";

        System.out.println("Model: " + model);

        String[] classes = {
            "floating",
            "ncorona",
            "noise",
            "pcorona",
            "surface",
            "void"
        };

        try (ONNXClassifier clf
                = new ONNXClassifier(model, 4, 9, 7, classes)) {

            BufferedImage img = ImageIO.read(new File(home + "NetBeansProjects/PRPDtool/klasyfikator/test_image.png"));;

            Prediction p = clf.classify(img);

            int best = 0;

            System.out.println("Predicted class = " + p.clsId);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
