package classifiers;

import java.util.Locale;

/**
 *
 * @author jstar
 */
public class Prediction {
        int classId;            // -1 jeśli brak detekcji
        String className;
        float confidence;
        float objectness;
        float classProbability;
        int gx;
        int gy;
        int anchor;
         
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
    
        @Override
    public String toString() {
        if (classId == -1) {
            return "no defect";
        } else {
            return className + String.format(Locale.US, " (%3.0f%%)", confidence*100);
        }
    }
}
