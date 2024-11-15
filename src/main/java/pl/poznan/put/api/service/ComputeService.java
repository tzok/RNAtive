package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import pl.poznan.put.notation.LeontisWesthof;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.InteractionNetworkFidelity;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.exception.TaskNotFoundException;
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
  private static final Logger logger = LoggerFactory.getLogger(ComputeService.class);
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final CsvGenerationService csvGenerationService;
  private final AnalysisClient analysisClient;
  private final VisualizationClient visualizationClient;

  private List<AnalyzedBasePair> conflictingBasePairs(
      Set<AnalyzedBasePair> candidates, LeontisWesthof leontisWesthof, HashBag<AnalyzedBasePair> allInteractions) {
    MultiValuedMap<PdbNamedResidueIdentifier, AnalyzedBasePair> map = new ArrayListValuedHashMap<>();

    candidates.stream()
        .filter(candidate -> candidate.leontisWesthof() == leontisWesthof)
        .forEach(
            candidate -> {
              var basePair = candidate.basePair();
              map.put(basePair.left(), candidate);
              map.put(basePair.right(), candidate);
            });

    return map.keySet().stream()
        .filter(key -> map.get(key).size() > 1)
        .flatMap(key -> map.get(key).stream())
        .distinct()
        .sorted(Comparator.comparingInt(t -> allInteractions.getCount(t)))
        .collect(Collectors.toList());
  }

  private Set<AnalyzedBasePair> resolveConflicts(Set<AnalyzedBasePair> candidates, HashBag<AnalyzedBasePair> allInteractions) {
    if (candidates.isEmpty()) {
      return candidates;
    }

    Set<AnalyzedBasePair> result = new HashSet<>(candidates);
    
    for (LeontisWesthof leontisWesthof : LeontisWesthof.values()) {
      List<AnalyzedBasePair> conflicting = conflictingBasePairs(result, leontisWesthof, allInteractions);

      while (!conflicting.isEmpty()) {
        result.remove(conflicting.get(0));
        conflicting = conflictingBasePairs(result, leontisWesthof, allInteractions);
      }
    }

    return result;
  }

  public ComputeService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      CsvGenerationService csvGenerationService,
      AnalysisClient analysisClient,
      VisualizationClient visualizationClient) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.csvGenerationService = csvGenerationService;
    this.analysisClient = analysisClient;
    this.visualizationClient = visualizationClient;
  }

  public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
    logger.info("Submitting new computation task with {} files", request.files().size());
    Task task = new Task();
    task.setRequest(objectMapper.writeValueAsString(request));
    task = taskRepository.save(task);

    // Start async computation
    processTaskAsync(task.getId());

    return new ComputeResponse(task.getId());
  }

  @Async
  public void processTaskAsync(String taskId) {
    logger.info("Starting async processing of task {}", taskId);
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
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
                      logger.error("Failed to parse analysis result for file: {}", file.name(), e);
                      return null;
                    }
                  })
              .toList();

      // Check if any model failed to parse
      if (analyzedModels.stream().anyMatch(Objects::isNull)) {
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Failed to parse one or more models");
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

      // Convert to ranked models and store result
      var results =
          analyzedModels.stream()
              .map(
                  model -> {
                    Set<AnalyzedBasePair> modelInteractions =
                        model.streamBasePairs(request.consensusMode()).collect(Collectors.toSet());
                    
                    // Resolve conflicts in model interactions if not stacking
                    if (request.consensusMode() != ConsensusMode.STACKING) {
                      var allModelInteractions = new HashBag<>(modelInteractions);
                      modelInteractions = resolveConflicts(modelInteractions, allModelInteractions);
                    }
                    
                    double inf =
                        InteractionNetworkFidelity.calculate(referenceStructure, modelInteractions);
                    return new RankedModel(model, inf);
                  })
              .collect(Collectors.toList());
      var taskResult = new TaskResult(results, referenceStructure);
      // Store the computation result
      String resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      // Generate visualization if possible
      try {
        String svg = visualizationClient.visualize(resultJson, request.visualizationTool());
        task.setSvg(svg);
      } catch (Exception e) {
        // Log but don't fail the task if visualization fails
        task.setMessage("Warning: Visualization generation failed: " + e.getMessage());
      }

      task.setStatus(TaskStatus.COMPLETED);
    } catch (Exception e) {
      logger.error("Task {} failed with error", taskId, e);
      task.setStatus(TaskStatus.FAILED);
      task.setMessage("Error processing task: " + e.getMessage());
    }
    taskRepository.save(task);
  }

  public TaskStatusResponse getTaskStatus(String taskId) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    return new TaskStatusResponse(
        task.getId(), task.getStatus(), task.getCreatedAt(), task.getMessage());
  }

  public String getTaskSvg(String taskId) throws Exception {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    if (task.getSvg() == null) {
      throw new IllegalStateException("SVG visualization not available");
    }

    return task.getSvg();
  }

  public CsvTablesResponse getCsvTables(String taskId) throws Exception {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    String resultJson = task.getResult();
    TaskResult taskResult = objectMapper.readValue(resultJson, TaskResult.class);
    List<RankedModel> results = taskResult.rankedModels();
    if (results == null || results.isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    int totalModelCount = results.size();
    var allInteractions =
        results.stream()
            .map(RankedModel::getBasePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    // Collect all interactions from all models
    var allCanonicalPairs =
        results.stream()
            .map(RankedModel::getCanonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allNonCanonicalPairs =
        results.stream()
            .map(RankedModel::getNonCanonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allStackings =
        results.stream()
            .map(RankedModel::getStackings)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    String rankingCsv = csvGenerationService.generateRankingCsv(results);
    String canonicalCsv =
        csvGenerationService.generatePairsCsv(
            allCanonicalPairs, allInteractions, totalModelCount, taskResult.referenceStructure());
    String nonCanonicalCsv =
        csvGenerationService.generatePairsCsv(
            allNonCanonicalPairs,
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    String stackingsCsv =
        csvGenerationService.generateStackingsCsv(allStackings, allInteractions, totalModelCount);

    List<String> fileNames =
        results.stream().map(RankedModel::getName).collect(Collectors.toList());

    return new CsvTablesResponse(
        rankingCsv, canonicalCsv, nonCanonicalCsv, stackingsCsv, fileNames);
  }

  public ModelCsvTablesResponse getModelCsvTables(String taskId, String filename) throws Exception {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    String resultJson = task.getResult();
    TaskResult taskResult = objectMapper.readValue(resultJson, TaskResult.class);
    List<RankedModel> results = taskResult.rankedModels();
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
            .map(RankedModel::getBasePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    String canonicalCsv =
        csvGenerationService.generatePairsCsv(
            targetModel.getCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    String nonCanonicalCsv =
        csvGenerationService.generatePairsCsv(
            targetModel.getNonCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    String stackingsCsv =
        csvGenerationService.generateStackingsCsv(
            targetModel.getStackings(), allInteractions, totalModelCount);

    return new ModelCsvTablesResponse(canonicalCsv, nonCanonicalCsv, stackingsCsv);
  }
}
