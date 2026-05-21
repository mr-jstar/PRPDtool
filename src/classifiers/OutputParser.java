
package classifiers;

/**
 *
 * @author jstar
 */
@FunctionalInterface
public interface OutputParser {
    Prediction [] parse(float[] flatOutput);
}
