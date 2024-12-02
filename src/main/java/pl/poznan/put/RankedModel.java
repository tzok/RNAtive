package pl.poznan.put;

import java.util.List;
import pl.poznan.put.structure.AnalyzedBasePair;

public class RankedModel implements Comparable<RankedModel> {
  private String name;

  private List<AnalyzedBasePair> basePairsAndStackings;

  private List<AnalyzedBasePair> canonicalBasePairs;

  private List<AnalyzedBasePair> nonCanonicalBasePairs;

  private List<AnalyzedBasePair> stackings;

  private double interactionNetworkFidelity;

  private int rank = -1;

  private String dotBracket;
  
  private List<String> removalReasons = new ArrayList<>();

  // Default constructor for Jackson
  public RankedModel() {}

  public RankedModel(
      AnalyzedModel analyzedModel, double interactionNetworkFidelity, String dotBracket) {
    this.name = analyzedModel.name();
    this.basePairsAndStackings = analyzedModel.basePairsAndStackings();
    this.canonicalBasePairs = analyzedModel.canonicalBasePairs();
    this.nonCanonicalBasePairs = analyzedModel.nonCanonicalBasePairs();
    this.stackings = analyzedModel.stackings();
    this.interactionNetworkFidelity = interactionNetworkFidelity;
    this.dotBracket = dotBracket;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<AnalyzedBasePair> getBasePairsAndStackings() {
    return basePairsAndStackings;
  }

  public void setBasePairsAndStackings(List<AnalyzedBasePair> basePairsAndStackings) {
    this.basePairsAndStackings = basePairsAndStackings;
  }

  public List<AnalyzedBasePair> getCanonicalBasePairs() {
    return canonicalBasePairs;
  }

  public void setCanonicalBasePairs(List<AnalyzedBasePair> canonicalBasePairs) {
    this.canonicalBasePairs = canonicalBasePairs;
  }

  public List<AnalyzedBasePair> getNonCanonicalBasePairs() {
    return nonCanonicalBasePairs;
  }

  public void setNonCanonicalBasePairs(List<AnalyzedBasePair> nonCanonicalBasePairs) {
    this.nonCanonicalBasePairs = nonCanonicalBasePairs;
  }

  public List<AnalyzedBasePair> getStackings() {
    return stackings;
  }

  public void setStackings(List<AnalyzedBasePair> stackings) {
    this.stackings = stackings;
  }

  public double getInteractionNetworkFidelity() {
    return interactionNetworkFidelity;
  }

  public void setInteractionNetworkFidelity(double interactionNetworkFidelity) {
    this.interactionNetworkFidelity = interactionNetworkFidelity;
  }

  public int getRank() {
    return rank;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  public String getDotBracket() {
    return dotBracket;
  }

  public void setDotBracket(String dotBracket) {
    this.dotBracket = dotBracket;
  }

  public List<String> getRemovalReasons() {
    return removalReasons;
  }

  public void setRemovalReasons(List<String> removalReasons) {
    this.removalReasons = removalReasons;
  }

  @Override
  public int compareTo(final RankedModel t) {
    if (Double.isNaN(interactionNetworkFidelity) && Double.isNaN(t.interactionNetworkFidelity)) {
      return 0;
    }
    if (Double.isNaN(interactionNetworkFidelity)) {
      return -1;
    }
    if (Double.isNaN(t.interactionNetworkFidelity)) {
      return 1;
    }
    return Double.compare(interactionNetworkFidelity, t.interactionNetworkFidelity);
  }
}
