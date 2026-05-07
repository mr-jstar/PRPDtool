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
}
