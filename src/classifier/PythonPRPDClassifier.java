package classifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jstar
 */
public class PythonPRPDClassifier implements Classifier {

    private final String pythonExe;
    private final String scriptPath;

    private final boolean ok;
    
    private String name;

    public PythonPRPDClassifier(String pythonExe, String scriptPath) {
        this.pythonExe = pythonExe;
        this.scriptPath = scriptPath;
        ok = Files.isExecutable(Paths.get(pythonExe)) && Files.exists(Paths.get(scriptPath));
        if( ok )
            name = Paths.get(scriptPath).getFileName().toString();
        else
            name = "UNKNOWN";
    }
    
    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean ok() {
        return ok;
    }

    /**
     * image - obraz PRPD pythonExe - np. "python3" scriptPath -
     * classify_prpd.py
     *
     * Python powinien wypisać:
     *
     * cls_id obj_score cls0 cls1 cls2 ...
     *
     * np: 2 0.98 0.01 0.02 0.95 0.02
     */
    @Override
    public Prediction classify(BufferedImage image) throws Exception {

        // ----------------------------------------------------
        // 1. zapis BufferedImage -> PNG
        // ----------------------------------------------------
        File tempFile = File.createTempFile("prpd_", ".png");

        try {
            ImageIO.write(image, "png", tempFile);

            // ------------------------------------------------
            // 2. uruchomienie pythona
            // ------------------------------------------------
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    scriptPath,
                    tempFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true);

            Process process = pb.start();

            // ------------------------------------------------
            // 3. odczyt wyniku
            // ------------------------------------------------
            String output;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(),
                            StandardCharsets.UTF_8))) {

                StringBuilder sb = new StringBuilder();

                String line;

                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }

                output = sb.toString().trim();
            }

            int exit = process.waitFor();

            if (exit != 0) {
                throw new RuntimeException(
                        "Python process failed.\nOutput:\n" + output
                );
            }

            // --------------------------------------------
            // parsowanie:
            //
            // cls_id obj_score cls0 cls1 ...
            // --------------------------------------------
            String[] tok = output.split("\\s+");

            if (tok.length < 2) {
                throw new RuntimeException(
                        "Invalid classifier output: " + output
                );
            }

            int clsId = Integer.parseInt(tok[0]);
            float objScore = Float.parseFloat(tok[1]);

            List<Float> scores = new ArrayList<>();

            for (int i = 2; i < tok.length; i++) {
                scores.add(Float.parseFloat(tok[i]));
            }

            float[] clsScores = new float[scores.size()];

            for (int i = 0; i < scores.size(); i++) {
                clsScores[i] = scores.get(i);
            }

            return new Prediction(
                    clsId < 0 ? null : clsId,
                    objScore,
                    objScore,
                    clsScores,
                    0, 0, 0
            );

        } finally {
            tempFile.delete();
        }
    }
}
