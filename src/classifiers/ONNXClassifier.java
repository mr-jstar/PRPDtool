package classifiers;

/**
 *
 * @author jstar
 */
import ai.onnxruntime.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.util.Collections;

public class ONNXClassifier implements AutoCloseable, Classifier {

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private OutputParser parser;
    private String name;

    private static final int W = 224;
    private static final int H = 224;

    private boolean ok;

    public ONNXClassifier(String modelPath, OutputParser parser) {
        this.name = Paths.get(modelPath).getFileName().toString().replaceAll("\\.[^.]+$","");
        try {
            this.env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            this.session = env.createSession(modelPath, opts);

            this.inputName = session.getInputNames().iterator().next();
            this.parser = parser;
            ok = true;
        } catch (Exception ex) {
            ok = false;
        }
    }
    @Override
    public boolean ok() {
        return ok;
    }
    
    @Override 
    public String name() {
        return name;
    }
    
    @Override
    public Prediction classify(BufferedImage image) throws Exception {
        if( ! ok )
            throw new IllegalStateException( getClass().getName() + " has not been properly initialized" );
        
        float[] chw = preprocessGray3CHW(image);

        try (OnnxTensor tensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(chw),
                new long[]{1, 3, H, W}
        ); OrtSession.Result result = session.run(
                Collections.singletonMap(inputName, tensor)
        )) {

            Object value = result.get(0).getValue();
            float[] flat = flatten(value);

            return parser.parse(flat);
        }
    }

    private static float[] preprocessGray3CHW(BufferedImage src) {
        BufferedImage gray = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, W, H, null);
        g.dispose();

        float[] chw = new float[3 * W * H];

        int plane = W * H;
        int r = 0;
        int gch = plane;
        int b = 2 * plane;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
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

    private static float[] flatten(Object value) {
        if (value instanceof float[]) {
            return (float[]) value;
        }

        if (value instanceof float[][]) {
            return ((float[][]) value)[0];
        }

        if (value instanceof float[][][][] out) {
            int n0 = out.length;
            int n1 = out[0].length;
            int n2 = out[0][0].length;
            int n3 = out[0][0][0].length;

            float[] flat = new float[n0 * n1 * n2 * n3];
            int k = 0;

            for (int i0 = 0; i0 < n0; i0++) {
                for (int i1 = 0; i1 < n1; i1++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        for (int i3 = 0; i3 < n3; i3++) {
                            flat[k++] = out[i0][i1][i2][i3];
                        }
                    }
                }
            }

            return flat;
        }

        throw new RuntimeException("Unsupported ONNX output type: " + value.getClass());
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
