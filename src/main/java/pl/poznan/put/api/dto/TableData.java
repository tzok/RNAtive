package pl.poznan.put.api.dto;

import java.util.List;

public record TableData(List<String> headers, List<List<Object>> rows) {}
