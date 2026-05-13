package classifiers;

/**
 *
 * @author jstar
 */
public class SqueezeYOLOParser implements OutputParser {

    private final int grid;
    private final int anchors;
    private final int classes;
    private final int stride;

    private final float confTh;

    private final String[] classNames;

    public SqueezeYOLOParser(
            int grid,
            int anchors,
            String[] classNames,
            float confTh
    ) {
        this.grid = grid;
        this.anchors = anchors;
        this.classNames = classNames;

        this.classes = classNames.length;
        this.stride = 5 + classes;

        this.confTh = confTh;
    }

    @Override
    public Prediction parse(float[] flat) {

        int bestGy = 0;
        int bestGx = 0;
        int bestAnchor = 0;
        int bestCls = 0;

        float bestConfidence = -Float.MAX_VALUE;
        float bestObjectness = 0.0f;
        float bestClassProb = 0.0f;

        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                for (int a = 0; a < anchors; a++) {

                    int base =
                            (((gy * grid + gx) * anchors + a) * stride);

                    float objectness = flat[base + 4];

                    for (int c = 0; c < classes; c++) {

                        float clsProb = flat[base + 5 + c];

                        float confidence =
                                objectness * clsProb;

                        if (confidence > bestConfidence) {
                            bestConfidence = confidence;
                            bestObjectness = objectness;
                            bestClassProb = clsProb;

                            bestGy = gy;
                            bestGx = gx;
                            bestAnchor = a;
                            bestCls = c;
                        }
                    }
                }
            }
        }

        int classId =
                bestConfidence >= confTh ? bestCls : -1;

        String className =
                classId >= 0 ? classNames[classId] : "none";

        return new Prediction(
                classId,
                className,
                bestConfidence,
                bestObjectness,
                bestClassProb,
                bestGx,
                bestGy,
                bestAnchor
        );
    }
}
