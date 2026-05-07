package classifier;

import ai.onnxruntime.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map.Entry;

/**
 *
 * @author jstar
 */
public class ONNXClassifier implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;

    private final int inputWidth = 224;
    private final int inputHeight = 224;

    public ONNXClassifier(String modelPath) throws Exception {
        env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions opts
                = new OrtSession.SessionOptions();

        session = env.createSession(modelPath, opts);
    }

    public float[] classify(BufferedImage image) throws Exception {

        float[] chw = bufferedImageToGrayCHW(image);

        long[] shape = {1, 3, inputHeight, inputWidth};

        OnnxTensor tensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(chw),
                shape
        );

        String inputName
                = session.getInputNames().iterator().next();

        OrtSession.Result result = session.run(
                Collections.singletonMap(inputName, tensor)
        );
        
        for( Entry<String,OnnxValue> e : result ) {
            System.out.println( e.getKey() + " -> " + e.getValue() );
        }

        // zakładamy output: [1,num_classes]
        float[][] output
                = (float[][]) result.get(0).getValue();

        tensor.close();
        result.close();

        return output[0];
    }

    public static float[] bufferedImageToGrayCHW(BufferedImage src) {

        final int W = 224;
        final int H = 224;

        // ------------------------------------------
        // 1. resize + grayscale
        // ------------------------------------------
        BufferedImage gray
                = new BufferedImage(
                        W,
                        H,
                        BufferedImage.TYPE_BYTE_GRAY
                );

        Graphics2D g = gray.createGraphics();

        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );

        g.drawImage(src, 0, 0, W, H, null);

        g.dispose();

        // ------------------------------------------
        // 2. CHW: 3 kanały identyczne
        // ------------------------------------------
        float[] chw = new float[3 * W * H];

        int plane = W * H;

        int idxR = 0;
        int idxG = plane;
        int idxB = 2 * plane;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {

                int rgb = gray.getRGB(x, y);

                int v = rgb & 0xFF;

                float f = (v / 255.0f - 0.5f) / 0.5f;

                // 3 identyczne kanały
                chw[idxR++] = f;
                chw[idxG++] = f;
                chw[idxB++] = f;
            }
        }

        return chw;
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
