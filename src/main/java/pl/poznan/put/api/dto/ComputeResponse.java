package pl.poznan.put.api.dto;

import java.util.List;
import pl.poznan.put.RankedModel;

public record ComputeResponse(List<RankedModel> results) {}
