package pl.poznan.put.api.dto;

import java.util.List;
import pl.poznan.put.Analyzer;
import pl.poznan.put.ConsensusMode;

public record ComputeRequest(
    List<FileData> files,
    Double confidenceLevel,
    Analyzer analyzer,
    ConsensusMode consensusMode,
    String dotBracket,
    Boolean applyMolProbityFilter) {
  public ComputeRequest {
    // Set defaults if null
    if (analyzer == null) {
      analyzer = Analyzer.BPNET;
    }
    if (consensusMode == null) {
      consensusMode = ConsensusMode.CANONICAL;
    }
    if (applyMolProbityFilter == null) {
      applyMolProbityFilter = true;
    }

    // Validate confidence level
    if (confidenceLevel != null && (confidenceLevel < 0 || confidenceLevel > 1)) {
      throw new IllegalArgumentException("Confidence level must be between 0 and 1");
    }
  }
}
