package pl.poznan.put.api.dto;

import java.util.List;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.util.ReferenceStructureUtil;

public record TaskResult(
    List<RankedModel> rankedModels,
    ReferenceStructureUtil.ReferenceParseResult referenceStructure,
    String dotBracket) {}
