package pl.poznan.put;

import java.util.Collection;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.math3.util.FastMath;

public class InteractionNetworkFidelity {
  private InteractionNetworkFidelity() {
    super();
  }

  /**
   * Calculates the Interaction Network Fidelity (INF) between a reference set and a model set. INF
   * is the geometric mean of Matthews Correlation Coefficient (MCC) components: Positive Predictive
   * Value (PPV) and Sensitivity (STY).
   *
   * @param referenceSet The collection representing the 'correct' or reference interactions.
   * @param modelSet The collection representing the interactions found in the model being
   *     evaluated.
   * @param <T> The type of elements in the collections (e.g., AnalyzedBasePair,
   *     ConsensusInteraction).
   * @return The Interaction Network Fidelity score (between 0.0 and 1.0).
   */
  public static <T> double calculate(Collection<T> referenceSet, Collection<T> modelSet) {
    double tp = CollectionUtils.intersection(referenceSet, modelSet).size();
    double fp = CollectionUtils.subtract(modelSet, referenceSet).size();
    double fn = CollectionUtils.subtract(referenceSet, modelSet).size();

    double ppvDenominator = tp + fp;
    double styDenominator = tp + fn;

    double ppv = (ppvDenominator == 0) ? 0.0 : tp / ppvDenominator;
    double sty = (styDenominator == 0) ? 0.0 : tp / styDenominator;

    // If either ppv or sty is 0, the geometric mean is 0
    return (ppv == 0 || sty == 0) ? 0.0 : FastMath.sqrt(ppv * sty);
  }

  /**
   * Calculates the Interaction Network Fidelity (INF) using a fuzzy reference set (where each item
   * has a probability) against a crisp model set.
   *
   * @param fuzzyReferenceSet A map where keys are the reference interactions and values are their
   *     probabilities (between 0.0 and 1.0).
   * @param modelSet The collection representing the interactions found in the model being
   *     evaluated.
   * @param <T> The type of elements in the collections/map keys (e.g., AnalyzedBasePair,
   *     ConsensusInteraction).
   * @return The fuzzy Interaction Network Fidelity score (between 0.0 and 1.0).
   */
  public static <T> double calculateFuzzy(
      Map<T, Double> fuzzyReferenceSet, Collection<T> modelSet) {
    var tp = 0.0;
    var fp = 0.0;
    var fn = 0.0;

    for (var entry : fuzzyReferenceSet.entrySet()) {
      var probability = entry.getValue();
      if (modelSet.contains(entry.getKey())) {
        tp += probability;
        fp += 1.0 - probability;
      } else {
        fn += probability;
        fn += entry.getValue();
      }
    }

    double ppvDenominator = tp + fp;
    double styDenominator = tp + fn;

    double ppv = (ppvDenominator == 0) ? 0.0 : tp / ppvDenominator;
    double sty = (styDenominator == 0) ? 0.0 : tp / styDenominator;

    // If either ppv or sty is 0, the geometric mean is 0
    return (ppv == 0 || sty == 0) ? 0.0 : FastMath.sqrt(ppv * sty);
  }
}
