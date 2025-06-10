package pl.poznan.put;

import java.util.HashSet;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;

/** Utility methods for interaction‐metric computations. */
public final class InteractionMetricsUtils {
  private InteractionMetricsUtils() {}

  public static void validateRequiredForbidden(
      Set<ConsensusInteraction> requiredSet, Set<ConsensusInteraction> forbiddenSet) {
    if (!CollectionUtils.intersection(requiredSet, forbiddenSet).isEmpty()) {
      throw new IllegalArgumentException("Required and forbidden sets must not overlap.");
    }
  }

  public static Set<ConsensusInteraction> buildAllInteractions(
      Set<ConsensusInteraction> referenceSet,
      Set<ConsensusInteraction> modelSet,
      Set<ConsensusInteraction> requiredSet,
      Set<ConsensusInteraction> forbiddenSet) {
    Set<ConsensusInteraction> all = new HashSet<>();
    all.addAll(referenceSet);
    all.addAll(modelSet);
    all.addAll(requiredSet);
    all.addAll(forbiddenSet);
    return all;
  }
}
