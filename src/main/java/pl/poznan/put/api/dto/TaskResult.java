package pl.poznan.put.api.dto;

import java.util.List;
import pl.poznan.put.RankedModel;
import pl.poznan.put.structure.AnalyzedBasePair;

public record TaskResult(
    List<RankedModel> rankedModels,
    List<AnalyzedBasePair> referenceStructure) {}
