package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.util.ReferenceStructureUtil;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbParser;
import pl.poznan.put.structure.AnalyzedBasePair;

@Service
public class ComputeService {
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final CsvGenerationService csvGenerationService;
  private final AnalysisClient analysisClient;

  public ComputeService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      CsvGenerationService csvGenerationService,
      AnalysisClient analysisClient) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.csvGenerationService = csvGenerationService;
    this.analysisClient = analysisClient;
  }

  public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
    Task task = new Task();
    task.setRequest(objectMapper.writeValueAsString(request));
    task = taskRepository.save(task);

    // Start async computation
    processTaskAsync(task.getId());

    return new ComputeResponse(task.getId());
  }

  @Async
  public void processTaskAsync(String taskId) {
    Task task = taskRepository.findById(taskId).orElseThrow();
    try {
      task.setStatus(TaskStatus.PROCESSING);
      taskRepository.save(task);

      ComputeRequest request = objectMapper.readValue(task.getRequest(), ComputeRequest.class);

      // Process each file through the analysis service and build models
      var analyzedModels =
          request.files().stream()
              .map(
                  file -> {
                    String jsonResult = analysisClient.analyze(file.content(), request.analyzer());
                    try {
                      var structure2D = objectMapper.readValue(jsonResult, BaseInteractions.class);
                      var structure3D = new PdbParser(false).parse(file.content()).get(0);
                      return new AnalyzedModel(file.name(), structure3D, structure2D);
                    } catch (JsonProcessingException e) {
                      return null;
                    }
                  })
              .toList();

      // Check if any model failed to parse
      if (analyzedModels.stream().anyMatch(Objects::isNull)) {
        task.setStatus(TaskStatus.FAILED);
        taskRepository.save(task);
        return;
      }

      // Get sequence from first model for reference structure
      var firstModel = analyzedModels.get(0);
      String sequence =
          firstModel.residueIdentifiers().stream()
              .map(PdbNamedResidueIdentifier::oneLetterName)
              .map(String::valueOf)
              .collect(Collectors.joining());

      // Read reference structure if dot-bracket is provided
      List<AnalyzedBasePair> referenceStructure =
          ReferenceStructureUtil.readReferenceStructure(request.dotBracket(), sequence, firstModel);

      // TODO: Use referenceStructure in the analysis

      // Convert to ranked models and store result
      var results =
          analyzedModels.stream()
              .map(model -> new RankedModel(model, 1.0)) // TODO: Calculate proper INF
              .collect(Collectors.toList());
      task.setResult(objectMapper.writeValueAsString(results));
      task.setStatus(TaskStatus.COMPLETED);
    } catch (Exception e) {
      task.setStatus(TaskStatus.FAILED);
    }
    taskRepository.save(task);
  }

  public TaskStatusResponse getTaskStatus(String taskId) {
    Task task = taskRepository.findById(taskId).orElseThrow();
    return new TaskStatusResponse(task.getId(), task.getStatus(), task.getCreatedAt());
  }

  public String getTaskSvg(String taskId) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    if (task.getSvg() == null) {
      throw new IllegalStateException("SVG visualization not available");
    }

    return task.getSvg();
  }

  public CsvTablesResponse getCsvTables(String taskId) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    String resultJson = task.getResult();
    List<RankedModel> results = objectMapper.readValue(resultJson, new TypeReference<>() {});
    if (results == null || results.isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    int totalModelCount = results.size();
    var allInteractions =
        results.stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::basePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    // Collect all interactions from all models
    var allCanonicalPairs =
        results.stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::canonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allNonCanonicalPairs =
        results.stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::nonCanonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allStackings =
        results.stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::stackings)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    String rankingCsv = csvGenerationService.generateRankingCsv(results);
    String canonicalCsv =
        csvGenerationService.generatePairsCsv(allCanonicalPairs, allInteractions, totalModelCount);
    String nonCanonicalCsv =
        csvGenerationService.generatePairsCsv(
            allNonCanonicalPairs, allInteractions, totalModelCount);
    String stackingsCsv =
        csvGenerationService.generateStackingsCsv(allStackings, allInteractions, totalModelCount);

    return new CsvTablesResponse(rankingCsv, canonicalCsv, nonCanonicalCsv, stackingsCsv);
  }

  public ModelCsvTablesResponse getModelCsvTables(String taskId, String filename) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    String resultJson = task.getResult();
    List<RankedModel> results = objectMapper.readValue(resultJson, new TypeReference<>() {});
    if (results == null || results.isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    // Find the model with matching filename
    RankedModel targetModel =
        results.stream()
            .filter(model -> model.getName().equals(filename))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + filename));

    int totalModelCount = results.size();
    var allInteractions =
        results.stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::basePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    String canonicalCsv =
        csvGenerationService.generatePairsCsv(
            targetModel.getAnalyzedModel().canonicalBasePairs(), allInteractions, totalModelCount);
    String nonCanonicalCsv =
        csvGenerationService.generatePairsCsv(
            targetModel.getAnalyzedModel().nonCanonicalBasePairs(),
            allInteractions,
            totalModelCount);
    String stackingsCsv =
        csvGenerationService.generateStackingsCsv(
            targetModel.getAnalyzedModel().stackings(), allInteractions, totalModelCount);

    return new ModelCsvTablesResponse(canonicalCsv, nonCanonicalCsv, stackingsCsv);
  }
}
