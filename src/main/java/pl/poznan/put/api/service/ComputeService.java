package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.InteractionNetworkFidelity;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.exception.ResourceNotFoundException;
import pl.poznan.put.api.exception.TaskNotFoundException;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.util.ReferenceStructureUtil;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbParser;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.formats.BpSeq;

@Service
public class ComputeService {
  private static final Logger logger = LoggerFactory.getLogger(ComputeService.class);
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final AnalysisClient analysisClient;
  private final VisualizationClient visualizationClient;
  private final VisualizationService visualizationService;
  private final ConversionClient conversionClient;

  private List<AnalyzedBasePair> conflictingBasePairs(
      Set<AnalyzedBasePair> candidates,
      LeontisWesthof leontisWesthof,
      HashBag<AnalyzedBasePair> allInteractions) {
    MultiValuedMap<PdbNamedResidueIdentifier, AnalyzedBasePair> map =
        new ArrayListValuedHashMap<>();

    candidates.stream()
        .filter(candidate -> candidate.leontisWesthof() == leontisWesthof)
        .forEach(
            candidate -> {
              pl.poznan.put.structure.BasePair basePair = candidate.basePair();
              map.put(basePair.left(), candidate);
              map.put(basePair.right(), candidate);
            });

    return map.keySet().stream()
        .filter(key -> map.get(key).size() > 1)
        .flatMap(key -> map.get(key).stream())
        .distinct()
        .sorted(Comparator.comparingInt(allInteractions::getCount))
        .collect(Collectors.toList());
  }

