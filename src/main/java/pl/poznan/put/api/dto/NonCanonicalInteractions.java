package pl.poznan.put.api.dto;

import java.util.List;

public record NonCanonicalInteractions(
    List<NonCanonicalInteraction> represented, List<NonCanonicalInteraction> notRepresented) {}
