package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
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
import pl.poznan.put.api.model.VisualizationTool;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.util.DrawerVarnaTz;
import pl.poznan.put.api.util.ReferenceStructureUtil;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbParser;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.formats.BpSeq;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;
import pl.poznan.put.utility.svg.Format;
import pl.poznan.put.utility.svg.SVGHelper;

@Service
public class ComputeService {
  private static final Logger logger = LoggerFactory.getLogger(ComputeService.class);
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final AnalysisClient analysisClient;
  private final VisualizationClient visualizationClient;
  private final DrawerVarnaTz drawerVarnaTz;
  private final VisualizationService visualizationService;
  private final ConversionClient conversionClient;

  public ComputeService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      AnalysisClient analysisClient,
      VisualizationClient visualizationClient,
      VisualizationService visualizationService,
      ConversionClient conversionClient,
      DrawerVarnaTz drawerVarnaTz) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.analysisClient = analysisClient;
    this.visualizationClient = visualizationClient;
    this.visualizationService = visualizationService;
    this.conversionClient = conversionClient;
    this.drawerVarnaTz = drawerVarnaTz;
  }

  public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
    logger.info("Submitting new computation task with {} files", request.files().size());
    var task = new Task();
    task.setRequest(objectMapper.writeValueAsString(request));
    task.setStatus(TaskStatus.PENDING); // Explicitly set initial status
    task = taskRepository.save(task);
    var taskId = task.getId();

    // Schedule async processing without waiting
    processTaskAsync(taskId);

    return new ComputeResponse(taskId);
  }

  @Async
  public void processTaskAsync(String taskId) {
    logger.info("Starting async processing of task {}", taskId);
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    try {
      task.setStatus(TaskStatus.PROCESSING);
      task = taskRepository.save(task);

      var request = objectMapper.readValue(task.getRequest(), ComputeRequest.class);

      var analyzedModels = parseAndAnalyzeFiles(request);
      if (analyzedModels.stream().anyMatch(Objects::isNull)) {
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Failed to parse one or more models");
        taskRepository.save(task);
        return;
      }

      var firstModel = analyzedModels.get(0);
      var sequence = extractSequence(firstModel);
      var referenceStructure =
          ReferenceStructureUtil.readReferenceStructure(request.dotBracket(), sequence, firstModel);
      var allInteractions = collectAllInteractions(analyzedModels);
      var threshold = calculateThreshold(request);
      var correctConsideredInteractions =
          computeCorrectInteractions(
              request.consensusMode(), analyzedModels, referenceStructure, allInteractions, threshold);
      resolveConflicts(request, correctConsideredInteractions, allInteractions);
      var rankedModels =
          generateRankedModels(request, analyzedModels, correctConsideredInteractions);
      var taskResult = new TaskResult(rankedModels, referenceStructure);
      var resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      var dotBracket = generateDotBracket(request, firstModel, correctConsideredInteractions);
      var svg =
          generateVisualization(request, firstModel, correctConsideredInteractions, dotBracket);
      task.setSvg(svg);

      task.setStatus(TaskStatus.COMPLETED);
    } catch (Exception e) {
      logger.error("Task {} failed with error", taskId, e);
      task.setStatus(TaskStatus.FAILED);
      task.setMessage("Error processing task: " + e.getMessage());
    }
    taskRepository.save(task);
  }

  private List<AnalyzedModel> parseAndAnalyzeFiles(ComputeRequest request) {
    return request.files().parallelStream()
        .map(
            file -> {
              var jsonResult = analysisClient.analyze(file.content(), request.analyzer());
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
  }

  private String extractSequence(AnalyzedModel firstModel) {
    return firstModel.residueIdentifiers().stream()
        .map(PdbNamedResidueIdentifier::oneLetterName)
        .map(String::valueOf)
        .collect(Collectors.joining());
  }

  private HashBag<AnalyzedBasePair> collectAllInteractions(List<AnalyzedModel> analyzedModels) {
    return analyzedModels.stream()
        .map(AnalyzedModel::basePairsAndStackings)
        .flatMap(Collection::stream)
        .collect(Collectors.toCollection(HashBag::new));
  }

  private int calculateThreshold(ComputeRequest request) {
    return (int) FastMath.ceil(request.confidenceLevel() * request.files().size());
  }

  private Set<AnalyzedBasePair> computeCorrectInteractions(
      ConsensusMode consensusMode,
      List<AnalyzedModel> analyzedModels,
      List<AnalyzedBasePair> referenceStructure,
      HashBag<AnalyzedBasePair> allInteractions,
      int threshold) {
    switch (consensusMode) {
      case CANONICAL:
        var canonicalBasePairs =
            analyzedModels.stream()
                .map(AnalyzedModel::canonicalBasePairs)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(HashBag::new));

        return canonicalBasePairs.stream()
            .filter(
                classifiedBasePair ->
                    referenceStructure.contains(classifiedBasePair)
                        || canonicalBasePairs.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

      case NON_CANONICAL:
        var nonCanonicalBasePairs =
            analyzedModels.stream()
                .map(AnalyzedModel::nonCanonicalBasePairs)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(HashBag::new));

        return nonCanonicalBasePairs.stream()
            .filter(
                classifiedBasePair ->
                    referenceStructure.contains(classifiedBasePair)
                        || nonCanonicalBasePairs.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

      case STACKING:
        var stackings =
            analyzedModels.stream()
                .map(AnalyzedModel::stackings)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(HashBag::new));

        return stackings.stream()
            .filter(
                classifiedBasePair ->
                    referenceStructure.contains(classifiedBasePair)
                        || stackings.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

      case ALL:
        return allInteractions.stream()
            .filter(
                classifiedBasePair ->
                    referenceStructure.contains(classifiedBasePair)
                        || allInteractions.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

      default:
        throw new IllegalArgumentException("Unsupported ConsensusMode: " + consensusMode);
    }
  }

  private void resolveConflicts(
      ComputeRequest request,
      Set<AnalyzedBasePair> correctConsideredInteractions,
      HashBag<AnalyzedBasePair> allInteractions) {
    if (request.consensusMode() != ConsensusMode.STACKING) {
      for (var leontisWesthof : LeontisWesthof.values()) {
        var conflicting =
            conflictingBasePairs(correctConsideredInteractions, leontisWesthof, allInteractions);

        while (!conflicting.isEmpty()) {
          correctConsideredInteractions.remove(conflicting.get(0));
          conflicting =
              conflictingBasePairs(correctConsideredInteractions, leontisWesthof, allInteractions);
        }
      }
    }
  }

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
              var basePair = candidate.basePair();
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

  private List<RankedModel> generateRankedModels(
      ComputeRequest request,
      List<AnalyzedModel> analyzedModels,
      Set<AnalyzedBasePair> correctConsideredInteractions) {
    var rankedModels =
        analyzedModels.stream()
            .map(
                model -> {
                  var modelInteractions =
                      model.streamBasePairs(request.consensusMode()).collect(Collectors.toSet());
                  var inf =
                      InteractionNetworkFidelity.calculate(
                          correctConsideredInteractions, modelInteractions);
                  return new RankedModel(model, inf);
                })
            .collect(Collectors.toList());

    var infs =
        rankedModels.stream()
            .map(RankedModel::getInteractionNetworkFidelity)
            .sorted(Comparator.reverseOrder())
            .toList();
    rankedModels.forEach(
        rankedModel ->
            rankedModel.setRank(infs.indexOf(rankedModel.getInteractionNetworkFidelity()) + 1));
    rankedModels.sort(Comparator.reverseOrder());

    return rankedModels;
  }

  private String generateDotBracket(
      ComputeRequest request,
      AnalyzedModel firstModel,
      Set<AnalyzedBasePair> correctConsideredInteractions)
      throws Exception {
    var residues = firstModel.residueIdentifiers();
    var canonicalPairs =
        correctConsideredInteractions.stream()
            .filter(pair -> pair.leontisWesthof().isCanonical())
            .collect(Collectors.toSet());
    var bpseq = BpSeq.fromBasePairs(residues, canonicalPairs.toString());
    return conversionClient.convertBpseqToDotBracket(bpseq.toString());
  }

  private String generateVisualization(
      ComputeRequest request,
      AnalyzedModel firstModel,
      Set<AnalyzedBasePair> correctConsideredInteractions,
      String dotBracket) {
    try {
      String svg;
      if (request.visualizationTool() == VisualizationTool.VARNA) {
        var dotBracketObj =
            ImmutableDefaultDotBracketFromPdb.of(
                extractSequence(firstModel), dotBracket.split("\n")[1], firstModel.structure3D());
        var svgDoc =
            drawerVarnaTz.drawSecondaryStructure(
                dotBracketObj,
                firstModel.structure3D(),
                new ArrayList<>(correctConsideredInteractions));
        var svgBytes = SVGHelper.export(svgDoc, Format.SVG);
        svg = new String(svgBytes);
      } else {
        var visualizationInput =
            visualizationService.prepareVisualizationInput(firstModel, dotBracket);
        var visualizationJson = objectMapper.writeValueAsString(visualizationInput);
        svg = visualizationClient.visualize(visualizationJson, request.visualizationTool());
      }
      return svg;
    } catch (Exception e) {
      logger.warn("Visualization generation failed", e);
      throw new RuntimeException("Visualization generation failed: " + e.getMessage(), e);
    }
  }

  public TaskStatusResponse getTaskStatus(String taskId) {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    return new TaskStatusResponse(
        task.getId(), task.getStatus(), task.getCreatedAt(), task.getMessage());
  }

  public String getTaskSvg(String taskId) {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    if (task.getSvg() == null) {
      throw new ResourceNotFoundException("SVG visualization not available");
    }

    return task.getSvg();
  }

  public TablesResponse getTables(String taskId) throws Exception {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    var resultJson = task.getResult();
    var taskResult = objectMapper.readValue(resultJson, TaskResult.class);
    var results = taskResult.rankedModels();
    if (results == null || results.isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    var totalModelCount = results.size();
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
    var rankingTable = generateRankingTable(results);
    var canonicalTable =
        generatePairsTable(
            allCanonicalPairs, allInteractions, totalModelCount, taskResult.referenceStructure());
    var nonCanonicalTable =
        generatePairsTable(
            allNonCanonicalPairs,
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    var stackingsTable = generateStackingsTable(allStackings, allInteractions, totalModelCount);

    var fileNames = results.stream().map(RankedModel::getName).collect(Collectors.toList());

    return new TablesResponse(
        rankingTable, canonicalTable, nonCanonicalTable, stackingsTable, fileNames);
  }

  private TableData generateRankingTable(List<RankedModel> models) {
    var headers = List.of("Rank", "File name", "INF");
    var rows =
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
    var headers = List.of("Nt1", "Nt2", "Leontis-Westhof", "Confidence", "Is reference?");
    var rows =
        pairs.stream()
            .map(
                pair -> {
                  var confidence = allInteractions.getCount(pair) / (double) totalModelCount;
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
    var headers = List.of("Nt1", "Nt2", "Confidence");
    var rows =
        stackings.stream()
            .map(
                stacking -> {
                  var confidence = allInteractions.getCount(stacking) / (double) totalModelCount;
                  return List.<Object>of(
                      stacking.basePair().left().toString(),
                      stacking.basePair().right().toString(),
                      confidence);
                })
            .collect(Collectors.toList());
    return new TableData(headers, rows);
  }

  public ModelTablesResponse getModelTables(String taskId, String filename) throws Exception {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    var resultJson = task.getResult();
    var taskResult = objectMapper.readValue(resultJson, TaskResult.class);
    var results = taskResult.rankedModels();
    if (results == null || results.isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    // Find the model with matching filename
    var targetModel =
        results.stream()
            .filter(model -> model.getName().equals(filename))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + filename));

    var totalModelCount = results.size();
    var allInteractions =
        results.stream()
            .map(RankedModel::getBasePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    var canonicalTable =
        generatePairsTable(
            targetModel.getCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    var nonCanonicalTable =
        generatePairsTable(
            targetModel.getNonCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    var stackingsTable =
        generateStackingsTable(targetModel.getStackings(), allInteractions, totalModelCount);

    return new ModelTablesResponse(canonicalTable, nonCanonicalTable, stackingsTable);
  }
}
