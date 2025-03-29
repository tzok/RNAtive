package pl.poznan.put;

import java.util.Collection;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.ClassifiedBasePair;
public class F1score {
    private F1score() {
        super();
    }
    public static double calculate(
      Iterable<? extends ClassifiedBasePair> correctBasePairs,
      Iterable<? extends ClassifiedBasePair> modelBasePairs) {
    var tp = CollectionUtils.intersection(correctBasePairs, modelBasePairs).size();
    var fp = CollectionUtils.subtract(modelBasePairs, correctBasePairs).size();
    var fn = CollectionUtils.subtract(correctBasePairs, modelBasePairs).size();
    
    //F1 score: 2tp / (2tp+fp+fn)
    var f1score = (2*tp)/(2*tp + fp + fn);
    return f1score;
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

    var f1score = (2*tp)/(2*tp + fp + fn);
    return f1score;
  }
}
