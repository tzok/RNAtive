package pl.poznan.put.api.dto;

import java.util.List;
import pl.poznan.put.RankedModel;

public record TaskResult(
    List<RankedModel> rankedModels,
    List<pl.poznan.put.structure.BasePair> referenceStructure,
    String dotBracket) {}
