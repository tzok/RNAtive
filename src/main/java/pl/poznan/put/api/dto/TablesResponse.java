package pl.poznan.put.api.dto;

import java.util.List;

public record TablesResponse(
    TableData ranking,
    TableData canonicalPairs,
    TableData nonCanonicalPairs,
    TableData stackings,
    List<String> fileNames,
    String dotBracket,
    String userRequest) {}
