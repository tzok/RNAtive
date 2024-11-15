package pl.poznan.put.api.dto;

import java.util.List;

public record CsvTablesResponse(
    String ranking,
    String canonicalPairs,
    String nonCanonicalPairs,
    String stackings,
    List<String> fileNames) {}
