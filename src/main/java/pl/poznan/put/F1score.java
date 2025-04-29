package pl.poznan.put;

import java.util.Collection;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;

public class F1score {
  private F1score() {
    super();
  }

  /**
   * Calculates the F1-score between a reference set and a model set. F1-score is the harmonic mean
   * of precision and recall.
   *
   * @param referenceSet The collection representing the 'correct' or reference interactions.
   * @param modelSet The collection representing the interactions found in the model being
   *     evaluated.
   * @param <T> The type of elements in the collections (e.g., AnalyzedBasePair,
   *     ConsensusInteraction).
   * @return The F1-score (between 0.0 and 1.0). Returns NaN if the denominator (2*tp + fp + fn) is
   *     zero.
   */
  public static <T> double calculate(Collection<T> referenceSet, Collection<T> modelSet) {
    double tp = CollectionUtils.intersection(referenceSet, modelSet).size();
    double fp = CollectionUtils.subtract(modelSet, referenceSet).size();
    double fn = CollectionUtils.subtract(referenceSet, modelSet).size();

    // F1 score: 2tp / (2tp+fp+fn)
    double denominator = 2 * tp + fp + fn;
    return (denominator == 0) ? Double.NaN : (2 * tp) / denominator;
  }

  /**
   * Calculates the F1-score using a fuzzy reference set (where each item has a probability) against
   * a crisp model set.
   *
   * @param fuzzyReferenceSet A map where keys are the reference interactions and values are their
   *     probabilities (between 0.0 and 1.0).
   * @param modelSet The collection representing the interactions found in the model being
   *     evaluated.
   * @param <T> The type of elements in the collections/map keys (e.g., AnalyzedBasePair,
   *     ConsensusInteraction).
   * @return The fuzzy F1-score (between 0.0 and 1.0). Returns NaN if the denominator (2*tp + fp +
   *     fn) is zero.
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
      }
    }

    double denominator = 2 * tp + fp + fn;
    return (denominator == 0) ? Double.NaN : (2 * tp) / denominator;
  }
}
