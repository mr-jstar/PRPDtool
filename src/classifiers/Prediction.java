package classifiers;

import java.util.Locale;

/**
 *
 * @author jstar
 */
public class Prediction {

    public int classId;            // -1 jeśli brak detekcji
    public String className;
    public float confidence;
    public float objectness;
    public float classProbability;
    public int gx;
    public int gy;
    public int anchor;
    public float[] box;

    public Prediction(
            int classId,
            String className,
            float confidence,
            float objectness,
            float classProbability,
            int gx,
            int gy,
            int anchor
    ) {
        this.classId = classId;
        this.className = className;
        this.confidence = confidence;
        this.objectness = objectness;
        this.classProbability = classProbability;
        this.gx = gx;
        this.gy = gy;
        this.anchor = anchor;
    }

    public Prediction(
            int classId,
            String className,
            float confidence,
            float objectness,
            float classProbability,
            int gx,
            int gy,
            int anchor,
            float[] box
    ) {
        this.classId = classId;
        this.className = className;
        this.confidence = confidence;
        this.objectness = objectness;
        this.classProbability = classProbability;
        this.gx = gx;
        this.gy = gy;
        this.anchor = anchor;
        this.box = box;
    }

    public String className() {
        return className;
    }

    @Override
    public String toString() {
        if (classId == -1) {
            return "no defect";
        } else {
            return className + String.format(Locale.US, " (%3.0f%%)", confidence * 100);
        }
    }
}
