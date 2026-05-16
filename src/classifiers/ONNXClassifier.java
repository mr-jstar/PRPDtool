package classifiers;

/**
 *
 * @author jstar
 */
import ai.onnxruntime.*;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ONNXClassifier implements AutoCloseable, Classifier {

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private Preprocessor preprocessor;
    private OutputParser parser;
    private String name;

    private static final int W = 224;
    private static final int H = 224;

    private boolean ok;

    public ONNXClassifier(String modelPath, Preprocessor preprocessor, OutputParser parser) {
        this.name = Paths.get(modelPath).getFileName().toString().replaceAll("\\.[^.]+$", "");
        try {
            this.env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            if (modelPath.toLowerCase().endsWith(".zip")) {
                Path tempDir = Files.createTempDirectory("onnx_model_");
                unzip(modelPath, tempDir);

                Path onnx = Files.walk(tempDir)
                        .filter(p -> p.toString().toLowerCase().endsWith(".onnx"))
                        .findFirst()
                        .orElseThrow(() -> new FileNotFoundException("No ONNX file in zip :" + tempDir));

                this.session = env.createSession(onnx.toString(), opts);
            } else {
                this.session = env.createSession(modelPath, opts);
            }

            this.inputName = session.getInputNames().iterator().next();
            this.preprocessor = preprocessor;
            this.parser = parser;
            ok = true;
        } catch (Exception ex) {
            ex.printStackTrace();
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
        if (!ok) {
            throw new IllegalStateException(getClass().getName() + " has not been properly initialized");
        }

        float[] chw = preprocessor.preprocess(image, W, H);

        try (OnnxTensor tensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(chw),
                new long[]{1, 3, H, W}
        ); OrtSession.Result result = session.run(
                Collections.singletonMap(inputName, tensor)
        )) {
            // For testing:  System.out.println(name() + ": result shape: " + result.get(0));
            Object value = result.get(0).getValue();

            if (name.contains("TEST")) {  // For testing - F[][][] is good for RTDETR output
                if (value instanceof float[][][] out) {

                    for (int q = 0; q < 300; q++) {
                        System.out.print(q + ": ");

                        for (int j = 0; j < 6; j++) {
                            System.out.print(out[0][q][j] + " ");
                        }

                        System.out.println();
                    }
                }
            }

            float[] flat = flatten(value);

            return parser.parse(flat);
        }
    }

    private static float[] flatten(Object value) {
        if (value instanceof float[]) {
            return (float[]) value;
        }

        if (value instanceof float[][]) {
            return ((float[][]) value)[0];
        }

        if (value instanceof float[][][] out) {
            int n0 = out.length;
            int n1 = out[0].length;
            int n2 = out[0][0].length;

            float[] flat = new float[n0 * n1 * n2];

            int k = 0;

            for (int i0 = 0; i0 < n0; i0++) {
                for (int i1 = 0; i1 < n1; i1++) {
                    for (int i2 = 0; i2 < n2; i2++) {
                        flat[k++] = out[i0][i1][i2];
                    }
                }
            }

            return flat;
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

    private static void unzip(String zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(Paths.get(zipPath))))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();

                if (!out.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
