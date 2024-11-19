package pl.poznan.put.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VisualizationInput(
    List<Strand> strands,
    List<Residue> residues,
    @JsonProperty("chainsWithResidues") List<Chain> chains,
    @JsonProperty("nonCanonicalInteractions") NonCanonicalInteractions interactions) {}
