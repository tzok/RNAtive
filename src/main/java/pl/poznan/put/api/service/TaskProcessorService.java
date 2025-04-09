package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.F1score;
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
import pl.poznan.put.model.BasePair;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.notation.NucleobaseEdge;
import pl.poznan.put.pdb.*;
import pl.poznan.put.pdb.analysis.*;
import pl.poznan.put.rnalyzer.MolProbityResponse;
import pl.poznan.put.rnalyzer.RnalyzerClient;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.formats.*;
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

  private static List<Pair<AnalyzedBasePair, Double>> sortFuzzySet(
      Map<AnalyzedBasePair, Double> fuzzySet) {
    // Sort the fuzzy canonical pairs by probability in descending order
    return fuzzySet.entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(pair -> Pair.of(-pair.getRight(), pair.getLeft())))
        .toList();
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

      logger.info("Parsing, analyzing and filtering files with MolProbity");
      var analyzedModels = parseAndAnalyzeFiles(request, task);
      if (analyzedModels.stream().anyMatch(Objects::isNull)) {
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Failed to parse one or more models");
        taskRepository.save(task);
        return CompletableFuture.completedFuture(null);
      }

      var modelCount = analyzedModels.size();

      if (modelCount < 2) {
        task.setStatus(TaskStatus.FAILED);
        task.setMessage(
            analyzedModels.isEmpty()
                ? "All models were filtered out by MolProbity criteria"
                : "Only one model remained after MolProbity filtering");
        taskRepository.save(task);
        return CompletableFuture.completedFuture(null);
      }

      logger.info("Reading reference structure");
      var firstModel = analyzedModels.get(0);
      var referenceStructure =
          ReferenceStructureUtil.readReferenceStructure(request.dotBracket(), firstModel);

      logger.info("Collecting all interactions");
      var canonicalPairsBag =
          analyzedModels.stream()
              .flatMap(
                  model ->
                      model.structure2D().basePairs().stream()
                          .filter(BasePair::isCanonical)
                          .map(model::basePairToAnalyzed))
              .collect(Collectors.toCollection(HashBag::new));
      var nonCanonicalPairsBag =
          analyzedModels.stream()
              .flatMap(
                  model ->
                      model.structure2D().basePairs().stream()
                          .filter(basePair -> !basePair.isCanonical())
                          .map(model::basePairToAnalyzed))
              .collect(Collectors.toCollection(HashBag::new));
      var stackingsBag =
          analyzedModels.stream()
              .flatMap(
                  model -> model.structure2D().stackings().stream().map(model::stackingToAnalyzed))
              .collect(Collectors.toCollection(HashBag::new));
      var allInteractionsBag = new HashBag<>(canonicalPairsBag);
      allInteractionsBag.addAll(nonCanonicalPairsBag);
      allInteractionsBag.addAll(stackingsBag);
      var consideredInteractionsBag =
          switch (request.consensusMode()) {
            case CANONICAL -> canonicalPairsBag;
            case NON_CANONICAL -> nonCanonicalPairsBag;
            case STACKING -> stackingsBag;
            case ALL -> allInteractionsBag;
          };

      List<RankedModel> rankedModels;
      DefaultDotBracketFromPdb dotBracket;
      Set<AnalyzedBasePair> correctConsideredInteractions;

      if (request.confidenceLevel() == null) {
        logger.info("Computing fuzzy interactions");
        var fuzzyCanonicalPairs =
            computeFuzzyInteractions(canonicalPairsBag, referenceStructure.basePairs(), modelCount);
        var fuzzyNonCanonicalPairs =
            computeFuzzyInteractions(
                nonCanonicalPairsBag, referenceStructure.basePairs(), modelCount);
        var fuzzyStackings =
            computeFuzzyInteractions(stackingsBag, referenceStructure.basePairs(), modelCount);
        var fuzzyAllInteractions =
            computeFuzzyInteractions(
                allInteractionsBag, referenceStructure.basePairs(), modelCount);
        var fuzzyConsideredInteractions =
            switch (request.consensusMode()) {
              case CANONICAL -> fuzzyCanonicalPairs;
              case NON_CANONICAL -> fuzzyNonCanonicalPairs;
              case STACKING -> fuzzyStackings;
              case ALL -> fuzzyAllInteractions;
            };

        logger.info("Generating fuzzy ranked models");
        rankedModels =
            generateFuzzyRankedModels(
                analyzedModels, fuzzyConsideredInteractions, request.consensusMode());

        logger.info("Generating fuzzy dot bracket notation");
        dotBracket = generateFuzzyDotBracket(firstModel, fuzzyCanonicalPairs);

        logger.info("Compute correct fuzzy interaction (for visualization)");
        correctConsideredInteractions =
            new HashSet<>(
                switch (request.consensusMode()) {
                  case CANONICAL -> correctFuzzyCanonicalPairs(fuzzyCanonicalPairs);
                  case NON_CANONICAL -> correctFuzzyNonCanonicalPairs(fuzzyNonCanonicalPairs);
                  case STACKING -> correctFuzzyStackings(fuzzyStackings);
                  case ALL -> correctFuzzyAllInteraction(
                      fuzzyCanonicalPairs, fuzzyNonCanonicalPairs, fuzzyStackings);
                });
      } else {
        logger.info("Computing correct interactions");
        var threshold = request.confidenceLevel();
        correctConsideredInteractions =
            computeCorrectInteractions(
                request.consensusMode(), consideredInteractionsBag, referenceStructure, threshold);

        logger.info("Generating ranked models");
        rankedModels =
            generateRankedModels(
                request.consensusMode(), analyzedModels, correctConsideredInteractions);

        logger.info("Generating dot bracket notation");
        dotBracket =
            generateDotBracket(
                firstModel,
                computeCorrectInteractions(
                    ConsensusMode.CANONICAL, canonicalPairsBag, referenceStructure, threshold));
      }

      logger.info("Creating task result");
      var taskResult =
          new TaskResult(rankedModels, referenceStructure, dotBracket.toStringWithStrands());
      var resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      logger.info("Generating visualization");
      var svg =
          generateVisualization(
              request.visualizationTool(), firstModel, correctConsideredInteractions, dotBracket);
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

  private DefaultDotBracketFromPdb generateFuzzyDotBracket(
      AnalyzedModel model, Map<AnalyzedBasePair, Double> fuzzyCanonicalPairs) {
    var residues = model.residueIdentifiers();
    var canonicalPairs = correctFuzzyCanonicalPairs(fuzzyCanonicalPairs);
    logger.trace(
        "Generating dot-bracket notation for {} residues and {} fuzzy canonical base pairs",
        residues.size(),
        canonicalPairs.size());
    var bpseq = BpSeq.fromBasePairs(residues, canonicalPairs);
    var converted = conversionClient.convertBpseqToDotBracket(bpseq.toString());
    var sequence = converted.split("\n")[0];
    var structure = converted.split("\n")[1];
    return ImmutableDefaultDotBracketFromPdb.of(sequence, structure, model.structure3D());
  }

  private List<AnalyzedBasePair> correctFuzzyCanonicalPairs(
      Map<AnalyzedBasePair, Double> fuzzyCanonicalPairs) {
    var used = new HashSet<PdbNamedResidueIdentifier>();
    var filtered = new ArrayList<AnalyzedBasePair>();

    for (var entry : sortFuzzySet(fuzzyCanonicalPairs)) {
      var analyzedBasePair = entry.getKey();
      var basePair = analyzedBasePair.basePair();
      var probability = entry.getValue();

      logger.trace("Base pair: {} with probability {}", analyzedBasePair, probability);

      if (used.contains(basePair.left()) || used.contains(basePair.right())) {
        continue;
      }

      used.add(basePair.left());
      used.add(basePair.right());
      filtered.add(analyzedBasePair);
    }

    return filtered;
  }

  private List<RankedModel> generateFuzzyRankedModels(
      List<AnalyzedModel> analyzedModels,
      Map<AnalyzedBasePair, Double> fuzzyInteractions,
      ConsensusMode consensusMode) {
    logger.info("Starting to generate fuzzy ranked models");
    var rankedModels =
        analyzedModels.stream()
            .map(
                model -> {
                  logger.debug("Processing model: {}", model.name());
                  var modelInteractions =
                      model.streamBasePairs(consensusMode).collect(Collectors.toSet());
                  logger.debug("Calculating fuzzy interaction network fidelity");
                  var inf =
                      InteractionNetworkFidelity.calculateFuzzy(
                          fuzzyInteractions, modelInteractions);
                  logger.debug("Fuzzy inf score: {}", inf);
                  logger.debug("Calculating fuzzy f1 score");
                  var f1score = F1score.calculateFuzzy(fuzzyInteractions, modelInteractions);
                  logger.debug("Fuzzy F1 score: {}", f1score);

                  logger.debug("Computing canonical base pairs");
                  var canonicalBasePairs =
                      computeCorrectInteractions(
                          ConsensusMode.CANONICAL,
                          new HashBag<>(model.canonicalBasePairs()),
                          new ReferenceStructureUtil.ReferenceParseResult(List.of(), List.of()),
                          0);
                  logger.debug("Generating dot bracket for model");
                  var dotBracket = generateDotBracket(model, canonicalBasePairs);
                  return new RankedModel(model, inf, f1score, dotBracket);
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

  private Set<AnalyzedBasePair> computeCorrectInteractions(
      ConsensusMode consensusMode,
      HashBag<AnalyzedBasePair> consideredInteractionsBag,
      ReferenceStructureUtil.ReferenceParseResult referenceStructure,
      int threshold) {
    logger.debug("Filtering relevant base pairs based on reference structure and threshold");
    List<PdbNamedResidueIdentifier> residuesUnpairedInReference =
        referenceStructure.markedResidues();
    var correctConsideredInteractions =
        consideredInteractionsBag.stream()
            .filter(
                classifiedBasePair -> {
                  pl.poznan.put.structure.BasePair pair = classifiedBasePair.basePair();
                  return !residuesUnpairedInReference.contains(pair.left())
                      && !residuesUnpairedInReference.contains(pair.right());
                })
            .filter(
                classifiedBasePair ->
                    referenceStructure.basePairs().contains(classifiedBasePair.basePair())
                        || consideredInteractionsBag.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

    if (consensusMode != ConsensusMode.STACKING) {
      logger.debug("Resolving conflicts in base pairs for consensus mode: {}", consensusMode);
      for (var leontisWesthof : LeontisWesthof.values()) {
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

        var conflicting =
            map.keySet().stream()
                .filter(key -> map.get(key).size() > 1)
                .flatMap(key -> map.get(key).stream())
                .distinct()
                .sorted(Comparator.comparingInt(consideredInteractionsBag::getCount))
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
                  .sorted(Comparator.comparingInt(consideredInteractionsBag::getCount))
                  .toList();
        }
      }
    }

    logger.info("Finished computing correct interactions");
    return correctConsideredInteractions;
  }

  private DefaultDotBracketFromPdb generateDotBracket(
      AnalyzedModel model, Collection<AnalyzedBasePair> correctCanonicalBasePairs) {
    var residues = model.residueIdentifiers();
    var canonicalPairs = new HashSet<>(correctCanonicalBasePairs);
    logger.trace(
        "Generating dot-bracket notation for {} residues and {} canonical base pairs",
        residues.size(),
        canonicalPairs.size());
    canonicalPairs.forEach(pair -> logger.trace("Base pair: {}", pair));
    var bpseq = BpSeq.fromBasePairs(residues, canonicalPairs);
    var converted = conversionClient.convertBpseqToDotBracket(bpseq.toString());
    var sequence = converted.split("\n")[0];
    var structure = converted.split("\n")[1];
    return ImmutableDefaultDotBracketFromPdb.of(sequence, structure, model.structure3D());
  }

  private Map<AnalyzedBasePair, Double> computeFuzzyInteractions(
      HashBag<AnalyzedBasePair> consideredInteractionsBag,
      List<pl.poznan.put.structure.BasePair> referenceStructure,
      int modelCount) {
    logger.info("Starting computation of fuzzy interactions");
    return consideredInteractionsBag.stream()
        .distinct()
        .collect(
            Collectors.toMap(
                k -> k,
                v -> {
                  if (referenceStructure.contains(v.basePair())) {
                    return 1.0;
                  }
                  return (double) (consideredInteractionsBag.getCount(v)) / modelCount;
                }));
  }

  private record ParsedModel(String name, String content, PdbModel structure3D) {}

  private List<AnalyzedModel> parseAndAnalyzeFiles(ComputeRequest request, Task task) {
    // Parse all files in parallel
    var models =
        request.files().parallelStream()
            .map(
                fileData ->
                    new ParsedModel(
                        fileData.name(),
                        fileData.content(),
                        new PdbParser()
                            .parse(fileData.content()).stream()
                                .findFirst()
                                .orElseThrow(
                                    () ->
                                        new RuntimeException(
                                            "No structure found in file " + fileData.name()))))
            .toList();
    var identifiersToModels =
        models.stream()
            .collect(Collectors.groupingBy(model -> model.structure3D.namedResidueIdentifiers()));

    // Check if all models have the same sequence of PdbNamedResidueIdentifiers
    if (identifiersToModels.size() > 1) {
      // Even if models have different sequences, they may still be saved
      var sequences =
          identifiersToModels.keySet().stream()
              .map(
                  list ->
                      list.stream()
                          .map(PdbNamedResidueIdentifier::oneLetterName)
                          .map(String::valueOf)
                          .map(String::toUpperCase)
                          .collect(Collectors.joining()))
              .collect(Collectors.toSet());

      // This error is not recoverable
      if (sequences.size() > 1) {
        throw new RuntimeException(formatNucleotideCompositionError(identifiersToModels));
      }

      // Find indices of modified residues in at least one model
      var modified =
          models.stream()
              .map(ParsedModel::structure3D)
              .map(ResidueCollection::residues)
              .map(
                  residues ->
                      IntStream.range(0, residues.size())
                          .boxed()
                          .filter(i -> residues.get(i).isModified())
                          .toList())
              .flatMap(Collection::stream)
              .collect(Collectors.toSet());

      // Regenerate models with unified naming and numbering scheme
      models =
          models.stream()
              .map(
                  model -> {
                    // Always chain A, no insertion codes and increasing number
                    var mapping =
                        IntStream.range(0, model.structure3D.residues().size())
                            .boxed()
                            .collect(
                                Collectors.toMap(
                                    i -> model.structure3D.residues().get(i).identifier(),
                                    i ->
                                        ImmutablePdbResidueIdentifier.of(
                                            "A", i + 1, Optional.empty())));
                    // Force modification detection where other models have it
                    var modresLines =
                        IntStream.range(0, model.structure3D.residues().size())
                            .boxed()
                            .filter(modified::contains)
                            .map(
                                i -> {
                                  var name =
                                      model.structure3D.residues().get(i).standardResidueName();
                                  return ImmutablePdbModresLine.of(
                                      "",
                                      name,
                                      "A",
                                      i + 1,
                                      Optional.empty(),
                                      name.toLowerCase(),
                                      "");
                                })
                            .toList();
                    // Regenerate atoms with new chain and residue numbers
                    var atoms =
                        model.structure3D.atoms().stream()
                            .map(
                                atom -> {
                                  var identifier = PdbResidueIdentifier.from(atom);
                                  if (!mapping.containsKey(identifier)) {
                                    throw new RuntimeException(
                                        "No mapping for residue " + identifier);
                                  }
                                  var mapped = mapping.get(identifier);
                                  return (PdbAtomLine)
                                      ImmutablePdbAtomLine.copyOf(atom)
                                          .withChainIdentifier(mapped.chainIdentifier())
                                          .withResidueNumber(mapped.residueNumber())
                                          .withInsertionCode(Optional.empty());
                                })
                            .toList();
                    // Finally rebuild the PdbModel
                    var regenerated =
                        ImmutableDefaultPdbModel.of(
                            ImmutablePdbHeaderLine.of("", new Date(0L), ""),
                            ImmutablePdbExpdtaLine.of(Collections.emptyList()),
                            ImmutablePdbRemark2Line.of(Double.NaN),
                            1,
                            atoms,
                            modresLines,
                            Collections.emptyList(),
                            "",
                            Collections.emptyList());
                    return new ParsedModel(model.name, regenerated.toPdb(), regenerated);
                  })
              .toList();

      // Perform the check again
      identifiersToModels =
          models.stream()
              .collect(Collectors.groupingBy(model -> model.structure3D.namedResidueIdentifiers()));
      if (identifiersToModels.size() > 1) {
        throw new RuntimeException(formatNucleotideCompositionError(identifiersToModels));
      }
    }

    // Optionally apply MolProbity filtering
    List<ParsedModel> validModels;
    if (request.molProbityFilter() == MolProbityFilter.ALL) {
      validModels = new ArrayList<>(models);
    } else {
      try (var rnalyzerClient = new RnalyzerClient()) {
        rnalyzerClient.initializeSession();
        validModels =
            models.stream()
                .map(
                    model -> {
                      var response =
                          rnalyzerClient.analyzePdbContent(model.content(), model.name());
                      return isModelValid(
                              model.name(), response.structure(), request.molProbityFilter(), task)
                          ? model
                          : null;
                    })
                .toList();
      }
    }

    // Analyze valid models
    return validModels.parallelStream()
        .filter(Objects::nonNull)
        .map(
            model -> {
              try {
                var jsonResult =
                    analysisClient.analyze(model.name, model.content, request.analyzer());
                var structure2D = objectMapper.readValue(jsonResult, BaseInteractions.class);
                return new AnalyzedModel(model.name, model.structure3D, structure2D);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(
                    "Failed to parse analysis result for file: " + model.name, e);
              }
            })
        .toList();
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

  private void addRemovalReason(String modelName, Task task, String reason) {
    logger.info("Model {} removed: {}", modelName, reason);
    task.addRemovalReason(modelName, reason);
  }

  private String formatNucleotideCompositionError(
      Map<List<PdbNamedResidueIdentifier>, List<ParsedModel>> identifiersToModels) {
    var message = new StringBuilder("Models have different nucleotide composition:\n");
    int i = 1;
    for (var entry : identifiersToModels.entrySet()) {
      message.append("Group ");
      message.append(i++);
      message.append("\nModels: ");
      message.append(
          entry.getValue().stream().map(ParsedModel::name).collect(Collectors.joining(", ")));
      message.append("\nIdentifiers: ");
      message.append(
          entry.getKey().stream()
              .map(PdbNamedResidueIdentifier::toString)
              .collect(Collectors.joining(", ")));
      message.append("\n\n");
    }
    return message.toString();
  }

  private boolean validateGoodAndCaution(
      String modelName, MolProbityResponse.Structure structure, Task task) {
    if ("warning".equalsIgnoreCase(structure.rankCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Overall rank category is %s (clashscore: %s, percentile rank: %s)",
              structure.rankCategory(), structure.clashscore(), structure.pctRank()));
      return false;
    }
    if ("warning".equalsIgnoreCase(structure.probablyWrongSugarPuckersCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Sugar pucker category is %s (%s%%)",
              structure.probablyWrongSugarPuckersCategory(),
              structure.pctProbablyWrongSugarPuckers()));
      return false;
    }
    if ("warning".equalsIgnoreCase(structure.badBackboneConformationsCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Backbone conformations category is %s (%s%%)",
              structure.badBackboneConformationsCategory(),
              structure.pctBadBackboneConformations()));
      return false;
    }
    if ("warning".equalsIgnoreCase(structure.badBondsCategory())) {
      addRemovalReason(
          modelName,
          task,
          String.format(
              "Bonds category is %s (%s%%)",
              structure.badBondsCategory(), structure.pctBadBonds()));
      return false;
    }
    if ("warning".equalsIgnoreCase(structure.badAnglesCategory())) {
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

  private List<RankedModel> generateRankedModels(
      ConsensusMode consensusMode,
      List<AnalyzedModel> analyzedModels,
      Set<AnalyzedBasePair> correctConsideredInteractions) {
    logger.info("Starting to generate ranked models");
    var rankedModels =
        analyzedModels.stream()
            .map(
                model -> {
                  logger.debug("Processing model: {}", model.name());
                  var modelInteractions =
                      model.streamBasePairs(consensusMode).collect(Collectors.toSet());
                  logger.debug("Calculating interaction network fidelity");
                  var inf =
                      InteractionNetworkFidelity.calculate(
                          correctConsideredInteractions, modelInteractions);
                  logger.debug("Normal INF score: {}", inf); // TODO returns 0.0
                  logger.debug("Calculating interaction network fidelity");
                  var f1 = F1score.calculate(correctConsideredInteractions, modelInteractions);
                  logger.debug("Normal F1 score: {}", f1); // TODO returns 0.0
                  logger.debug("Computing canonical base pairs");
                  var canonicalBasePairs =
                      computeCorrectInteractions(
                          ConsensusMode.CANONICAL,
                          new HashBag<>(model.canonicalBasePairs()),
                          new ReferenceStructureUtil.ReferenceParseResult(List.of(), List.of()),
                          0);
                  logger.debug("Generating dot bracket for model");
                  var dotBracket = generateDotBracket(model, canonicalBasePairs);
                  return new RankedModel(model, inf, f1, dotBracket);
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

  private String generateVisualization(
      VisualizationTool visualizationTool,
      AnalyzedModel firstModel,
      Set<AnalyzedBasePair> correctConsideredInteractions,
      DotBracketFromPdb dotBracket) {
    try {
      String svg;
      if (visualizationTool == VisualizationTool.VARNA) {
        var svgDoc =
            drawerVarnaTz.drawSecondaryStructure(
                dotBracket,
                firstModel.structure3D(),
                new ArrayList<>(correctConsideredInteractions));
        var svgBytes = SVGHelper.export(svgDoc, Format.SVG);
        svg = new String(svgBytes);
      } else {
        var visualizationInput =
            visualizationService.prepareVisualizationInput(firstModel, dotBracket);
        var visualizationJson = objectMapper.writeValueAsString(visualizationInput);
        svg = visualizationClient.visualize(visualizationJson, visualizationTool);
      }
      return svg;
    } catch (Exception e) {
      logger.warn("Visualization generation failed", e);
      throw new RuntimeException("Visualization generation failed: " + e.getMessage(), e);
    }
  }

  private List<AnalyzedBasePair> correctFuzzyNonCanonicalPairs(
      Map<AnalyzedBasePair, Double> fuzzyNonCanonicalPairs) {
    var used = new HashSet<Pair<PdbNamedResidueIdentifier, NucleobaseEdge>>();
    var filtered = new ArrayList<AnalyzedBasePair>();

    for (var entry : sortFuzzySet(fuzzyNonCanonicalPairs)) {
      logger.trace("Base pair: {} with probability {}", entry.getKey(), entry.getValue());
      var key = entry.getKey();
      var left = Pair.of(key.basePair().left(), key.leontisWesthof().edge5());
      var right = Pair.of(key.basePair().right(), key.leontisWesthof().edge3());

      if (used.contains(left) || used.contains(right)) {
        continue;
      }

      used.add(left);
      used.add(right);
      filtered.add(key);
    }

    return filtered;
  }

  private List<AnalyzedBasePair> correctFuzzyStackings(
      Map<AnalyzedBasePair, Double> fuzzyStackings) {
    return sortFuzzySet(fuzzyStackings).stream().map(Pair::getLeft).toList();
  }

  private List<AnalyzedBasePair> correctFuzzyAllInteraction(
      Map<AnalyzedBasePair, Double> fuzzyCanonicalPairs,
      Map<AnalyzedBasePair, Double> fuzzyNonCanonicalPairs,
      Map<AnalyzedBasePair, Double> fuzzyStackings) {
    var result = correctFuzzyCanonicalPairs(fuzzyCanonicalPairs);
    result.addAll(correctFuzzyNonCanonicalPairs(fuzzyNonCanonicalPairs));
    result.addAll(correctFuzzyStackings(fuzzyStackings));
    return result;
  }
}
