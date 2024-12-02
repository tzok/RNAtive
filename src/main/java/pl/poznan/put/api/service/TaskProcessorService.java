package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.InteractionNetworkFidelity;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.exception.TaskNotFoundException;
import pl.poznan.put.api.model.MolProbityFilter;
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
import pl.poznan.put.rnalyzer.MolProbityResponse;
import pl.poznan.put.rnalyzer.RnalyzerClient;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.formats.BpSeq;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;
import pl.poznan.put.utility.svg.Format;
import pl.poznan.put.utility.svg.SVGHelper;

@Service
@Transactional
public class TaskProcessorService {
  private static final Logger logger = LoggerFactory.getLogger(TaskProcessorService.class);
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final AnalysisClient analysisClient;
  private final VisualizationClient visualizationClient;
  private final DrawerVarnaTz drawerVarnaTz;
  private final VisualizationService visualizationService;
  private final ConversionClient conversionClient;

  @Autowired
  public TaskProcessorService(
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

  @Async("taskExecutor")
  public CompletableFuture<Void> processTaskAsync(String taskId) {
    logger.info("Starting async processing of task {}", taskId);
    try {
      logger.info("Fetching task from repository");
      var task =
          taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
      task.setStatus(TaskStatus.PROCESSING);
      taskRepository.save(task);

      logger.info("Parsing task request");
      var request = objectMapper.readValue(task.getRequest(), ComputeRequest.class);

      logger.info("Parsing and analyzing files");
      var analyzedModels = parseAndAnalyzeFiles(request, task);
      if (analyzedModels.stream().anyMatch(Objects::isNull)) {
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Failed to parse one or more models");
        taskRepository.save(task);
        return CompletableFuture.completedFuture(null);
      }

      if (analyzedModels.size() < 2) {
        task.setStatus(TaskStatus.FAILED);
        task.setMessage(
            analyzedModels.isEmpty()
                ? "All models were filtered out by MolProbity criteria"
                : "Only one model remained after MolProbity filtering");
        taskRepository.save(task);
        return CompletableFuture.completedFuture(null);
      }

      logger.info("Extracting sequence from the first model");
      var firstModel = analyzedModels.get(0);
      var sequence = extractSequence(firstModel);
      logger.info("Reading reference structure");
      var referenceStructure =
          ReferenceStructureUtil.readReferenceStructure(request.dotBracket(), sequence, firstModel);
      logger.info("Collecting all interactions");
      var allInteractions = collectAllInteractions(analyzedModels);
      logger.info("Calculating threshold");
      var threshold = calculateThreshold(request);
      logger.info("Computing correct interactions");
      var correctConsideredInteractions =
          computeCorrectInteractions(
              request.consensusMode(),
              analyzedModels,
              referenceStructure,
              allInteractions,
              threshold);
      logger.info("Generating ranked models");
      var rankedModels =
          generateRankedModels(request, analyzedModels, correctConsideredInteractions);

      logger.info("Generating dot bracket notation");
      var dotBracket =
          generateDotBracket(
              firstModel,
              computeCorrectInteractions(
                  ConsensusMode.CANONICAL,
                  analyzedModels,
                  referenceStructure,
                  allInteractions,
                  threshold));

      logger.info("Creating task result");
      var taskResult = new TaskResult(rankedModels, referenceStructure, dotBracket);
      var resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      logger.info("Generating visualization");
      var svg =
          generateVisualization(request, firstModel, correctConsideredInteractions, dotBracket);
      task.setSvg(svg);

      logger.info("Task processing completed successfully");
      task.setStatus(TaskStatus.COMPLETED);
      taskRepository.save(task);
    } catch (Exception e) {
      logger.error("Task {} failed with error", taskId, e);
      var taskOptional = taskRepository.findById(taskId);
      if (taskOptional.isPresent()) {
        var task = taskOptional.get();
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Error processing task: " + e.getMessage());
        taskRepository.save(task);
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  private List<AnalyzedModel> parseAndAnalyzeFiles(ComputeRequest request, Task task) {
    var removedModels = new ArrayList<RankedModel>();
    var analyzedModels = new ArrayList<AnalyzedModel>();

    try (var rnalyzerClient = new RnalyzerClient()) {
      if (request.molProbityFilter() != MolProbityFilter.ALL) {
        rnalyzerClient.initializeSession();
      }

      for (var file : request.files()) {
        try {
          var structure3D = new PdbParser().parse(file.content()).get(0);

          // Apply MolProbity filtering early if enabled
          if (request.molProbityFilter() != MolProbityFilter.ALL) {
            var response = rnalyzerClient.analyzePdbContent(structure3D.toPdb(), file.name());
            if (!isModelValid(
                file.name(), response.structure(), request.molProbityFilter(), task)) {
              continue; // Skip analysis for filtered models
            }
          }

          // Only analyze models that passed MolProbity filtering
          var jsonResult = analysisClient.analyze(file.content(), request.analyzer());
          var structure2D = objectMapper.readValue(jsonResult, BaseInteractions.class);
          analyzedModels.add(new AnalyzedModel(file.name(), structure3D, structure2D));
        } catch (JsonProcessingException e) {
          logger.error("Failed to parse analysis result for file: {}", file.name(), e);
        }
      }
    }

    // Check if all models have the same sequence
    if (analyzedModels.stream().allMatch(Objects::nonNull)) {
      var sequences =
          analyzedModels.stream().map(AnalyzedModel::residueIdentifiers).distinct().toList();

      if (sequences.size() > 1) {
        logger.error("Models have different nucleotide composition");
        var message =
            "Models have different nucleotide composition. Found nucleotides: "
                + String.join(
                    ", ", sequences.stream().map(List::toString).collect(Collectors.joining(", ")));
        throw new RuntimeException(message);
      }
    }

    return analyzedModels;
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
    logger.info(
        "Starting computation of correct interactions with consensus mode: {}", consensusMode);
    HashBag<AnalyzedBasePair> relevantBasePairs =
        switch (consensusMode) {
          case CANONICAL -> analyzedModels.stream()
              .map(AnalyzedModel::canonicalBasePairs)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
          case NON_CANONICAL -> analyzedModels.stream()
              .map(AnalyzedModel::nonCanonicalBasePairs)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
          case STACKING -> analyzedModels.stream()
              .map(AnalyzedModel::stackings)
              .flatMap(Collection::stream)
              .collect(Collectors.toCollection(HashBag::new));
          case ALL -> allInteractions;
        };

    logger.debug("Filtering relevant base pairs based on reference structure and threshold");
    Set<AnalyzedBasePair> correctConsideredInteractions =
        relevantBasePairs.stream()
            .filter(
                classifiedBasePair ->
                    referenceStructure.contains(classifiedBasePair)
                        || relevantBasePairs.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

    if (consensusMode != ConsensusMode.STACKING) {
      logger.debug("Resolving conflicts in base pairs for consensus mode: {}", consensusMode);
      for (LeontisWesthof leontisWesthof : LeontisWesthof.values()) {
        MultiValuedMap<PdbNamedResidueIdentifier, AnalyzedBasePair> map =
            new ArrayListValuedHashMap<>();

        correctConsideredInteractions.stream()
            .filter(candidate -> candidate.leontisWesthof() == leontisWesthof)
            .forEach(
                candidate -> {
                  var basePair = candidate.basePair();
                  map.put(basePair.left(), candidate);
                  map.put(basePair.right(), candidate);
                });

        List<AnalyzedBasePair> conflicting =
            map.keySet().stream()
                .filter(key -> map.get(key).size() > 1)
                .flatMap(key -> map.get(key).stream())
                .distinct()
                .sorted(Comparator.comparingInt(allInteractions::getCount))
                .collect(Collectors.toList());

        while (!conflicting.isEmpty()) {
          correctConsideredInteractions.remove(conflicting.get(0));

          // Update the map after removing a conflicting base pair
          map.clear();
          correctConsideredInteractions.stream()
              .filter(candidate -> candidate.leontisWesthof() == leontisWesthof)
              .forEach(
                  candidate -> {
                    var basePair = candidate.basePair();
                    map.put(basePair.left(), candidate);
                    map.put(basePair.right(), candidate);
                  });

          conflicting =
              map.keySet().stream()
                  .filter(key -> map.get(key).size() > 1)
                  .flatMap(key -> map.get(key).stream())
                  .distinct()
                  .sorted(Comparator.comparingInt(allInteractions::getCount))
                  .toList();
        }
      }
    }

    logger.info("Finished computing correct interactions");
    return correctConsideredInteractions;
  }

  private List<RankedModel> generateRankedModels(
      ComputeRequest request,
      List<AnalyzedModel> analyzedModels,
      Set<AnalyzedBasePair> correctConsideredInteractions) {
    logger.info("Starting to generate ranked models");
    var rankedModels =
        analyzedModels.stream()
            .map(
                model -> {
                  logger.debug("Processing model: {}", model.name());
                  var modelInteractions =
                      model.streamBasePairs(request.consensusMode()).collect(Collectors.toSet());
                  logger.debug("Calculating interaction network fidelity");
                  var inf =
                      InteractionNetworkFidelity.calculate(
                          correctConsideredInteractions, modelInteractions);
                  logger.debug("Computing canonical base pairs");
                  var canonicalBasePairs =
                      computeCorrectInteractions(
                          ConsensusMode.CANONICAL, List.of(model), List.of(), new HashBag<>(), 0);
                  logger.debug("Generating dot bracket for model");
                  var dotBracket = generateDotBracket(model, canonicalBasePairs);
                  return new RankedModel(model, inf, dotBracket);
                })
            .collect(Collectors.toList());

    logger.info("Ranking models based on interaction network fidelity");
    var infs =
        rankedModels.stream()
            .map(RankedModel::getInteractionNetworkFidelity)
            .sorted(Comparator.reverseOrder())
            .toList();
    rankedModels.forEach(
        rankedModel ->
            rankedModel.setRank(infs.indexOf(rankedModel.getInteractionNetworkFidelity()) + 1));
    rankedModels.sort(Comparator.reverseOrder());

    logger.info("Finished generating ranked models");
    return rankedModels;
  }

  private String generateDotBracket(
      AnalyzedModel firstModel, Collection<AnalyzedBasePair> correctCanonicalBasePairs) {
    var residues = firstModel.residueIdentifiers();
    var canonicalPairs = new HashSet<>(correctCanonicalBasePairs);
    logger.trace(
        "Generating dot-bracket notation for {} residues and {} canonical base pairs",
        residues.size(),
        canonicalPairs.size());
    canonicalPairs.forEach(pair -> logger.trace("Base pair: {}", pair));
    var bpseq = BpSeq.fromBasePairs(residues, canonicalPairs);
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

  private boolean isModelValid(
      String modelName,
      MolProbityResponse.Structure structure,
      MolProbityFilter filter,
      Task task) {

    if (filter == MolProbityFilter.GOOD_ONLY) {
      return validateGoodOnly(modelName, structure, task);
    } else if (filter == MolProbityFilter.GOOD_AND_CAUTION) {
      return validateGoodAndCaution(modelName, structure, task);
    }
    return true;
  }

  private boolean validateGoodOnly(
      String modelName, MolProbityResponse.Structure structure, Task task) {
    if (!"good".equalsIgnoreCase(structure.rankCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Overall rank category is %s (clashscore: %s, percentile rank: %s)",
              structure.rankCategory(), structure.clashscore(), structure.pctRank()));
      return false;
    }
    if (!"good".equalsIgnoreCase(structure.probablyWrongSugarPuckersCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Sugar pucker category is %s (%s%%)",
              structure.probablyWrongSugarPuckersCategory(),
              structure.pctProbablyWrongSugarPuckers()));
      return false;
    }
    if (!"good".equalsIgnoreCase(structure.badBackboneConformationsCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Backbone conformations category is %s (%s%%)",
              structure.badBackboneConformationsCategory(),
              structure.pctBadBackboneConformations()));
      return false;
    }
    if (!"good".equalsIgnoreCase(structure.badBondsCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Bonds category is %s (%s%%)",
              structure.badBondsCategory(), structure.pctBadBonds()));
      return false;
    }
    if (!"good".equalsIgnoreCase(structure.badAnglesCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Angles category is %s (%s%%)",
              structure.badAnglesCategory(), structure.pctBadAngles()));
      return false;
    }
    return true;
  }

  private boolean validateGoodAndCaution(
      String modelName, MolProbityResponse.Structure structure, Task task) {
    if ("bad".equalsIgnoreCase(structure.rankCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Overall rank category is %s (clashscore: %s, percentile rank: %s)",
              structure.rankCategory(), structure.clashscore(), structure.pctRank()));
      return false;
    }
    if ("bad".equalsIgnoreCase(structure.probablyWrongSugarPuckersCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Sugar pucker category is %s (%s%%)",
              structure.probablyWrongSugarPuckersCategory(),
              structure.pctProbablyWrongSugarPuckers()));
      return false;
    }
    if ("bad".equalsIgnoreCase(structure.badBackboneConformationsCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Backbone conformations category is %s (%s%%)",
              structure.badBackboneConformationsCategory(),
              structure.pctBadBackboneConformations()));
      return false;
    }
    if ("bad".equalsIgnoreCase(structure.badBondsCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Bonds category is %s (%s%%)",
              structure.badBondsCategory(), structure.pctBadBonds()));
      return false;
    }
    if ("bad".equalsIgnoreCase(structure.badAnglesCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Angles category is %s (%s%%)",
              structure.badAnglesCategory(), structure.pctBadAngles()));
      return false;
    }
    return true;
  }

  private void addRemovalReason(String modelName, Task task, String reason) {
    logger.info("Model {} removed: {}", modelName, reason);
    task.addRemovalReason(String.format("Model %s: %s", modelName, reason));
  }
}
