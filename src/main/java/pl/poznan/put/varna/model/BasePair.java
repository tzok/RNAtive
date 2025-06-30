package pl.poznan.put.varna.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.orsay.lri.varna.models.rna.ModeleBP;
import java.awt.Color;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class BasePair {
  @JsonProperty("id1")
  public int id1; // References Nucleotide.id

  @JsonProperty("id2")
  public int id2; // References Nucleotide.id

  @JsonProperty("edge5")
  public ModeleBP.Edge edge5;

  @JsonProperty("edge3")
  public ModeleBP.Edge edge3;

  @JsonProperty("stericity")
  public ModeleBP.Stericity stericity;

  @JsonProperty("canonical")
  public Boolean canonical; // Use Boolean object type to handle absence (null)

  @JsonProperty("color")
  public String color; // Optional color field

  @JsonProperty("thickness")
  public Double thickness; // Optional thickness field

  // Transient field to store the parsed color object
  public transient Optional<Color> parsedColor = Optional.empty();

  // Getter for the parsed color
  public Optional<Color> getParsedColor() {
    return parsedColor;
  }

  @Override
  public String toString() {
    return "BasePair{"
        + "id1="
        + id1
        + ", id2="
        + id2
        + ", edge5="
        + edge5
        + ", edge3="
        + edge3
        + ", stericity="
        + stericity
        + ", canonical="
        + canonical
        + ", color='"
        + color
        + '\''
        + ", thickness="
        + thickness
        + '}';
  }
}
