package classifier;

import java.util.Locale;

/**
 *
 * @author jstar
 */
public class Prediction {

    public final Integer clsId;      // null jeśli brak detekcji
    public final float objScore;
    public final float clsProb;
    public final float[] clsScores;
    public final int gx, gy, anchor;

    private final String[] classes;

    public Prediction(Integer clsId, float objScore, float clsProb,
            float[] clsScores, int gx, int gy, int anchor,
            String [] classes ) {
        this.clsId = clsId;
        this.objScore = objScore;
        this.clsProb = clsProb;
        this.clsScores = clsScores;
        this.gx = gx;
        this.gy = gy;
        this.anchor = anchor;
        this.classes = classes;
    }

    @Override
    public String toString() {
        if (clsId == null) {
            return "no defect";
        } else {
            return classes[clsId] + String.format(Locale.US, " (%3.0f%%)", objScore*100);
        }
    }
}
