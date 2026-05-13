package classifiers;

/**
 *
 * @author jstar
 */
public class MobileYOLOParser implements OutputParser {

    private final int grid;
    private final int anchors;
    private final int classes;
    private final int stride;

    private final float confObjTh;
    private final float confClsTh;

    private final String[] classNames;

    public MobileYOLOParser(
            int grid,
            int anchors,
            String[] classNames,
            float confObjTh,
            float confClsTh
    ) {
        this.grid = grid;
        this.anchors = anchors;
        this.classNames = classNames;

        this.classes = classNames.length;
        this.stride = 5 + classes;

        this.confObjTh = confObjTh;
        this.confClsTh = confClsTh;
    }

    @Override
    public Prediction parse(float[] flat) {

        int bestGy = 0;
        int bestGx = 0;
        int bestAnchor = 0;

        float bestObjectness = -Float.MAX_VALUE;

        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                for (int a = 0; a < anchors; a++) {

                    int base =
                            (((gy * grid + gx) * anchors + a) * stride);

                    float obj = flat[base + 4];

                    if (obj > bestObjectness) {
                        bestObjectness = obj;
                        bestGy = gy;
                        bestGx = gx;
                        bestAnchor = a;
                    }
                }
            }
        }

        int base =
                (((bestGy * grid + bestGx) * anchors + bestAnchor) * stride);

        int bestCls = 0;
        float bestClassProb = -Float.MAX_VALUE;

        for (int c = 0; c < classes; c++) {
            float clsProb = flat[base + 5 + c];

            if (clsProb > bestClassProb) {
                bestClassProb = clsProb;
                bestCls = c;
            }
        }

        float confidence = bestObjectness * bestClassProb;

        boolean accepted =
                bestObjectness >= confObjTh &&
                bestClassProb >= confClsTh;

        int classId = accepted ? bestCls : -1;

        String className =
                accepted ? classNames[bestCls] : "none";

        return new Prediction(
                classId,
                className,
                confidence,
                bestObjectness,
                bestClassProb,
                bestGx,
                bestGy,
                bestAnchor
        );
    }
}
