package pl.poznan.put.rnalyzer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MolProbityResponse(Structure structure) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Structure(
      Description description,
      String clashscore,
      String pctRank,
      String rankCategory,
      String pctProbablyWrongSugarPuckers,
      String probablyWrongSugarPuckersCategory,
      String pctBadBackboneConformations,
      String badBackboneConformationsCategory,
      String pctBadBonds,
      String badBondsCategory,
      String pctBadAngles,
      String badAnglesCategory) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Description(String filename, String errors) {}
}
