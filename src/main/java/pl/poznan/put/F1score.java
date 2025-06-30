package pl.poznan.put;

import java.util.HashSet;
import java.util.Set;

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
   * @return The F1-score (between 0.0 and 1.0). Returns NaN if the denominator (2*tp + fp + fn) is
   *     zero.
   */
  public static double calculate(
      Set<ConsensusInteraction> referenceSet,
      Set<ConsensusInteraction> modelSet,
      Set<ConsensusInteraction> requiredSet,
      Set<ConsensusInteraction> forbiddenSet) {
    InteractionMetricsUtils.validateRequiredForbidden(requiredSet, forbiddenSet);

    Set<ConsensusInteraction> truePositiveSet = new HashSet<>();
    Set<ConsensusInteraction> falsePositiveSet = new HashSet<>();
    Set<ConsensusInteraction> falseNegativeSet = new HashSet<>();

    Set<ConsensusInteraction> allInteractions =
        InteractionMetricsUtils.buildAllInteractions(
            referenceSet, modelSet, requiredSet, forbiddenSet);

    for (ConsensusInteraction interaction : allInteractions) {
      boolean isInReference = referenceSet.contains(interaction);
      boolean isInModel = modelSet.contains(interaction);
      boolean isRequired = requiredSet.contains(interaction);
      boolean isForbidden = forbiddenSet.contains(interaction);

      if (isForbidden) {
        if (isInModel) {
          falsePositiveSet.add(interaction); // Predicted forbidden -> False Positive
        }
        // Forbidden not predicted -> OK, does not affect metrics
      } else if (isRequired) { // Required interaction (not forbidden)
        if (isInModel) {
          truePositiveSet.add(interaction); // Predicted required -> True Positive
        } else {
          falseNegativeSet.add(interaction); // Not predicted required -> False Negative
        }
      } else if (isInReference) { // Interaction in reference (not required, not forbidden)
        if (isInModel) {
          truePositiveSet.add(interaction); // Predicted from reference -> True Positive
        } else {
          falseNegativeSet.add(interaction); // Not predicted from reference -> False Negative
        }
      } else { // Interaction not in reference, not required, not forbidden
        if (isInModel) {
          falsePositiveSet.add(interaction); // Predicted unexpected -> False Positive
        }
        // Not predicted unexpected -> OK, does not affect metrics
      }
    }

    double tp = truePositiveSet.size();
    double fp = falsePositiveSet.size();
    double fn = falseNegativeSet.size();

    // F1 score: 2tp / (2tp+fp+fn)
    double denominator = 2 * tp + fp + fn;
    return (denominator == 0) ? 0.0 : (2 * tp) / denominator;
  }

  /**
   * Calculates the F1-score using a fuzzy reference set (where each item has a probability) against
   * a crisp model set.
   *
   * @param referenceSet A map where keys are the reference interactions and values are their
   *     probabilities (between 0.0 and 1.0).
   * @param modelSet The collection representing the interactions found in the model being
   *     evaluated.
   * @return The fuzzy F1-score (between 0.0 and 1.0). Returns NaN if the denominator (2*tp + fp +
   *     fn) is zero.
   */
  public static double calculateFuzzy(
      Set<ConsensusInteraction> referenceSet,
      Set<ConsensusInteraction> modelSet,
      Set<ConsensusInteraction> requiredSet,
      Set<ConsensusInteraction> forbiddenSet) {
    InteractionMetricsUtils.validateRequiredForbidden(requiredSet, forbiddenSet);

    Set<ConsensusInteraction> allInteractions =
        InteractionMetricsUtils.buildAllInteractions(
            referenceSet, modelSet, requiredSet, forbiddenSet);

    double tp = 0.0;
    double fp = 0.0;
    double fn = 0.0;

    for (ConsensusInteraction interaction : allInteractions) {
      double probability = interaction.probability();
      double predictedDegree = modelSet.contains(interaction) ? probability : 0.0;

      if (forbiddenSet.contains(interaction)) {
        // If the interaction is forbidden and predicted, it's a false positive
        fp += predictedDegree;
        continue;
      }

      if (requiredSet.contains(interaction)) {
        tp += predictedDegree; // True positive for required interaction
        fn += (1.0 - predictedDegree); // False negative for required interaction
        continue;
      }

      if (referenceSet.contains(interaction)) {
        tp += predictedDegree; // True positive for reference interaction
        fn += (probability - predictedDegree); // False negative for reference interaction
        continue;
      }

      fp += predictedDegree; // False positive for unexpected interaction
    }

    double denominator = 2 * tp + fp + fn;
    return (denominator == 0) ? 0.0 : (2 * tp) / denominator;
  }
}
