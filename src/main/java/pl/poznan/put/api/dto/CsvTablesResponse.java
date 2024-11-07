package pl.poznan.put.api.dto;

public record CsvTablesResponse(
    String ranking, String canonicalPairs, String nonCanonicalPairs, String stackings) {}
