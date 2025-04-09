package pl.poznan.put.api.dto;

import java.util.List;
import java.util.Objects;
import pl.poznan.put.Analyzer;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.api.model.MolProbityFilter;
import pl.poznan.put.api.model.VisualizationTool;

public record ComputeRequest(
    List<FileData> files,
    Integer confidenceLevel,
    Analyzer analyzer,
    ConsensusMode consensusMode,
    String dotBracket,
    MolProbityFilter molProbityFilter,
    VisualizationTool visualizationTool) {
  public ComputeRequest {
    // Set defaults if null
    if (analyzer == null) {
      analyzer = Analyzer.BPNET;
    }
    if (consensusMode == null) {
      consensusMode = ConsensusMode.CANONICAL;
    }
    if (molProbityFilter == null) {
      // Default to no filtering
      molProbityFilter = MolProbityFilter.ALL;
    }
    if (visualizationTool == null) {
      visualizationTool = VisualizationTool.RNAPUZZLER;
    }

    if (!Objects.isNull(confidenceLevel)
        && (confidenceLevel < 2 || confidenceLevel > files.size())) {
      throw new IllegalArgumentException(
          "Confidence level must be between 2 and the number of files in the request");
    }
  }
}
