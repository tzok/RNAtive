package pl.poznan.put.api.dto;

public record ModelCsvTablesResponse(
    String canonicalPairs, String nonCanonicalPairs, String stackings) {}
