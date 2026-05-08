package classifier;

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

    private final static String[] classes = {"DEF1", "DEF2", "DEF3", "DEF4"};

    public Prediction(Integer clsId, float objScore, float clsProb,
            float[] clsScores, int gx, int gy, int anchor) {
        this.clsId = clsId;
        this.objScore = objScore;
        this.clsProb = clsProb;
        this.clsScores = clsScores;
        this.gx = gx;
        this.gy = gy;
        this.anchor = anchor;
    }

    @Override
    public String toString() {
        if (clsId == null) {
            return "no defect";
        } else {
            return classes[clsId] + objScore;
        }
    }
}
