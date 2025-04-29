package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
import fr.orsay.lri.varna.models.rna.ModeleBP;
import java.util.concurrent.atomic.AtomicInteger;
import pl.poznan.put.api.model.*;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.util.DrawerVarnaTz;
import pl.poznan.put.api.util.ReferenceStructureUtil;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.model.BasePair;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.notation.NucleobaseEdge;
import pl.poznan.put.notation.Stericity;
import pl.poznan.put.pdb.*;
import pl.poznan.put.pdb.analysis.*;
import pl.poznan.put.rna.InteractionType;
import pl.poznan.put.rnalyzer.MolProbityResponse;
import pl.poznan.put.rnalyzer.RnalyzerClient;
import pl.poznan.put.structure.*;
import pl.poznan.put.structure.formats.*;
import pl.poznan.put.utility.svg.Format;
import pl.poznan.put.utility.svg.SVGHelper;
import pl.poznan.put.varna.model.Nucleotide;
import pl.poznan.put.varna.model.StructureData;

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
  private final RnapolisClient rnapolisClient;
  private final VarnaTzClient varnaTzClient;

  @Autowired
  public TaskProcessorService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      AnalysisClient analysisClient,
      VisualizationClient visualizationClient,
      VisualizationService visualizationService,
      ConversionClient conversionClient,
      DrawerVarnaTz drawerVarnaTz,
      RnapolisClient rnapolisClient,
      VarnaTzClient varnaTzClient) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.analysisClient = analysisClient;
    this.visualizationClient = visualizationClient;
    this.visualizationService = visualizationService;
    this.conversionClient = conversionClient;
    this.drawerVarnaTz = drawerVarnaTz;
    this.rnapolisClient = rnapolisClient;
    this.varnaTzClient = varnaTzClient;
  }

  private static List<Pair<AnalyzedBasePair, Double>> sortFuzzySet(
      Map<AnalyzedBasePair, Double> fuzzySet) {
    // Sort the fuzzy canonical pairs by probability in descending order
    return fuzzySet.entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(pair -> Pair.of(-pair.getRight(), pair.getLeft())))
        .toList();
  }

  /**
   * Process files through RNApolis service to unify their format.
   *
   * @param files List of files to process
   * @return List of processed files
   */
  private List<FileData> processFilesWithRnapolis(List<FileData> files) {
    logger.info("Processing {} files with RNApolis service", files.size());
    try {
      List<FileData> processedFiles = rnapolisClient.processFiles(files);
      logger.info("RNApolis processing completed, received {} files", processedFiles.size());
      return processedFiles;
    } catch (Exception e) {
      logger.error("Error processing files with RNApolis", e);
      // Return original files if processing fails
      return files;
    }
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

      // Process files through RNApolis to unify their format
      logger.info("Processing files with RNApolis service");
      List<FileData> processedFiles = processFilesWithRnapolis(request.files());

      // Create a new request with the processed files
      ComputeRequest processedRequest =
          new ComputeRequest(
              processedFiles,
              request.confidenceLevel(),
              request.analyzer(),
              request.consensusMode(),
              request.dotBracket(),
              request.molProbityFilter(),
              request.visualizationTool());

      logger.info("Parsing, analyzing and filtering files with MolProbity");
      var analyzedModels = parseAndAnalyzeFiles(processedRequest, task);
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
            computeFuzzyInteractions(canonicalPairsBag, referenceStructure, modelCount);
        var fuzzyNonCanonicalPairs =
            computeFuzzyInteractions(nonCanonicalPairsBag, referenceStructure, modelCount);
        var fuzzyStackings = computeFuzzyInteractions(stackingsBag, referenceStructure, modelCount);
        var fuzzyAllInteractions =
            computeFuzzyInteractions(allInteractionsBag, referenceStructure, modelCount);
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

    // Filter out pairs with probability 0 before conflict resolution
    var sortedSet = sortFuzzySet(fuzzyCanonicalPairs);
    var positiveProbabilityPairs =
        sortedSet.stream().filter(pair -> pair.getRight() > 0.0).toList();

    for (var entry : positiveProbabilityPairs) {
      var analyzedBasePair = entry.getKey();
      var basePair = analyzedBasePair.basePair();

      logger.trace("Base pair: {} with probability {}", analyzedBasePair, entry.getValue());

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
      ReferenceStructureUtil.ReferenceParseResult referenceStructure,
      int modelCount) {
    logger.info("Starting computation of fuzzy interactions");
    return consideredInteractionsBag.stream()
        .distinct()
        .collect(
            Collectors.toMap(
                k -> k,
                v -> {
                  // If either residue is marked as unpaired in reference, score is 0.0
                  if (referenceStructure.markedResidues().contains(v.basePair().left())
                      || referenceStructure.markedResidues().contains(v.basePair().right())) {
                    return 0.0;
                  }
                  // If the pair exists in the reference structure base pairs, score is 1.0
                  if (referenceStructure.basePairs().contains(v.basePair())) {
                    return 1.0;
                  }
                  // Otherwise, score is the confidence level (frequency)
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
                                .map(model -> model.filteredNewInstance(MoleculeType.RNA))
                                .orElseThrow(
                                    () -> {
                                      String errorFileName = "error-" + fileData.name();
                                      Path tempFilePath = Paths.get("/tmp", errorFileName);
                                      try {
                                        logger.warn(
                                            "No structure found in file {}, saving content to {}",
                                            fileData.name(),
                                            tempFilePath);
                                        Files.writeString(
                                            tempFilePath,
                                            fileData.content(),
                                            StandardOpenOption.CREATE,
                                            StandardOpenOption.WRITE,
                                            StandardOpenOption.TRUNCATE_EXISTING);
                                      } catch (IOException e) {
                                        logger.error(
                                            "Failed to save content of {} to {}",
                                            fileData.name(),
                                            tempFilePath,
                                            e);
                                      }
                                      return new RuntimeException(
                                          "No structure found in file " + fileData.name());
                                    })))
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
      logger.info("MolProbity filtering is set to ALL, skipping filtering.");
      validModels = new ArrayList<>(models);
    } else {
      logger.info("Attempting MolProbity filtering with level: {}", request.molProbityFilter());
      try (var rnalyzerClient = new RnalyzerClient()) {
        rnalyzerClient.initializeSession();
        validModels =
            models.stream()
                .map(
                    model -> {
                      MolProbityResponse response = null;
                      try {
                        response = rnalyzerClient.analyzePdbContent(model.content(), model.name());

                        // Store the MolProbity response JSON in the task
                        try {
                          String responseJson = objectMapper.writeValueAsString(response);
                          task.addMolProbityResponse(model.name(), responseJson);
                        } catch (JsonProcessingException e) {
                          logger.error(
                              "Failed to serialize MolProbityResponse for model {}",
                              model.name(),
                              e);
                          // Optionally store an error message instead of JSON
                          task.addMolProbityResponse(
                              model.name(), "{\"error\": \"Serialization failed\"}");
                        }

                        // Now check validity using the obtained response
                        return isModelValid(
                                model.name(),
                                response.structure(), // Use the response object directly
                                request.molProbityFilter(),
                                task)
                            ? model
                            : null; // Filtered out
                      } catch (Exception e) {
                        logger.warn(
                            "MolProbity analysis failed for model {}: {}. Model will be included.",
                            model.name(),
                            e.getMessage());
                        // Store an error indication if analysis failed before validity check
                        if (response == null) {
                          task.addMolProbityResponse(
                              model.name(),
                              String.format(
                                  "{\"error\": \"MolProbity analysis failed: %s\"}",
                                  e.getMessage()));
                        }
                        return model; // Include model if analysis fails
                      }
                    })
                .filter(Objects::nonNull) // Remove nulls (filtered models)
                .toList();
        logger.info(
            "MolProbity filtering completed. {} models passed out of {}.",
            validModels.size(),
            models.size());
      } catch (Exception e) {
        logger.warn(
            "MolProbity filtering failed due to an error with the RNAlyzer service: {}. Proceeding"
                + " without MolProbity filtering.",
            e.getMessage());
        // If the RNAlyzer service fails entirely, proceed with all models
        validModels = new ArrayList<>(models);
      }
    }

    // Analyze valid models (now contains either filtered or all models)
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

    switch (filter) {
      case ALL:
        return true; // No filtering

      case CLASHSCORE:
        if (!"good".equalsIgnoreCase(structure.rankCategory())) {
          addRemovalReason(
              modelName,
              task,
              String.format(
                  "Clashscore rank category is '%s' (required: 'good')", structure.rankCategory()));
          return false;
        }
        return true; // Only clashscore needs to be good

      case CLASHSCORE_BONDS_ANGLES:
        boolean isValid = true;
        if (!"good".equalsIgnoreCase(structure.rankCategory())) {
          addRemovalReason(
              modelName,
              task,
              String.format(
                  "Clashscore rank category is '%s' (required: 'good')", structure.rankCategory()));
          isValid = false;
        }
        if (!"good".equalsIgnoreCase(structure.badBondsCategory())) {
          addRemovalReason(
              modelName,
              task,
              String.format(
                  "Bad bonds category is '%s' (required: 'good')", structure.badBondsCategory()));
          isValid = false;
        }
        if (!"good".equalsIgnoreCase(structure.badAnglesCategory())) {
          addRemovalReason(
              modelName,
              task,
              String.format(
                  "Bad angles category is '%s' (required: 'good')", structure.badAnglesCategory()));
          isValid = false;
        }
        return isValid;

      default:
        // Should not happen, but default to true (no filtering) if enum changes unexpectedly
        logger.warn("Unknown MolProbityFilter value: {}. Applying no filter.", filter);
        return true;
    }
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

  // Removed validateGoodAndCaution as it's no longer used

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
                  logger.debug("Normal INF score: {}", inf);
                  logger.debug("Calculating interaction network fidelity");
                  var f1 = F1score.calculate(correctConsideredInteractions, modelInteractions);
                  logger.debug("Normal F1 score: {}", f1);
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
      DotBracketFromPdb dotBracket) { // Note: dotBracket might not be needed for VARNA_TZ
    try {
      String svg;
      if (visualizationTool == VisualizationTool.VARNA) {
        logger.info("Generating visualization using DrawerVarnaTz (local VARNA)");
        var svgDoc =
            drawerVarnaTz.drawSecondaryStructure(
                dotBracket,
                firstModel.structure3D(),
                new ArrayList<>(correctConsideredInteractions));
        var svgBytes = SVGHelper.export(svgDoc, Format.SVG);
        svg = new String(svgBytes);
      } else if (visualizationTool == VisualizationTool.VARNA_TZ) {
        logger.info("Generating visualization using VarnaTzClient (remote varna-tz service)");
        var structureData = createStructureData(firstModel, correctConsideredInteractions);
        var svgDoc = varnaTzClient.visualize(structureData);
        var svgBytes = SVGHelper.export(svgDoc, Format.SVG);
        svg = new String(svgBytes);
      } else {
        logger.info("Generating visualization using VisualizationClient (remote adapters service)");
        var visualizationInput =
            visualizationService.prepareVisualizationInput(firstModel, dotBracket);
        var visualizationJson = objectMapper.writeValueAsString(visualizationInput);
        svg = visualizationClient.visualize(visualizationJson, visualizationTool);
      }
      return svg;
    } catch (Exception e) {
      logger.warn("Visualization generation failed for tool: {}", visualizationTool, e);
      throw new RuntimeException(
          "Visualization generation failed for tool " + visualizationTool + ": " + e.getMessage(),
          e);
    }
  }

  private List<AnalyzedBasePair> correctFuzzyNonCanonicalPairs(
      Map<AnalyzedBasePair, Double> fuzzyNonCanonicalPairs) {
    var used = new HashSet<Pair<PdbNamedResidueIdentifier, NucleobaseEdge>>();
    var filtered = new ArrayList<AnalyzedBasePair>();

    // Filter out pairs with probability 0 before conflict resolution
    var sortedSet = sortFuzzySet(fuzzyNonCanonicalPairs);
    var positiveProbabilityPairs =
        sortedSet.stream().filter(pair -> pair.getRight() > 0.0).toList();

    for (var entry : positiveProbabilityPairs) {
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
    // Filter out pairs with probability 0
    return sortFuzzySet(fuzzyStackings).stream()
        .filter(pair -> pair.getRight() > 0.0)
        .map(Pair::getLeft)
        .toList();
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

  private StructureData createStructureData(
      AnalyzedModel model, Set<AnalyzedBasePair> correctInteractions) {
    logger.debug("Creating StructureData for VarnaTzClient");
    var structureData = new StructureData();
    var nucleotides = new ArrayList<Nucleotide>();
    var residueToIdMap = new HashMap<PdbNamedResidueIdentifier, Integer>();
    var idCounter = new AtomicInteger(1); // Start IDs from 1

    // Create Nucleotides and map PdbNamedResidueIdentifier to generated ID
    model
        .residueIdentifiers()
        .forEach(
            residueIdentifier -> {
              var nucleotide = new Nucleotide();
              int currentId = idCounter.getAndIncrement();
              nucleotide.id = currentId;
              nucleotide.number = residueIdentifier.residueNumber();
              nucleotide.character = String.valueOf(residueIdentifier.oneLetterName());
              // Colors are left null for now
              nucleotides.add(nucleotide);
              residueToIdMap.put(residueIdentifier, currentId);
              logger.trace(
                  "Created Nucleotide: id={}, number={}, char={}, mapped from {}",
                  nucleotide.id,
                  nucleotide.number,
                  nucleotide.character,
                  residueIdentifier);
            });
    structureData.nucleotides = nucleotides;
    logger.debug("Generated {} nucleotides", nucleotides.size());

    // Create BasePairs using the mapped IDs
    var basePairs =
        correctInteractions.stream()
            .filter(
                interaction ->
                    interaction.interactionType()
                        == InteractionType
                            .BASE_BASE) // Ensure we only process base pairs for this structure
            .map(
                analyzedPair -> {
                  var varnaBp = new pl.poznan.put.varna.model.BasePair();
                  var bioCommonsPair = analyzedPair.basePair();
                  var lw = analyzedPair.leontisWesthof();

                  Integer id1 = residueToIdMap.get(bioCommonsPair.left());
                  Integer id2 = residueToIdMap.get(bioCommonsPair.right());

                  if (id1 == null || id2 == null) {
                    logger.warn(
                        "Could not find mapping for base pair: {}. Skipping.", analyzedPair);
                    return null; // Skip if mapping not found
                  }

                  varnaBp.id1 = id1;
                  varnaBp.id2 = id2;

                  ModeleBP.Edge edge5 = translateEdge(lw.edge5());
                  ModeleBP.Edge edge3 = translateEdge(lw.edge3());
                  ModeleBP.Stericity stericity = translateStericity(lw.stericity());

                  // Skip if any part is UNKNOWN
                  if (edge5 == ModeleBP.Edge.UNKNOWN
                      || edge3 == ModeleBP.Edge.UNKNOWN
                      || stericity == ModeleBP.Stericity.UNKNOWN) {
                    logger.warn(
                        "Skipping base pair due to UNKNOWN edge or stericity: {}", analyzedPair);
                    return null;
                  }

                  varnaBp.edge5 = edge5;
                  varnaBp.edge3 = edge3;
                  varnaBp.stericity = stericity;
                  varnaBp.canonical = analyzedPair.isCanonical();
                  // Color and thickness are left null

                  logger.trace(
                      "Created Varna BasePair: id1={}, id2={}, edge5={}, edge3={}, stericity={},"
                          + " canonical={}",
                      varnaBp.id1,
                      varnaBp.id2,
                      varnaBp.edge5,
                      varnaBp.edge3,
                      varnaBp.stericity,
                      varnaBp.canonical);
                  return varnaBp;
                })
            .filter(Objects::nonNull) // Remove skipped pairs
            .collect(Collectors.toList());

    structureData.basePairs = basePairs;
    logger.debug("Generated {} base pairs for Varna", basePairs.size());

    return structureData;
  }

  private ModeleBP.Edge translateEdge(final NucleobaseEdge edge) {
    return switch (edge) {
      case WATSON_CRICK -> ModeleBP.Edge.WC;
      case HOOGSTEEN -> ModeleBP.Edge.HOOGSTEEN;
      case SUGAR -> ModeleBP.Edge.SUGAR;
      case UNKNOWN -> ModeleBP.Edge.UNKNOWN; // Or handle as needed
    };
  }

  private ModeleBP.Stericity translateStericity(final Stericity stericity) {
    return switch (stericity) {
      case CIS -> ModeleBP.Stericity.CIS;
      case TRANS -> ModeleBP.Stericity.TRANS;
      case UNKNOWN -> ModeleBP.Stericity.UNKNOWN; // Or handle as needed
    };
  }
}
