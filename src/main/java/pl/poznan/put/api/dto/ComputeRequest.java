package pl.poznan.put.api.dto;

public record ComputeRequest(
    String name, String analyzer, String consensusMode, Double confidenceLevel) {}
