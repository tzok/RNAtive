package pl.poznan.put.varna.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.awt.Color;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class Stacking {
  @JsonProperty("id1")
  public String id1;

  @JsonProperty("id2")
  public String id2;

  @JsonProperty("color")
  public String color;

  @JsonProperty("thickness")
  public Double thickness; // Using Double to allow for null if not specified

  // Transient field to store the parsed color object
  public transient Optional<Color> parsedColor = Optional.empty();

  // Getter for the parsed color
  public Optional<Color> getParsedColor() {
    return parsedColor;
  }

  @Override
  public String toString() {
    return "Stacking{"
        + "id1='"
        + id1
        + '\''
        + ", id2='"
        + id2
        + '\''
        + ", color='"
        + color
        + '\''
        + ", thickness="
        + thickness
        + '}';
  }
}
