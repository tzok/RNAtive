package pl.poznan.put.api.dto;

import java.util.List;
import pl.poznan.put.RankedModel;

public record TaskResultResponse(
    String taskId,
    List<RankedModel> results
) {}
