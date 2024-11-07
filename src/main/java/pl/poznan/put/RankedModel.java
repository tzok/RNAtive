package pl.poznan.put;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RankedModel implements Comparable<RankedModel> {
  @JsonProperty("analyzedModel")
  private AnalyzedModel analyzedModel;

  @JsonProperty("interactionNetworkFidelity")
  private double interactionNetworkFidelity;

  @JsonProperty("rank")
  private int rank = -1;

  // Default constructor for Jackson
  public RankedModel() {}

  public RankedModel(AnalyzedModel analyzedModel, double interactionNetworkFidelity) {
    this.analyzedModel = analyzedModel;
    this.interactionNetworkFidelity = interactionNetworkFidelity;
  }

  public void setAnalyzedModel(AnalyzedModel analyzedModel) {
    this.analyzedModel = analyzedModel;
  }

  public void setInteractionNetworkFidelity(double interactionNetworkFidelity) {
    this.interactionNetworkFidelity = interactionNetworkFidelity;
  }

  public AnalyzedModel getAnalyzedModel() {
    return analyzedModel;
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
