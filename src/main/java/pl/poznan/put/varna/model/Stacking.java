package pl.poznan.put.varna.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Stacking {
  @JsonProperty("id1")
  public String id1;

  @JsonProperty("id2")
  public String id2;

  @JsonProperty("color")
  public String color;

  @JsonProperty("thickness")
  public Double thickness; // Using Double to allow for null if not specified

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
