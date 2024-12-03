package pl.poznan.put;

import java.util.Collection;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.math3.util.FastMath;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.ClassifiedBasePair;

public class InteractionNetworkFidelity {
  private InteractionNetworkFidelity() {
    super();
  }

  public static double calculate(
      Iterable<? extends ClassifiedBasePair> correctBasePairs,
      Iterable<? extends ClassifiedBasePair> modelBasePairs) {
    var tp = CollectionUtils.intersection(correctBasePairs, modelBasePairs).size();
    var fp = CollectionUtils.subtract(modelBasePairs, correctBasePairs).size();
    var fn = CollectionUtils.subtract(correctBasePairs, modelBasePairs).size();
    var ppv = tp / (tp + fp);
    var sty = tp / (tp + fn);
    return FastMath.sqrt(ppv * sty);
  }

  public static double calculateFuzzy(
      Map<AnalyzedBasePair, Double> fuzzyInteractions,
      Collection<AnalyzedBasePair> modelBasePairs) {
    var tp = 0.0;
    var fp = 0.0;
    var fn = 0.0;

    for (var entry : fuzzyInteractions.entrySet()) {
      if (modelBasePairs.contains(entry.getKey())) {
        tp += entry.getValue();
        fp += 1.0 - entry.getValue();
      } else {
        fn += entry.getValue();
      }
    }

    var ppv = tp / (tp + fp);
    var sty = tp / (tp + fn);
    return FastMath.sqrt(ppv * sty);
  }
}
