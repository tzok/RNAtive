package pl.poznan.put;

import java.util.Collection;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.math3.util.FastMath;
import pl.poznan.put.structure.AnalyzedBasePair;
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
    return FastMath.sqrt(ppv * sty);
  }

  public static double calculateFuzzy(
      final Map<AnalyzedBasePair, Double> fuzzyInteractions,
      final Collection<AnalyzedBasePair> modelBasePairs) {
    double sum = modelBasePairs.stream().mapToDouble(fuzzyInteractions::get).sum();
    return FastMath.sqrt(sum / modelBasePairs.size());
  }
}
