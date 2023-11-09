package pl.poznan.put;

public enum Analyzer {
  BARNABA,
  BPNET,
  FR3D,
  MCANNOTATE,
  RNAPOLIS,
  RNAVIEW;

  public String urlPart() {
    return switch (this) {
      case BARNABA -> "barnaba";
      case BPNET -> "bpnet";
      case FR3D -> "fr3d";
      case MCANNOTATE -> "mc-annotate";
      case RNAPOLIS -> "rnapolis";
      case RNAVIEW -> "rnaview";
    };
  }
}
