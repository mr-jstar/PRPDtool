package classifiers;

/**
 *
 * @author jstar
 */
public class RTDETRParser implements OutputParser {

    private final int numQueries;
    private final String[] classNames;
    private final float confTh;

    public RTDETRParser(
            int numQueries,
            String[] classNames,
            float confTh
    ) {
        this.numQueries = numQueries;
        this.classNames = classNames;
        this.confTh = confTh;
    }

    @Override
    public Prediction [] parse(float[] flat) {

        final int STRIDE = 6;

        float bestConf = -Float.MAX_VALUE;

        int bestCls = -1;
        int bestQuery = -1;

        for (int q = 0; q < numQueries; q++) {

            int base = q * STRIDE;

            float conf = flat[base + 4];
            int cls = Math.round(flat[base + 5]);

            if (conf > bestConf) {
                bestConf = conf;
                bestCls = cls;
                bestQuery = q;
            }
        }

        int classId
                = bestConf >= confTh ? bestCls : -1;

        String className
                = (classId >= 0 && classId < classNames.length)
                        ? classNames[classId]
                        : "none";

        return new Prediction [] { new Prediction(
                classId,
                className,
                bestConf,
                bestConf,
                bestConf,
                bestQuery,
                0,
                0,
                new float[]{flat[bestQuery * STRIDE], flat[bestQuery * STRIDE + 1], flat[bestQuery * STRIDE + 2], flat[bestQuery * STRIDE + 3]}
        ) };
    }
}
