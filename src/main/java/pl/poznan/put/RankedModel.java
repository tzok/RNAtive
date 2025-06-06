package pl.poznan.put;

import java.util.List;
import java.util.Map;
import pl.poznan.put.structure.AnalyzedBasePair;

public record RankedModel(
    String name,
    List<AnalyzedBasePair> basePairsAndStackings,
    List<AnalyzedBasePair> canonicalBasePairs,
    List<AnalyzedBasePair> nonCanonicalBasePairs,
    List<AnalyzedBasePair> stackings,
    Map<ConsensusMode, Double> interactionNetworkFidelity,
    Map<ConsensusMode, Double> f1score,
    Map<ConsensusMode, Integer> rank,
    String dotBracket)
    implements Comparable<RankedModel> {

  @Override
  public int compareTo(final RankedModel t) {
    // Default comparison based on ALL mode INF, if available.
    // External sorting based on requested mode is preferred.
    Double thisInfAll = this.interactionNetworkFidelity().get(ConsensusMode.ALL);
    Double otherInfAll = t.interactionNetworkFidelity().get(ConsensusMode.ALL);

    if (thisInfAll == null && otherInfAll == null) return 0;
    if (thisInfAll == null) return -1; // Models with score are better
    if (otherInfAll == null) return 1;

    if (Double.isNaN(thisInfAll) && Double.isNaN(otherInfAll)) {
      return 0;
    }
    if (Double.isNaN(thisInfAll)) {
      return -1;
    }
    if (Double.isNaN(otherInfAll)) {
      return 1;
    }
    // Higher INF is better, so for ascending sort (default for Comparable),
    // we need to reverse the comparison or sort in reverse order externally.
    // Double.compare(a,b) returns -1 if a < b.
    // If thisInfAll < otherInfAll, we want this to be "greater" in a descending sort.
    // So, we compare otherInfAll to thisInfAll.
    return Double.compare(otherInfAll, thisInfAll);
  }
}
