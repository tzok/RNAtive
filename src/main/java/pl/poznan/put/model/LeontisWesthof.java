package pl.poznan.put.model;

public enum LeontisWesthof {
  cWW,
  cWH,
  cWS,
  cHW,
  cHH,
  cHS,
  cSW,
  cSH,
  cSS,
  tWW,
  tWH,
  tWS,
  tHW,
  tHH,
  tHS,
  tSW,
  tSH,
  tSS;

  public pl.poznan.put.notation.LeontisWesthof toBioCommons() {
    return pl.poznan.put.notation.LeontisWesthof.fromString(this.name().toUpperCase());
  }
}
