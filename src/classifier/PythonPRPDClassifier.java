package classifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jstar
 */
public class PythonPRPDClassifier {

    public static class Result {
        public final int clsId;
        public final double objScore;
        public final double[] clsScores;
        public final String rawOutput;
        
        private final static String [] classes = { "DEF1", "DEF2", "DEF3", "DEF4" };

        public Result(int clsId,
                      double objScore,
                      double[] clsScores,
                      String rawOutput) {
            this.clsId = clsId;
            this.objScore = objScore;
            this.clsScores = clsScores;
            this.rawOutput = rawOutput;
        }
        
        @Override
        public String toString() {
            if( clsId == -1 )
                return "no defect";
            else 
                return classes[clsId] + objScore;
        }
    }

    /**
     * image        - obraz PRPD
     * pythonExe    - np. "python3"
     * scriptPath   - classify_prpd.py
     *
     * Python powinien wypisać:
     *
     * cls_id obj_score cls0 cls1 cls2 ...
     *
     * np:
     * 2 0.98 0.01 0.02 0.95 0.02
     */
    public static Result classify(
            BufferedImage image,
            String pythonExe,
            String scriptPath
    ) throws Exception {

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
            double objScore = Double.parseDouble(tok[1]);

            List<Double> scores = new ArrayList<>();

            for (int i = 2; i < tok.length; i++) {
                scores.add(Double.parseDouble(tok[i]));
            }

            double[] clsScores = new double[scores.size()];

            for (int i = 0; i < scores.size(); i++) {
                clsScores[i] = scores.get(i);
            }

            return new Result(
                    clsId,
                    objScore,
                    clsScores,
                    output
            );

        } finally {
            tempFile.delete();
        }
    }
}
