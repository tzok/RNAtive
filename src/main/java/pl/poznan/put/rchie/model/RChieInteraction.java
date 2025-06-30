package pl.poznan.put.rchie.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record RChieInteraction(int i, int j, Optional<String> color) {}
