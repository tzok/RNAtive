package pl.poznan.put;

public class RankedModel implements Comparable<RankedModel> {
  private final AnalyzedModel analyzedModel;
  private final double interactionNetworkFidelity;
  private int rank = -1;

  public RankedModel(AnalyzedModel analyzedModel, double interactionNetworkFidelity) {
    this.analyzedModel = analyzedModel;
    this.interactionNetworkFidelity = interactionNetworkFidelity;
  }

  public double getInteractionNetworkFidelity() {
    return interactionNetworkFidelity;
  }

  public int getRank() {
    return rank;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  public String getName() {
    return analyzedModel.name();
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
