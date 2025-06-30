package pl.poznan.put.rchie.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record RChieData(
    String sequence,
    Optional<String> title,
    List<RChieInteraction> top,
    List<RChieInteraction> bottom) {}
