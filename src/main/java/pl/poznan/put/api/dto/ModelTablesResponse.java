package pl.poznan.put.api.dto;

public record ModelTablesResponse(
    TableData canonicalPairs, TableData nonCanonicalPairs, TableData stackings) {}
