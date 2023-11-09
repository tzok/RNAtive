package pl.poznan.put;

public final class Defaults {
  public static final Analyzer ANALYZER = Analyzer.BPNET;
  public static final ConsensusMode DEFAULT_CONSENSUS_MODE = ConsensusMode.CANONICAL;
  public static final String CONFIDENCE_LEVEL = "0.5";

  private Defaults() {
    super();
  }
}
