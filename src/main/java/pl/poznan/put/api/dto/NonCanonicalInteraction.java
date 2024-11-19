package pl.poznan.put.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NonCanonicalInteraction(
    @JsonProperty("residueLeft") Residue left,
    @JsonProperty("residueRight") Residue right,
    String leontisWesthof) {}