  public ComputeService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      AnalysisClient analysisClient,
      VisualizationClient visualizationClient,
      VisualizationService visualizationService,
      ConversionClient conversionClient) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.analysisClient = analysisClient;
    this.visualizationClient = visualizationClient;
    this.visualizationService = visualizationService;
    this.conversionClient = conversionClient;
  }

  public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
    logger.info("Submitting new computation task with {} files", request.files().size());
    Task task = new Task();
    task.setRequest(objectMapper.writeValueAsString(request));
    task.setStatus(TaskStatus.PENDING); // Explicitly set initial status
    task = taskRepository.save(task);
    String taskId = task.getId();

    // Schedule async processing without waiting
    processTaskAsync(taskId);

    return new ComputeResponse(taskId);
  }

  @Async
  public void processTaskAsync(String taskId) {
    logger.info("Starting async processing of task {}", taskId);
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    try {
      task.setStatus(TaskStatus.PROCESSING);
      task = taskRepository.save(task);

      ComputeRequest request = objectMapper.readValue(task.getRequest(), ComputeRequest.class);

      // Process each file through the analysis service and build models
      var analyzedModels =
          request.files().parallelStream()
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

      // Collect all interactions
      var canonicalBasePairs =
          analyzedModels.stream()
              .map(AnalyzedModel::canonicalBasePairs)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
      var nonCanonicalBasePairs =
          analyzedModels.stream()
              .map(AnalyzedModel::nonCanonicalBasePairs)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
      var stackings =
          analyzedModels.stream()
              .map(AnalyzedModel::stackings)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
      var allInteractions =
          analyzedModels.stream()
              .map(AnalyzedModel::basePairsAndStackings)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
      var consideredInteractions =
          switch (request.consensusMode()) {
            case CANONICAL -> canonicalBasePairs;
            case NON_CANONICAL -> nonCanonicalBasePairs;
            case STACKING -> stackings;
            case ALL -> allInteractions;
          };

      // Filter out only those in reference structure or those that meet the threshold
      int threshold = (int) FastMath.ceil(request.confidenceLevel() * request.files().size());
      Set<AnalyzedBasePair> correctInteractions =
          consideredInteractions.stream()
              .filter(
                  classifiedBasePair ->
                      referenceStructure.contains(classifiedBasePair)
                          || consideredInteractions.getCount(classifiedBasePair) >= threshold)
              .collect(Collectors.toSet());

      // Filter out invalid combinations of Leontis-Westhof classifications
      if (request.consensusMode() != ConsensusMode.STACKING) {
        for (LeontisWesthof leontisWesthof : LeontisWesthof.values()) {
          List<AnalyzedBasePair> conflicting =
              conflictingBasePairs(correctInteractions, leontisWesthof, allInteractions);

          while (!conflicting.isEmpty()) {
            correctInteractions.remove(conflicting.get(0));
            conflicting =
                conflictingBasePairs(correctInteractions, leontisWesthof, allInteractions);
          }
        }
      }

      // Convert to ranked models and store result
      var rankedModels =
          analyzedModels.stream()
              .map(
                  model -> {
                    Set<AnalyzedBasePair> modelInteractions =
                        model.streamBasePairs(request.consensusMode()).collect(Collectors.toSet());
                    double inf =
                        InteractionNetworkFidelity.calculate(
                            correctInteractions, modelInteractions);
                    return new RankedModel(model, inf);
                  })
              .collect(Collectors.toList());

      List<Double> infs =
          rankedModels.stream()
              .map(RankedModel::getInteractionNetworkFidelity)
              .sorted(Comparator.reverseOrder())
              .toList();
      rankedModels.forEach(
          rankedModel ->
              rankedModel.setRank(infs.indexOf(rankedModel.getInteractionNetworkFidelity()) + 1));
      rankedModels.sort(Comparator.reverseOrder());

      var taskResult = new TaskResult(rankedModels, referenceStructure);

      // Store the computation result
      String resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      // Generate dot-bracket from canonical interactions
      var residues = analyzedModels.get(0).residueIdentifiers();
      var canonicalPairs =
          correctInteractions.stream()
              .filter(canonicalBasePairs::contains)
              .collect(Collectors.toSet());
      var bpseq = BpSeq.fromBasePairs(residues, canonicalPairs);
      String dotBracket = conversionClient.convertBpseqToDotBracket(bpseq.toString());

      // Generate visualization input and SVG
      try {
        var visualizationInput =
            visualizationService.prepareVisualizationInput(analyzedModels.get(0), dotBracket);
        String visualizationJson = objectMapper.writeValueAsString(visualizationInput);
        String svg = visualizationClient.visualize(visualizationJson, request.visualizationTool());
        task.setSvg(svg);
      } catch (Exception e) {
        logger.warn("Visualization generation failed", e);
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

  public String getTaskSvg(String taskId) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    if (task.getSvg() == null) {
      throw new ResourceNotFoundException("SVG visualization not available");
    }

    return task.getSvg();
  }

  public TablesResponse getTables(String taskId) throws Exception {
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

    // Convert CSV data to structured tables
    TableData rankingTable = generateRankingTable(results);
    TableData canonicalTable =
        generatePairsTable(
            allCanonicalPairs, allInteractions, totalModelCount, taskResult.referenceStructure());
    TableData nonCanonicalTable =
        generatePairsTable(
            allNonCanonicalPairs,
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    TableData stackingsTable =
        generateStackingsTable(allStackings, allInteractions, totalModelCount);

    List<String> fileNames =
        results.stream().map(RankedModel::getName).collect(Collectors.toList());

    return new TablesResponse(
        rankingTable, canonicalTable, nonCanonicalTable, stackingsTable, fileNames);
  }

  public ModelTablesResponse getModelTables(String taskId, String filename) throws Exception {
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

    TableData canonicalTable =
        generatePairsTable(
            targetModel.getCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    TableData nonCanonicalTable =
        generatePairsTable(
            targetModel.getNonCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    TableData stackingsTable =
        generateStackingsTable(targetModel.getStackings(), allInteractions, totalModelCount);

    return new ModelTablesResponse(canonicalTable, nonCanonicalTable, stackingsTable);
  }

  private TableData generateRankingTable(List<RankedModel> models) {
    List<String> headers = List.of("Rank", "File name", "INF");
    List<List<Object>> rows =
        models.stream()
            .map(
                model ->
                    List.<Object>of(
                        model.getRank(), model.getName(), model.getInteractionNetworkFidelity()))
            .collect(Collectors.toList());
    return new TableData(headers, rows);
  }

  private TableData generatePairsTable(
      List<? extends AnalyzedBasePair> pairs,
      HashBag<AnalyzedBasePair> allInteractions,
      int totalModelCount,
      List<AnalyzedBasePair> referenceStructure) {
    List<String> headers = List.of("Nt1", "Nt2", "Leontis-Westhof", "Confidence", "Is reference?");
    List<List<Object>> rows =
        pairs.stream()
            .map(
                pair -> {
                  double confidence = allInteractions.getCount(pair) / (double) totalModelCount;
                  return List.<Object>of(
                      pair.basePair().left().toString(),
                      pair.basePair().right().toString(),
                      pair.leontisWesthof().toString(),
                      confidence,
                      referenceStructure.contains(pair));
                })
            .collect(Collectors.toList());
    return new TableData(headers, rows);
  }

  private TableData generateStackingsTable(
      List<? extends AnalyzedBasePair> stackings,
      HashBag<AnalyzedBasePair> allInteractions,
      int totalModelCount) {
    List<String> headers = List.of("Nt1", "Nt2", "Confidence");
    List<List<Object>> rows =
        stackings.stream()
            .map(
                stacking -> {
                  double confidence = allInteractions.getCount(stacking) / (double) totalModelCount;
                  return List.<Object>of(
                      stacking.basePair().left().toString(),
                      stacking.basePair().right().toString(),
                      confidence);
                })
            .collect(Collectors.toList());
    return new TableData(headers, rows);
  }
}
