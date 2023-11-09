package pl.poznan.put;

import org.apache.commons.collections4.CollectionUtils;
import pl.poznan.put.structure.ClassifiedBasePair;

public final class InteractionNetworkFidelity {
  private InteractionNetworkFidelity() {
    super();
  }

  public static double calculate(
      final Iterable<? extends ClassifiedBasePair> correctBasePairs,
      final Iterable<? extends ClassifiedBasePair> modelBasePairs) {
    final double tp = CollectionUtils.intersection(correctBasePairs, modelBasePairs).size();
    final double fp = CollectionUtils.subtract(modelBasePairs, correctBasePairs).size();
    final double fn = CollectionUtils.subtract(correctBasePairs, modelBasePairs).size();
    final double ppv = tp / (tp + fp);
    final double sty = tp / (tp + fn);
    return Math.sqrt(ppv * sty);
  }
}
