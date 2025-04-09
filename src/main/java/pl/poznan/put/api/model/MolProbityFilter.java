package pl.poznan.put.api.model;

public enum MolProbityFilter {
  ALL, // No filtering based on MolProbity scores
  CLASHSCORE, // Filter based only on clashscore rank category
  CLASHSCORE_BONDS_ANGLES // Filter based on clashscore, bad bonds, and bad angles categories
}
