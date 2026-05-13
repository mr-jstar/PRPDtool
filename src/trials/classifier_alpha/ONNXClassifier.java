package trials.classifier_alpha;

import ai.onnxruntime.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.util.Collections;
import javax.imageio.ImageIO;

/**
 *
 * @author jstar
 */
public class ONNXClassifier implements Classifier, AutoCloseable {

    private OrtEnvironment env;
    private OrtSession session;

    private final int num_classes;// = 4;
    private final int num_anchors;// = 9;
    private final int grid_size; // = 7;

    private final int inputWidth = 224;
    private final int inputHeight = 224;

    private String model;
    private boolean ok;
    private String [] classes; 

    public ONNXClassifier(String modelPath, int num_classes, int num_anchors, int grid_size, String [] classes ) {
        model = Paths.get(modelPath).getFileName().toString();
        this.num_classes = num_classes;
        this.num_anchors = num_anchors;
        this.grid_size = grid_size;
        this.classes = classes;
        try {
            env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);

            OrtSession.SessionOptions opts
                    = new OrtSession.SessionOptions();
            opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);

            session = env.createSession(modelPath, opts);
            ok = true;
        } catch (Exception e) {
            System.err.println("ONNXClassifier using model " + modelPath + " can not be constructed: " + e.getMessage());
            ok = false;
        }
    }

    @Override
    public String name() {
        return model;
    }

    @Override
    public boolean ok() {
        return ok;
    }

    @Override
    public Prediction classify(BufferedImage image) throws Exception {

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

        /*
        for (Entry<String, OnnxValue> e : result) {
            System.out.println(e.getKey() + " -> " + e.getValue());
        }
         */
        Object value = result.get(0).getValue();

        //System.out.println(value);
        float[] flat;

        if (value instanceof float[][][][]) {
            flat = flatten((float[][][][]) value);
        } else if (value instanceof float[]) {
            flat = (float[]) value;
        } else if (value instanceof float[][]) {
            flat = ((float[][]) value)[0];
        } else {
            throw new RuntimeException(
                    "Unsupported ONNX output type: " + value.getClass()
            );
        }

        tensor.close();
        result.close();

        return parseYoloLikeOutput(flat, 0.30f, 0.55f);
    }

    public Prediction parseYoloLikeOutput(
            float[] flat,
            float confObjTh,
            float confClsTh
    ) {
        int stride = 5 + num_classes; // 9

        int bestGy = 0;
        int bestGx = 0;
        int bestA = 0;
        float bestObj = -Float.MAX_VALUE;

        for (int gy = 0; gy < grid_size; gy++) {
            for (int gx = 0; gx < grid_size; gx++) {
                for (int a = 0; a < num_anchors; a++) {

                    int base
                            = (((gy * grid_size + gx) * num_anchors + a) * stride);

                    float obj = flat[base + 4];

                    if (obj > bestObj) {
                        bestObj = obj;
                        bestGy = gy;
                        bestGx = gx;
                        bestA = a;
                    }
                }
            }
        }

        int bestBase
                = (((bestGy * grid_size + bestGx) * num_anchors + bestA) * stride);

        float[] clsScores = new float[num_classes];

        int clsId = 0;
        float clsProb = -Float.MAX_VALUE;

        for (int c = 0; c < num_classes; c++) {
            float v = flat[bestBase + 5 + c];
            clsScores[c] = v;

            if (v > clsProb) {
                clsProb = v;
                clsId = c;
            }
        }

        if (bestObj < confObjTh || clsProb < confClsTh) {
            return new Prediction(
                    null,
                    bestObj,
                    clsProb,
                    clsScores,
                    bestGx,
                    bestGy,
                    bestA,
                    classes
            );
        }

        return new Prediction(
                clsId,
                bestObj,
                clsProb,
                clsScores,
                bestGx,
                bestGy,
                bestA,
                classes
        );
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

        try {
            ImageIO.write(gray, "png", new File("gray.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    private float[] flatten(float[][][][] out) {
        int stride = 5 + num_classes; // 9

        float[] flat = new float[grid_size * grid_size * num_anchors * stride];

        int k = 0;

        for (int gy = 0; gy < grid_size; gy++) {
            for (int gx = 0; gx < grid_size; gx++) {
                for (int a = 0; a < num_anchors; a++) {
                    int base = a * stride;

                    for (int j = 0; j < stride; j++) {
                        flat[k++] = out[0][gy][gx][base + j];
                    }
                }
            }
        }

        return flat;
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
