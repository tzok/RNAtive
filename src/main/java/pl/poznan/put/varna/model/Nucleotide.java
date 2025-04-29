package pl.poznan.put.varna.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.Color;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Nucleotide {
  @JsonProperty("id")
  public int id;

  @JsonProperty("number")
  public int number;

  @JsonProperty("char")
  public String character;

  @JsonProperty("outlineColor")
  public String outlineColor;

  @JsonProperty("innerColor")
  public String innerColor;

  @JsonProperty("nameColor")
  public String nameColor;

  // Transient fields to store the parsed color objects
  public transient Optional<Color> parsedOutlineColor = Optional.empty();
  public transient Optional<Color> parsedInnerColor = Optional.empty();
  public transient Optional<Color> parsedNameColor = Optional.empty();

  // Getters for the parsed colors (optional, but good practice)
  public Optional<Color> getParsedOutlineColor() {
    return parsedOutlineColor;
  }

  public Optional<Color> getParsedInnerColor() {
    return parsedInnerColor;
  }

  public Optional<Color> getParsedNameColor() {
    return parsedNameColor;
  }

  @Override
  public String toString() {
    return "Nucleotide{"
        + "id="
        + id
        + ", number="
        + number
        + ", character='"
        + character
        + '\''
        + ", outlineColor='"
        + outlineColor
        + '\''
        + ", innerColor='"
        + innerColor
        + '\''
        + ", nameColor='"
        + nameColor
        + '\''
        + '}';
  }
}
