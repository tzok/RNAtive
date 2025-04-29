package pl.poznan.put.varna.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StructureData {
  @JsonProperty("nucleotides")
  public List<Nucleotide> nucleotides;

  @JsonProperty("basePairs")
  public List<BasePair> basePairs;

  @Override
  public String toString() {
    return "StructureData{"
        + "nucleotides="
        + (nucleotides != null ? nucleotides.size() : 0)
        + " items, basePairs="
        + (basePairs != null ? basePairs.size() : 0)
        + " items}";
  }
}
