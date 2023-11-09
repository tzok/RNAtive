package pl.poznan.put.model;

public enum Saenger {
  I,
  II,
  III,
  IV,
  V,
  VI,
  VII,
  VIII,
  IX,
  X,
  XI,
  XII,
  XIII,
  XIV,
  XV,
  XVI,
  XVII,
  XVIII,
  XIX,
  XX,
  XXI,
  XXII,
  XXIII,
  XXIV,
  XXV,
  XXVI,
  XXVII,
  XXVIII;

  public pl.poznan.put.notation.Saenger toBioCommons() {
    return pl.poznan.put.notation.Saenger.fromString(this.name());
  }
}
