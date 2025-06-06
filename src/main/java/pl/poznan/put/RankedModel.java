package pl.poznan.put;

import java.util.List;
import pl.poznan.put.structure.AnalyzedBasePair;

public record RankedModel(
    String name,
    List<AnalyzedBasePair> basePairsAndStackings,
    List<AnalyzedBasePair> canonicalBasePairs,
    List<AnalyzedBasePair> nonCanonicalBasePairs,
    List<AnalyzedBasePair> stackings,
    double interactionNetworkFidelity,
    double f1score,
    int rank,
    String dotBracket)
    implements Comparable<RankedModel> {

  @Override
  public int compareTo(final RankedModel t) {
    if (Double.isNaN(interactionNetworkFidelity) && Double.isNaN(t.interactionNetworkFidelity())) {
      return 0;
    }
    if (Double.isNaN(interactionNetworkFidelity)) {
      return -1;
    }
    if (Double.isNaN(t.interactionNetworkFidelity())) {
      return 1;
    }
    return Double.compare(interactionNetworkFidelity, t.interactionNetworkFidelity());
  }
}
