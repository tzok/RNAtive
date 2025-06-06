package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.orsay.lri.varna.models.rna.ModeleBP;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.mahdilamb.colormap.Colormap;
import net.mahdilamb.colormap.Colormaps;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.poznan.put.*;
import pl.poznan.put.ConsensusInteraction;
import pl.poznan.put.ConsensusInteraction.InteractionCategory;
import pl.poznan.put.api.dto.ComputeRequest;
import pl.poznan.put.api.dto.FileData;
import pl.poznan.put.api.dto.TaskResult;
import pl.poznan.put.api.exception.TaskNotFoundException;
import pl.poznan.put.api.model.MolProbityFilter;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.model.VisualizationTool;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.util.ReferenceStructureUtil;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.model.BasePair;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.notation.NucleobaseEdge;
import pl.poznan.put.notation.Stericity;
import pl.poznan.put.pdb.*;
import pl.poznan.put.pdb.analysis.*;
import pl.poznan.put.rchie.model.RChieData;
import pl.poznan.put.rchie.model.RChieInteraction;
import pl.poznan.put.rna.InteractionType;
import pl.poznan.put.rnalyzer.MolProbityResponse;
import pl.poznan.put.rnalyzer.RnalyzerClient;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.ImmutableAnalyzedBasePair;
import pl.poznan.put.structure.ImmutableBasePair;
import pl.poznan.put.structure.formats.*;
import pl.poznan.put.utility.svg.Format;
import pl.poznan.put.utility.svg.SVGHelper;
import pl.poznan.put.varna.model.Nucleotide;
import pl.poznan.put.varna.model.StructureData;

@Service
@Transactional
public class TaskProcessorService {
  private static final Logger logger = LoggerFactory.getLogger(TaskProcessorService.class);
  private static final Colormap COLORMAP = Colormaps.get("Algae");
  private static final String RCHIE_REFERENCE_COLOR = "#808080"; // Gray
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final AnalysisClient analysisClient;
  private final VisualizationClient visualizationClient;
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
      RnapolisClient rnapolisClient,
      VarnaTzClient varnaTzClient) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.analysisClient = analysisClient;
    this.visualizationClient = visualizationClient;
    this.visualizationService = visualizationService;
    this.conversionClient = conversionClient;
    this.rnapolisClient = rnapolisClient;
    this.varnaTzClient = varnaTzClient;
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
      logger.info("Unifying format RNApolis unifier service");
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

      logger.info("Collecting and sorting all interactions");
      var fullInteractionResult = collectInteractions(analyzedModels, referenceStructure);
      var aggregatedInteractionResult = fullInteractionResult.aggregatedResult();

      logger.info("Ranking models");
      var rankedModels = generateRankedModels(analyzedModels, fullInteractionResult, request);

      logger.info("Generating dot bracket notation for consensus canonical base pairs");
      var consensusDotBracket =
          generateDotBracket(
              firstModel,
              determineConsensusSet(
                  aggregatedInteractionResult.sortedInteractions,
                  request.confidenceLevel(),
                  ConsensusMode.CANONICAL));

      logger.info("Creating task result");
      var taskResult =
          new TaskResult(
              rankedModels, referenceStructure, consensusDotBracket.toStringWithStrands());
      var resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      logger.info("Generating visualizations for individual models and consensus in parallel");

      // Generate the consensus SVG first (can be done anytime after consensusDotBracket is ready)
      logger.info("Generating visualization for consensus structure");
      var consensusSvg =
          generateVisualization(
              request.visualizationTool(),
              firstModel, // Use first model as template for consensus
              consensusDotBracket, // Use the overall consensus dot-bracket
              determineConsensusSet( // Use aggregated interactions for consensus visualization
                  aggregatedInteractionResult.sortedInteractions(),
                  request.confidenceLevel(),
                  ConsensusMode.ALL));
      logger.debug("Generated SVG for consensus");

      // Prepare RChieData
      RChieData rChieData =
          prepareRChieData(
              firstModel,
              aggregatedInteractionResult.aggregatedResult(),
              referenceStructure);
      // At this point, rChieData is prepared. It can be added to TaskResult or Task entity later.
      logger.info("Prepared RChieData with {} top and {} bottom interactions.",
              rChieData.top().size(), rChieData.bottom().size());

      // Generate model-specific SVGs in parallel and collect them
      logger.info("Generating visualizations for individual models in parallel");
      ConcurrentMap<String, String> modelSvgMap =
          rankedModels.parallelStream()
              .map(
                  rankedModel -> {
                    AnalyzedModel correspondingAnalyzedModel =
                        analyzedModels.stream()
                            .filter(am -> am.name().equals(rankedModel.getName()))
                            .findFirst()
                            .orElse(null); // Should not happen if logic is correct

                    if (correspondingAnalyzedModel != null) {
                      InteractionCollectionResult modelInteractionResult =
                          fullInteractionResult.perModelResults().get(rankedModel.getName());
                      if (modelInteractionResult != null) {
                        Set<ConsensusInteraction> modelInteractionsToVisualize =
                            determineConsensusSet(
                                modelInteractionResult.sortedInteractions(),
                                request.confidenceLevel(),
                                ConsensusMode.ALL); // Use ALL mode for visualization

                        DefaultDotBracketFromPdb modelDotBracket =
                            generateDotBracket(
                                correspondingAnalyzedModel,
                                determineConsensusSet(
                                    modelInteractionResult.sortedInteractions(),
                                    request.confidenceLevel(),
                                    ConsensusMode.CANONICAL));

                        String modelSvg =
                            generateVisualization(
                                request.visualizationTool(),
                                correspondingAnalyzedModel,
                                modelDotBracket,
                                modelInteractionsToVisualize);
                        logger.debug("Generated SVG for model: {}", rankedModel.getName());
                        return Map.entry(rankedModel.getName(), modelSvg);
                      } else {
                        logger.warn(
                            "Could not find interaction results for model {} to generate SVG.",
                            rankedModel.getName());
                      }
                    } else {
                      logger.warn(
                          "Could not find corresponding AnalyzedModel for RankedModel {} to"
                              + " generate SVG.",
                          rankedModel.getName());
                    }
                    return null; // Return null if SVG generation failed for this model
                  })
              .filter(Objects::nonNull) // Filter out entries where SVG generation failed
              .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

      logger.info("Storing all generated SVGs in the task");
      // Store the consensus SVG
      task.addModelSvg("consensus", consensusSvg);
      // Store all model-specific SVGs
      task.getModelSvgs().putAll(modelSvgMap);
      logger.debug("Stored consensus SVG and {} model-specific SVGs", modelSvgMap.size());

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

  /**
   * Internal record to hold the results of interaction collection for a single model or aggregated
   * across models. Holds either the aggregated results or per-model results.
   */
  private record InteractionCollectionResult(
      List<ConsensusInteraction> sortedInteractions, // List of ConsensusInteraction objects
      HashBag<AnalyzedBasePair> canonicalPairsBag,
      HashBag<AnalyzedBasePair> nonCanonicalPairsBag,
      HashBag<AnalyzedBasePair> stackingsBag,
      HashBag<AnalyzedBasePair> allInteractionsBag) {}

  /**
   * Internal record to hold the complete results of interaction collection, including both the
   * aggregated results and the per-model results.
   */
  private record FullInteractionCollectionResult(
      InteractionCollectionResult aggregatedResult,
      Map<String, InteractionCollectionResult> perModelResults) {}

  /**
   * Collects all interactions (canonical, non-canonical, stacking) from the analyzed models,
   * calculates their frequency, determines if they are part of the reference structure, stores
   * per-model results, and returns the aggregated results along with the per-model map.
   *
   * @param analyzedModels The list of models analyzed by a secondary structure tool.
   * @param referenceStructure The parsed reference structure (dot-bracket).
   * @return A {@link FullInteractionCollectionResult} containing the aggregated results (bags and
   *     sorted consensus list) and a map of per-model interaction results (bags only).
   */
  private FullInteractionCollectionResult collectInteractions(
      List<AnalyzedModel> analyzedModels,
      ReferenceStructureUtil.ReferenceParseResult referenceStructure) {
    var combinedCanonicalBag = new HashBag<AnalyzedBasePair>();
    var combinedNonCanonicalBag = new HashBag<AnalyzedBasePair>();
    var combinedStackingBag = new HashBag<AnalyzedBasePair>();
    var combinedAllBag = new HashBag<AnalyzedBasePair>();

    // Step 1: Aggregate bags from all models
    logger.debug("Aggregating interaction bags from {} models", analyzedModels.size());
    for (AnalyzedModel model : analyzedModels) {
      // Temporarily collect bags without creating full per-model results yet
      var modelBags = collectInteractionsForModel(model); // Gets bags only
      combinedCanonicalBag.addAll(modelBags.canonicalPairsBag());
      combinedNonCanonicalBag.addAll(modelBags.nonCanonicalPairsBag());
      combinedStackingBag.addAll(modelBags.stackingsBag());
      combinedAllBag.addAll(modelBags.allInteractionsBag());
    }
    logger.debug(
        "Total unique interactions aggregated across all models: Canonical={}, NonCanonical={},"
            + " Stacking={}, All={}",
        combinedCanonicalBag.uniqueSet().size(),
        combinedNonCanonicalBag.uniqueSet().size(),
        combinedStackingBag.uniqueSet().size(),
        combinedAllBag.uniqueSet().size());

    // Step 2: Create the map of AnalyzedBasePair to ConsensusInteraction from aggregated data
    logger.debug("Creating map of AnalyzedBasePair to ConsensusInteraction from aggregated bags");
    var allConsensusInteractionsMap =
        combinedAllBag.uniqueSet().stream()
            .filter(
                analyzedPair ->
                    analyzedPair.interactionType() == InteractionType.STACKING
                        || analyzedPair.interactionType() == InteractionType.BASE_BASE)
            .collect(
                Collectors.toMap(
                    analyzedPair -> analyzedPair, // Key is the AnalyzedBasePair
                    analyzedPair -> { // Value is the ConsensusInteraction
                      var category =
                          analyzedPair.interactionType() == InteractionType.STACKING
                              ? InteractionCategory.STACKING
                              : InteractionCategory.BASE_PAIR;
                      var lw =
                          (category == InteractionCategory.BASE_PAIR)
                              ? Optional.of(analyzedPair.leontisWesthof())
                              : Optional.<LeontisWesthof>empty();
                      boolean isCanonical = analyzedPair.isCanonical();
                      int count =
                          combinedAllBag.getCount(analyzedPair); // Use combined bag for count
                      boolean presentInRef =
                          referenceStructure.basePairs().contains(analyzedPair.basePair());
                      boolean forbiddenInRef =
                          referenceStructure
                                  .markedResidues()
                                  .contains(analyzedPair.basePair().left())
                              || referenceStructure
                                  .markedResidues()
                                  .contains(analyzedPair.basePair().right());
                      double probability =
                          analyzedModels.isEmpty() ? 0.0 : (double) count / analyzedModels.size();

                      // Ensure partner1 is always "less than" partner2 for consistent sorting
                      var p1 = analyzedPair.basePair().left();
                      var p2 = analyzedPair.basePair().right();
                      if (p1.compareTo(p2) > 0) {
                        var temp = p1;
                        p1 = p2;
                        p2 = temp;
                      }

                      return new ConsensusInteraction(
                          p1,
                          p2,
                          category,
                          lw,
                          isCanonical,
                          count,
                          probability,
                          presentInRef,
                          forbiddenInRef);
                    }));

    logger.debug(
        "Sorting {} total ConsensusInteraction objects for the aggregated result",
        allConsensusInteractionsMap.size());
    var sortedAggregatedInteractions =
        allConsensusInteractionsMap.values().stream() // Get values from the map
            .sorted(
                Comparator.comparing(ConsensusInteraction::category)
                    .thenComparing(ConsensusInteraction::modelCount, Comparator.reverseOrder())
                    .thenComparing(ConsensusInteraction::partner1)
                    .thenComparing(ConsensusInteraction::partner2))
            .toList();

    if (logger.isTraceEnabled()) {
      logger.trace("Sorted Aggregated Consensus Interactions:");
      sortedAggregatedInteractions.forEach(interaction -> logger.trace("  {}", interaction));
    }

    // Step 3: Create the aggregated result object
    var aggregatedResult =
        new InteractionCollectionResult(
            sortedAggregatedInteractions, // Full sorted list
            combinedCanonicalBag,
            combinedNonCanonicalBag,
            combinedStackingBag,
            combinedAllBag);

    // Step 4: Iterate again to create per-model results with ConsensusInteraction lists
    var perModelResults = new HashMap<String, InteractionCollectionResult>();
    logger.debug("Generating per-model results with ConsensusInteraction lists");
    for (AnalyzedModel model : analyzedModels) {
      // Get the bags for this specific model again
      var modelBags = collectInteractionsForModel(model);
      var modelAnalyzedPairs = modelBags.allInteractionsBag().uniqueSet();

      // Look up the corresponding ConsensusInteraction objects from the map
      var modelConsensusInteractions =
          modelAnalyzedPairs.stream()
              .map(allConsensusInteractionsMap::get) // Use the main map
              .filter(Objects::nonNull) // Filter out any potential misses (shouldn't happen)
              .sorted( // Sort using the same comparator as the aggregated list
                  Comparator.comparing(ConsensusInteraction::category)
                      .thenComparing(ConsensusInteraction::modelCount, Comparator.reverseOrder())
                      .thenComparing(ConsensusInteraction::partner1)
                      .thenComparing(ConsensusInteraction::partner2))
              .toList();

      // Create the per-model result
      var modelResult =
          new InteractionCollectionResult(
              modelConsensusInteractions, // Model-specific sorted ConsensusInteraction list
              modelBags.canonicalPairsBag(),
              modelBags.nonCanonicalPairsBag(),
              modelBags.stackingsBag(),
              modelBags.allInteractionsBag());

      perModelResults.put(model.name(), modelResult);

      // Update TRACE logging for the model
      if (logger.isTraceEnabled()) {
        logger.trace("Sorted Consensus Interactions found in model {}:", model.name());
        modelConsensusInteractions.forEach(interaction -> logger.trace("  {}", interaction));
      }
    }

    // Step 5: Return the final result containing aggregated and per-model data
    return new FullInteractionCollectionResult(aggregatedResult, perModelResults);
  }

  /**
   * Collects interaction bags (canonical, non-canonical, stacking, all) for a single analyzed
   * model.
   *
   * @param model The analyzed model.
   * @return An {@link InteractionCollectionResult} containing only the interaction bags for the
   *     given model. The `sortedInteractions` field will be empty.
   */
  private InteractionCollectionResult collectInteractionsForModel(AnalyzedModel model) {
    logger.trace("Collecting interaction bags for model: {}", model.name());

    var canonicalPairsBag =
        model.structure2D().basePairs().stream()
            .filter(BasePair::isCanonical)
            .map(model::basePairToAnalyzed)
            .collect(Collectors.toCollection(HashBag::new));
    logger.trace("Model {}: Found {} canonical base pairs", model.name(), canonicalPairsBag.size());

    var nonCanonicalPairsBag =
        model.structure2D().basePairs().stream()
            .filter(basePair -> !basePair.isCanonical())
            .map(model::basePairToAnalyzed)
            .collect(Collectors.toCollection(HashBag::new));
    logger.trace(
        "Model {}: Found {} non-canonical base pairs", model.name(), nonCanonicalPairsBag.size());

    var stackingsBag =
        model.structure2D().stackings().stream()
            .map(model::stackingToAnalyzed)
            .collect(Collectors.toCollection(HashBag::new));
    logger.trace("Model {}: Found {} stackings", model.name(), stackingsBag.size());

    var allInteractionsBag = new HashBag<>(canonicalPairsBag);
    allInteractionsBag.addAll(nonCanonicalPairsBag);
    allInteractionsBag.addAll(stackingsBag);
    logger.trace(
        "Model {}: Total interactions (all types): {}", model.name(), allInteractionsBag.size());

    // Return only the bags. The sorted list (of ConsensusInteraction) will be populated later.
    return new InteractionCollectionResult(
        Collections.emptyList(), // sortedInteractions is empty at this stage
        canonicalPairsBag,
        nonCanonicalPairsBag,
        stackingsBag,
        allInteractionsBag);
  }

  /**
   * Parses PDB files, filters for RNA, and handles basic parsing errors.
   *
   * @param files The list of FileData objects to parse.
   * @return A list of ParsedModel objects.
   */
  private List<ParsedModel> parsePdbFiles(List<FileData> files) {
    logger.info("Parsing {} PDB files in parallel", files.size());
    return files.parallelStream()
        .map(
            fileData -> {
              try {
                PdbModel structure3D =
                    new PdbParser()
                        .parse(fileData.content()).stream()
                            .findFirst()
                            .map(model -> model.filteredNewInstance(MoleculeType.RNA))
                            .orElseThrow(
                                () ->
                                    new RuntimeException(
                                        "No RNA structure found in file " + fileData.name()));
                return new ParsedModel(fileData.name(), fileData.content(), structure3D);
              } catch (Exception e) {
                // Log and save problematic file content for debugging
                String errorFileName = "error-" + fileData.name();
                Path tempFilePath = Paths.get("/tmp", errorFileName);
                try {
                  logger.warn(
                      "Failed to parse file {}, saving content to {}. Error: {}",
                      fileData.name(),
                      tempFilePath,
                      e.getMessage());
                  Files.writeString(
                      tempFilePath,
                      fileData.content(),
                      StandardOpenOption.CREATE,
                      StandardOpenOption.WRITE,
                      StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException ioEx) {
                  logger.error(
                      "Failed to save content of {} to {}", fileData.name(), tempFilePath, ioEx);
                }
                // Propagate the original parsing exception
                throw new RuntimeException(
                    "Failed to parse file " + fileData.name() + ": " + e.getMessage(), e);
              }
            })
        .toList();
  }

  /**
   * Checks for consistency in nucleotide composition and identifiers across models. Attempts to
   * unify models if sequences match but identifiers differ.
   *
   * @param models The list of parsed models.
   * @return The list of models, potentially unified.
   * @throws RuntimeException if models have fundamentally different sequences or unification fails.
   */
  private List<ParsedModel> unifyModelsIfNeeded(List<ParsedModel> models) {
    logger.info("Checking nucleotide composition consistency for {} models", models.size());
    var identifiersToModels =
        models.stream()
            .collect(Collectors.groupingBy(model -> model.structure3D.namedResidueIdentifiers()));

    if (identifiersToModels.size() <= 1) {
      logger.info("All models have consistent nucleotide composition and identifiers.");
      return models; // No unification needed
    }

    logger.warn(
        "Models have inconsistent identifiers ({} groups found). Checking sequences.",
        identifiersToModels.size());

    // Check if sequences are identical despite identifier differences
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

    if (sequences.size() > 1) {
      logger.error("Models have fundamentally different sequences. Cannot proceed.");
      throw new RuntimeException(formatNucleotideCompositionError(identifiersToModels));
    }

    logger.info(
        "Sequences match despite identifier differences. Attempting model unification (renaming,"
            + " renumbering, forcing MODRES).");

    // Find indices of modified residues across all models
    var modifiedResidueIndices =
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
    logger.debug("Indices of modified residues found across models: {}", modifiedResidueIndices);

    // Regenerate models with a unified scheme (Chain A, 1-based numbering, no icodes, forced
    // MODRES)
    var unifiedModels =
        models.stream()
            .map(
                model -> {
                  logger.trace("Unifying model: {}", model.name());
                  var originalResidues = model.structure3D.residues();
                  int residueCount = originalResidues.size();

                  // Create mapping from old identifier to new unified identifier
                  var mapping =
                      IntStream.range(0, residueCount)
                          .boxed()
                          .collect(
                              Collectors.toMap(
                                  i -> originalResidues.get(i).identifier(),
                                  i ->
                                      ImmutablePdbResidueIdentifier.of(
                                          "A", i + 1, Optional.empty())));

                  // Generate MODRES lines for residues identified as modified in *any* model
                  var modresLines =
                      IntStream.range(0, residueCount)
                          .boxed()
                          .filter(modifiedResidueIndices::contains)
                          .map(
                              i -> {
                                var name = originalResidues.get(i).standardResidueName();
                                return ImmutablePdbModresLine.of(
                                    "", name, "A", i + 1, Optional.empty(), name.toLowerCase(), "");
                              })
                          .toList();

                  // Regenerate atom lines with new chain and residue numbers
                  var atoms =
                      model.structure3D.atoms().stream()
                          .map(
                              atom -> {
                                var identifier = PdbResidueIdentifier.from(atom);
                                var mapped =
                                    mapping.get(identifier); // Should always exist if parsing
                                // succeeded
                                if (mapped == null) {
                                  // This indicates an internal logic error
                                  throw new IllegalStateException(
                                      "Internal error: No mapping found for residue "
                                          + identifier
                                          + " during unification of model "
                                          + model.name());
                                }
                                return (PdbAtomLine)
                                    ImmutablePdbAtomLine.copyOf(atom)
                                        .withChainIdentifier(mapped.chainIdentifier())
                                        .withResidueNumber(mapped.residueNumber())
                                        .withInsertionCode(Optional.empty());
                              })
                          .toList();

                  // Rebuild the PdbModel with unified data
                  var regenerated =
                      ImmutableDefaultPdbModel.of(
                          ImmutablePdbHeaderLine.of("", new Date(0L), ""),
                          ImmutablePdbExpdtaLine.of(Collections.emptyList()),
                          ImmutablePdbRemark2Line.of(Double.NaN),
                          1, // Assuming single model PDBs after RNApolis splitting
                          atoms,
                          modresLines,
                          Collections.emptyList(), // Assuming no TER cards needed for RNA
                          "", // Original PDB content is lost here, using regenerated
                          Collections.emptyList()); // Assuming no CONECT records needed

                  logger.trace("Finished unifying model: {}", model.name());
                  // Return a new ParsedModel with the regenerated structure and its PDB string
                  return new ParsedModel(model.name, regenerated.toPdb(), regenerated);
                })
            .toList();

    // Final check after unification
    var finalIdentifiersToModels =
        unifiedModels.stream()
            .collect(Collectors.groupingBy(model -> model.structure3D.namedResidueIdentifiers()));
    if (finalIdentifiersToModels.size() > 1) {
      // This should ideally not happen if sequences matched
      logger.error("Model unification failed. Inconsistent identifiers remain.");
      throw new RuntimeException(formatNucleotideCompositionError(finalIdentifiersToModels));
    }

    logger.info("Model unification successful.");
    return unifiedModels;
  }

  /**
   * Filters models based on MolProbity analysis results.
   *
   * @param models The list of models to filter.
   * @param filter The MolProbity filter level.
   * @param task The current task, used to store removal reasons and responses.
   * @return A list of models that passed the filter.
   */
  private List<ParsedModel> filterModelsWithMolProbity(
      List<ParsedModel> models, MolProbityFilter filter, Task task) {
    if (filter == MolProbityFilter.ALL) {
      logger.info("MolProbity filtering is set to ALL, skipping filtering.");
      return new ArrayList<>(models); // Return a mutable copy
    }

    logger.info("Attempting MolProbity filtering with level: {}", filter);
    var validModels = new ArrayList<ParsedModel>(); // Use mutable list

    try (var rnalyzerClient = new RnalyzerClient()) {
      rnalyzerClient.initializeSession();

      for (ParsedModel model : models) {
        MolProbityResponse response = null;
        boolean isValid; // Assume valid unless proven otherwise or analysis fails
        try {
          response = rnalyzerClient.analyzePdbContent(model.content(), model.name());

          // Store the MolProbity response JSON in the task
          try {
            String responseJson = objectMapper.writeValueAsString(response);
            task.addMolProbityResponse(model.name(), responseJson);
          } catch (JsonProcessingException e) {
            logger.error("Failed to serialize MolProbityResponse for model {}", model.name(), e);
            task.addMolProbityResponse(model.name(), "{\"error\": \"Serialization failed\"}");
          }

          // Check validity using the obtained response
          isValid = isModelValid(model.name(), response.structure(), filter, task);

        } catch (Exception e) {
          logger.warn(
              "MolProbity analysis failed for model {}: {}. Model will be included by default.",
              model.name(),
              e.getMessage());
          // Store an error indication if analysis failed
          if (response == null) { // Only store error if we didn't get a response to serialize
            task.addMolProbityResponse(
                model.name(),
                String.format("{\"error\": \"MolProbity analysis failed: %s\"}", e.getMessage()));
          }
          isValid = true; // Include model if analysis fails
        }

        if (isValid) {
          validModels.add(model);
        }
        // Removal reason is added within isModelValid if !isValid
      }

      logger.info(
          "MolProbity filtering completed. {} models passed out of {}.",
          validModels.size(),
          models.size());
      return validModels;

    } catch (Exception e) {
      logger.warn(
          "MolProbity filtering failed due to an error initializing or using the RNAlyzer service:"
              + " {}. Proceeding without MolProbity filtering.",
          e.getMessage());
      // If the RNAlyzer service fails entirely, return all original models
      return new ArrayList<>(models); // Return a mutable copy
    }
  }

  /**
   * Analyzes the secondary structure (2D) of the given models using the specified analyzer.
   *
   * @param models The list of models (3D structures) to analyze.
   * @param analyzer The secondary structure analysis tool to use.
   * @return A list of AnalyzedModel objects containing both 3D and 2D information.
   */
  private List<AnalyzedModel> analyzeSecondaryStructures(
      List<ParsedModel> models, pl.poznan.put.Analyzer analyzer) {
    logger.info(
        "Analyzing secondary structures for {} models using {}", models.size(), analyzer.name());
    return models.parallelStream()
        .map(
            model -> {
              try {
                var jsonResult = analysisClient.analyze(model.name, model.content, analyzer);
                var structure2D = objectMapper.readValue(jsonResult, BaseInteractions.class);
                logger.debug("Successfully analyzed model: {}", model.name());
                return new AnalyzedModel(model.name, model.structure3D, structure2D);
              } catch (JsonProcessingException e) {
                logger.error(
                    "Failed to parse analysis result for file: {}. Error: {}",
                    model.name(),
                    e.getMessage());
                throw new RuntimeException(
                    "Failed to parse analysis result for file: " + model.name(), e);
              } catch (Exception e) {
                logger.error(
                    "Analysis failed for model: {}. Error: {}", model.name(), e.getMessage());
                throw new RuntimeException("Analysis failed for model: " + model.name(), e);
              }
            })
        .toList();
  }

  /**
   * Orchestrates the parsing, validation, filtering, and analysis of input files.
   *
   * @param request The compute request containing files and parameters.
   * @param task The task entity to update with progress and results.
   * @return A list of fully analyzed models.
   */
  private List<AnalyzedModel> parseAndAnalyzeFiles(ComputeRequest request, Task task) {
    // 1. Parse PDB files into 3D structures
    List<ParsedModel> parsedModels = parsePdbFiles(request.files());

    // 2. Check consistency and unify models if needed
    List<ParsedModel> consistentModels = unifyModelsIfNeeded(parsedModels);

    // 3. Filter models using MolProbity
    List<ParsedModel> validModels =
        filterModelsWithMolProbity(consistentModels, request.molProbityFilter(), task);

    // 4. Analyze secondary structures for valid models
    List<AnalyzedModel> analyzedModels =
        analyzeSecondaryStructures(validModels, request.analyzer());

    logger.info(
        "Finished parsing and analysis. Resulting in {} analyzed models.", analyzedModels.size());
    return analyzedModels;
  }

  private DefaultDotBracketFromPdb generateDotBracket(
      AnalyzedModel model, Set<ConsensusInteraction> canonicalInteractions) {
    var residues = model.residueIdentifiers();
    var canonicalPairs =
        canonicalInteractions.stream()
            .map(
                ci ->
                    ImmutableAnalyzedBasePair.of(
                        ImmutableBasePair.of(ci.partner1(), ci.partner2())))
            .distinct()
            .toList();
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

  /**
   * Resolves conflicts among a set of interactions based on the consensus mode and interaction
   * counts. For base-base interactions (non-stacking modes), it iteratively removes the lower-count
   * interaction involved in a conflict for each Leontis-Westhof type until no conflicts remain.
   *
   * @param interactions The initial collection of consensus interactions to resolve.
   * @param confidenceLevel The confidence level threshold (null for fuzzy mode), used to determine
   *     comparison logic.
   * @param mode The consensus mode, determining if base-base conflict resolution is needed.
   * @return A new set containing the consensus interactions after conflict resolution.
   */
  private Set<ConsensusInteraction> resolveInteractionConflicts(
      Collection<ConsensusInteraction> interactions, Integer confidenceLevel, ConsensusMode mode) {
    logger.debug(
        "Resolving conflicts for {} interactions (mode: {}, confidenceLevel: {})",
        interactions.size(),
        mode,
        confidenceLevel == null ? "fuzzy" : confidenceLevel);
    var resolvedInteractions = new HashSet<>(interactions); // Work on a mutable copy

    // Only resolve conflicts for base-base interactions if mode is not STACKING
    if (mode != ConsensusMode.STACKING) {
      logger.debug("Resolving base-base conflicts");

      // Define the comparator
      Comparator<ConsensusInteraction> conflictComparator =
          Comparator.comparing(ConsensusInteraction::modelCount) // Threshold: Higher count wins
              .thenComparing(ConsensusInteraction::partner1) // Tie-breaker 1
              .thenComparing(ConsensusInteraction::partner2); // Tie-breaker 2

      for (var leontisWesthof : LeontisWesthof.values()) {
        while (true) {
          MultiValuedMap<PdbNamedResidueIdentifier, ConsensusInteraction> map =
              new ArrayListValuedHashMap<>();
          resolvedInteractions.stream() // Operate on the mutable set
              .filter(
                  candidate ->
                      candidate.category() == ConsensusInteraction.InteractionCategory.BASE_PAIR)
              .filter(
                  candidate ->
                      candidate.leontisWesthof().isPresent()
                          && candidate.leontisWesthof().get() == leontisWesthof)
              .forEach(
                  candidate -> {
                    map.put(candidate.partner1(), candidate);
                    map.put(candidate.partner2(), candidate);
                  });

          var conflicting =
              map.keySet().stream()
                  .filter(
                      key -> map.get(key).size() > 1) // Find residues involved in >1 interaction
                  .flatMap(
                      key -> map.get(key).stream()) // Get all interactions involving these residues
                  .distinct() // Unique interactions
                  .sorted(conflictComparator) // Sort by count/probability (lowest first)
                  .toList();

          if (conflicting.isEmpty()) {
            break; // No more conflicts for this LeontisWesthof type
          }

          // Remove the interaction with the lowest count/probability involved in the conflict
          ConsensusInteraction interactionToRemove = conflicting.get(0);
          logger.trace(
              "Conflict detected for LW {}. Removing lowest priority interaction: {}",
              leontisWesthof,
              interactionToRemove);
          resolvedInteractions.remove(interactionToRemove);
          // Loop will recalculate conflicts in the next iteration with the updated set
        }
      }
    }

    logger.debug(
        "Number of interactions after conflict resolution: {}", resolvedInteractions.size());
    return resolvedInteractions; // Return the set of non-conflicting interactions
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

  /**
   * Helper method to convert a Set of ConsensusInteraction (representing the fuzzy consensus) to a
   * Map suitable for fuzzy scoring functions.
   */
  private Map<ConsensusInteraction, Double> consensusSetToFuzzyMap(
      Set<ConsensusInteraction> consensusSet) {
    return consensusSet.stream()
        .collect(Collectors.toMap(Function.identity(), ConsensusInteraction::probability));
  }

  /**
   * Determines the final set of consensus interactions based on the specified mode (ALL, CANONICAL,
   * etc.), analysis type (fuzzy/threshold), and reference structure.
   *
   * @param allConsensusInteractions The full list of aggregated ConsensusInteraction objects.
   * @param confidenceLevel The confidence threshold (null for fuzzy mode).
   * @param consensusMode The type of interactions to consider (ALL, CANONICAL, etc.).
   * @return A set of ConsensusInteraction objects representing the final consensus.
   */
  private Set<ConsensusInteraction> determineConsensusSet(
      List<ConsensusInteraction> allConsensusInteractions,
      Integer confidenceLevel,
      ConsensusMode consensusMode) {
    logger.info(
        "Determining consensus set for mode: {}, confidenceLevel: {}",
        consensusMode,
        confidenceLevel == null ? "fuzzy" : confidenceLevel);

    // 1. Filter by interaction category (based on consensusMode) and basic validity
    // (isInteractionConsidered)
    var filteredInteractions =
        allConsensusInteractions.stream()
            .filter(
                interaction -> {
                  // Check if the interaction category matches the requested mode
                  boolean categoryMatch =
                      switch (consensusMode) {
                        case CANONICAL -> interaction.category()
                                == ConsensusInteraction.InteractionCategory.BASE_PAIR
                            && interaction.isCanonical();
                        case NON_CANONICAL -> interaction.category()
                                == ConsensusInteraction.InteractionCategory.BASE_PAIR
                            && !interaction.isCanonical();
                        case STACKING -> interaction.category()
                            == ConsensusInteraction.InteractionCategory.STACKING;
                        case ALL -> true; // Include all categories
                      };
                  // Also check if it passes the basic fuzzy/threshold/reference filter
                  return categoryMatch && isInteractionConsidered(interaction, confidenceLevel);
                })
            .toList(); // Collect to list first for logging/debugging if needed

    logger.debug(
        "Found {} interactions matching mode {} and passing initial filters.",
        filteredInteractions.size(),
        consensusMode);

    // 2. Resolve conflicts within the filtered set
    var consensusSet =
        resolveInteractionConflicts(filteredInteractions, confidenceLevel, consensusMode);

    logger.info(
        "Determined final consensus set with {} interactions for mode {}",
        consensusSet.size(),
        consensusMode);
    return consensusSet;
  }

  /**
   * Generates ranked models based on the comparison against a determined consensus set. Handles
   * both threshold and fuzzy modes.
   *
   * @param analyzedModels The list of models to rank.
   * @param fullInteractionResult The complete interaction results (aggregated and per-model).
   * @param request The original compute request containing parameters.
   * @return A list of RankedModel objects, sorted by rank.
   */
  private List<RankedModel> generateRankedModels(
      List<AnalyzedModel> analyzedModels,
      FullInteractionCollectionResult fullInteractionResult,
      ComputeRequest request) {
    logger.info("Starting generation of ranked models (unified logic)");
    Integer confidenceLevel = request.confidenceLevel();
    ConsensusMode consensusMode = request.consensusMode();

    // 1. Determine the target consensus set based on the request's mode and consensus type
    Set<ConsensusInteraction> targetConsensusSet =
        determineConsensusSet(
            fullInteractionResult.aggregatedResult().sortedInteractions(),
            confidenceLevel,
            consensusMode);
    logger.debug(
        "Prepared threshold target set with {} entries for scoring", targetConsensusSet.size());

    // 2. Iterate through models, calculate scores, and generate dot-brackets
    var rankedModelsList = new ArrayList<RankedModel>();
    for (AnalyzedModel model : analyzedModels) {
      logger.debug("Processing model for ranking: {}", model.name());
      List<ConsensusInteraction> modelConsensusInteractions =
          fullInteractionResult.perModelResults().get(model.name()).sortedInteractions();
      Set<ConsensusInteraction> modelConsensusSet =
          determineConsensusSet(modelConsensusInteractions, confidenceLevel, consensusMode);

      // Calculate INF and F1 scores based on mode
      double inf;
      double f1;
      if (confidenceLevel == null) { // Fuzzy mode
        logger.debug("Calculating fuzzy INF and F1 scores for model {}", model.name());
        Map<ConsensusInteraction, Double> targetConsensusMap =
            consensusSetToFuzzyMap(targetConsensusSet);
        inf = InteractionNetworkFidelity.calculateFuzzy(targetConsensusMap, modelConsensusSet);
        f1 = F1score.calculateFuzzy(targetConsensusMap, modelConsensusSet);
        logger.debug("Model {}: Fuzzy INF = {}, Fuzzy F1 = {}", model.name(), inf, f1);
      } else { // Threshold mode
        logger.debug("Calculating threshold INF and F1 scores for model {}", model.name());
        inf = InteractionNetworkFidelity.calculate(targetConsensusSet, modelConsensusSet);
        f1 = F1score.calculate(targetConsensusSet, modelConsensusSet);
        logger.debug("Model {}: Threshold INF = {}, Threshold F1 = {}", model.name(), inf, f1);
      }

      // Generate dot-bracket for the model
      logger.debug("Generating dot-bracket for model {}", model.name());
      DefaultDotBracketFromPdb dotBracket =
          generateDotBracket(
              model,
              determineConsensusSet(
                  modelConsensusInteractions, confidenceLevel, ConsensusMode.CANONICAL));

      rankedModelsList.add(new RankedModel(model, inf, f1, dotBracket));
    }

    // 3. Rank the models based on Interaction Network Fidelity (INF)
    logger.info("Ranking {} models based on Interaction Network Fidelity", rankedModelsList.size());
    final var infs = // Use final for lambda capture clarity
        rankedModelsList.stream()
            .map(RankedModel::getInteractionNetworkFidelity)
            .sorted(Comparator.reverseOrder())
            .toList();
    rankedModelsList.forEach(
        rankedModel -> {
          int rank = infs.indexOf(rankedModel.getInteractionNetworkFidelity()) + 1;
          rankedModel.setRank(rank);
          logger.trace("Model {} assigned rank {}", rankedModel.getName(), rank);
        });
    rankedModelsList.sort(Comparator.comparingInt(RankedModel::getRank)); // Sort by rank

    logger.info("Finished generating ranked models");
    return rankedModelsList;
  }

  private String generateVisualization(
      VisualizationTool visualizationTool,
      AnalyzedModel model,
      DotBracketFromPdb dotBracket,
      Set<ConsensusInteraction> interactionsToVisualize) {
    try {
      String svg;
      if (visualizationTool == VisualizationTool.VARNA) {
        logger.info("Generating visualization using VarnaTzClient (remote varna-tz service)");
        var structureData = createStructureData(model, interactionsToVisualize);
        var svgDoc = varnaTzClient.visualize(structureData);
        var svgBytes = SVGHelper.export(svgDoc, Format.SVG);
        svg = new String(svgBytes);
      } else {
        logger.info("Generating visualization using VisualizationClient (remote adapters service)");
        var visualizationInput = visualizationService.prepareVisualizationInput(model, dotBracket);
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

  private StructureData createStructureData(
      AnalyzedModel model, Set<ConsensusInteraction> interactionsToVisualize) {
    logger.debug("Creating StructureData for VarnaTzClient with confidence coloring");
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

    // Log considered base pairs at TRACE level
    if (logger.isTraceEnabled()) {
      logger.trace("Considered Base Pairs:");
      interactionsToVisualize.forEach(bp -> logger.trace("  {}", bp));
    }

    // Create BasePairs using the mapped IDs and calculate confidence color
    // Remove skipped pairs
    var basePairs =
        interactionsToVisualize.stream()
            .filter(
                interaction ->
                    interaction.category()
                        == InteractionCategory
                            .BASE_PAIR) // Ensure we only process base pairs for this structure
            .filter(interaction -> interaction.leontisWesthof().isPresent())
            .map(
                interaction -> {
                  var varnaBp = new pl.poznan.put.varna.model.BasePair();
                  var lw = interaction.leontisWesthof();

                  Integer id1 = residueToIdMap.get(interaction.partner1());
                  Integer id2 = residueToIdMap.get(interaction.partner2());

                  if (id1 == null || id2 == null) {
                    logger.warn("Could not find mapping for base pair: {}. Skipping.", interaction);
                    return null; // Skip if mapping not found
                  }

                  varnaBp.id1 = id1;
                  varnaBp.id2 = id2;

                  Optional<ModeleBP.Edge> edge5 = translateEdge(lw.get().edge5());
                  Optional<ModeleBP.Edge> edge3 = translateEdge(lw.get().edge3());
                  Optional<ModeleBP.Stericity> stericity = translateStericity(lw.get().stericity());

                  // Skip if any part is UNKNOWN
                  if (edge5.isEmpty() || edge3.isEmpty() || stericity.isEmpty()) {
                    logger.warn(
                        "Skipping base pair due to UNKNOWN edge or stericity: {}", interaction);
                    return null;
                  }

                  varnaBp.edge5 = edge5.get();
                  varnaBp.edge3 = edge3.get();
                  varnaBp.stericity = stericity.get();
                  varnaBp.canonical = interaction.isCanonical();

                  // Calculate confidence and set color
                  double confidence = interaction.probability();
                  varnaBp.color = getColorForConfidence(confidence);

                  logger.trace(
                      "Created Varna BasePair: id1={}, id2={}, edge5={}, edge3={}, stericity={},"
                          + " canonical={}, confidence={}, color={}",
                      varnaBp.id1,
                      varnaBp.id2,
                      varnaBp.edge5,
                      varnaBp.edge3,
                      varnaBp.stericity,
                      varnaBp.canonical,
                      confidence,
                      varnaBp.color);
                  return varnaBp;
                })
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparingInt((pl.poznan.put.varna.model.BasePair bp) -> bp.id1)
                    .thenComparingInt(bp -> bp.id2))
            .collect(Collectors.toList());

    structureData.basePairs = basePairs;
    logger.debug("Generated and sorted {} base pairs for Varna", basePairs.size());

    // Log sorted base pairs at TRACE level
    if (logger.isTraceEnabled()) {
      logger.trace("Sorted Varna Base Pairs:");
      basePairs.forEach(bp -> logger.trace("  {}", bp));
    }

    // Create Stackings using the mapped IDs and calculate confidence color
    var stackings =
        interactionsToVisualize.stream()
            .filter(
                interaction ->
                    interaction.category() == InteractionCategory.STACKING) // Filter for stackings
            .map(
                interaction -> {
                  var varnaStacking = new pl.poznan.put.varna.model.Stacking();

                  Integer id1 = residueToIdMap.get(interaction.partner1());
                  Integer id2 = residueToIdMap.get(interaction.partner2());

                  if (id1 == null || id2 == null) {
                    logger.warn(
                        "Could not find mapping for stacking interaction: {}. Skipping.",
                        interaction);
                    return null; // Skip if mapping not found
                  }

                  varnaStacking.id1 = String.valueOf(id1); // VARNA expects string IDs for stackings
                  varnaStacking.id2 = String.valueOf(id2);

                  // Calculate confidence and set color
                  double confidence = interaction.probability();
                  varnaStacking.color = getColorForConfidence(confidence);
                  // Thickness can be set if needed, e.g., based on confidence or a fixed value
                  // varnaStacking.thickness = calculateThicknessForConfidence(confidence);

                  logger.trace(
                      "Created Varna Stacking: id1={}, id2={}, confidence={}, color={}",
                      varnaStacking.id1,
                      varnaStacking.id2,
                      confidence,
                      varnaStacking.color);
                  return varnaStacking;
                })
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparing(
                        (pl.poznan.put.varna.model.Stacking s) -> Integer.parseInt(s.id1))
                    .thenComparing(s -> Integer.parseInt(s.id2)))
            .collect(Collectors.toList());

    structureData.stackings = stackings;
    logger.debug("Generated and sorted {} stackings for Varna", stackings.size());

    // Log sorted stackings at TRACE level
    if (logger.isTraceEnabled()) {
      logger.trace("Sorted Varna Stackings:");
      stackings.forEach(s -> logger.trace("  {}", s));
    }

    return structureData;
  }

  private Optional<ModeleBP.Edge> translateEdge(final NucleobaseEdge edge) {
    return switch (edge) {
      case WATSON_CRICK -> Optional.of(ModeleBP.Edge.WC);
      case HOOGSTEEN -> Optional.of(ModeleBP.Edge.HOOGSTEEN);
      case SUGAR -> Optional.of(ModeleBP.Edge.SUGAR);
      case UNKNOWN -> Optional.empty();
    };
  }

  private Optional<ModeleBP.Stericity> translateStericity(final Stericity stericity) {
    return switch (stericity) {
      case CIS -> Optional.of(ModeleBP.Stericity.CIS);
      case TRANS -> Optional.of(ModeleBP.Stericity.TRANS);
      case UNKNOWN -> Optional.empty();
    };
  }

  /**
   * Generates a hex color string (#RRGGBB) based on a confidence score (0.0 to 1.0) using the Blues
   * colormap.
   *
   * @param confidence The confidence score (0.0 to 1.0).
   * @return A hex color string.
   */
  private String getColorForConfidence(double confidence) {
    // Clamp confidence to the range [0.0, 1.0] as expected by the colormap
    double clampedConfidence = Math.max(0.0, Math.min(1.0, confidence));

    // Get the color from the colormap
    Color color = COLORMAP.get(clampedConfidence);

    // Format the color as a hex string #RRGGBB
    return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
  }

  /** Internal record to hold intermediate parsing results. */
  private record ParsedModel(String name, String content, PdbModel structure3D) {}

  /**
   * Determines if a consensus interaction should be considered based on the analysis mode (fuzzy or
   * threshold) and reference structure constraints.
   *
   * @param interaction The consensus interaction to check.
   * @param confidenceLevel The confidence level threshold (null for fuzzy mode).
   * @return True if the interaction should be considered, false otherwise.
   */
  private boolean isInteractionConsidered(
      ConsensusInteraction interaction, Integer confidenceLevel) {
    // Never consider interactions involving residues marked as unpaired in the reference
    if (interaction.forbiddenInReference()) {
      return false;
    }

    if (confidenceLevel != null) {
      // Threshold Mode: Consider if present in reference OR meets the threshold count
      return interaction.presentInReference() || interaction.modelCount() >= confidenceLevel;
    } else {
      // Fuzzy Mode: Consider if present in reference (implicit probability 1.0) or has positive
      // probability
      // Note: presentInReference implies probability 1.0 in fuzzy calculation,
      // so checking probability > 0 covers both cases.
      return interaction.probability() > 0.0;
    }
  }

  private RChieData prepareRChieData(
      AnalyzedModel firstModel,
      InteractionCollectionResult aggregatedInteractionResult,
      ReferenceStructureUtil.ReferenceParseResult referenceStructure) {
    logger.info("Preparing RChieData");

    List<PdbNamedResidueIdentifier> modelResidues = firstModel.residueIdentifiers();
    Map<PdbNamedResidueIdentifier, Integer> residueToIndexMap = new HashMap<>();
    for (int i = 0; i < modelResidues.size(); i++) {
      residueToIndexMap.put(modelResidues.get(i), i + 1);
    }

    String sequence =
        modelResidues.stream()
            .map(PdbNamedResidueIdentifier::oneLetterName)
            .map(String::valueOf)
            .collect(Collectors.joining());

    // Prepare top interactions (aggregated consensus canonical)
    List<RChieInteraction> topInteractions =
        aggregatedInteractionResult.sortedInteractions().stream()
            .filter(
                ci ->
                    ci.category() == ConsensusInteraction.InteractionCategory.BASE_PAIR
                        && ci.isCanonical())
            .map(
                ci -> {
                  Integer index1 = residueToIndexMap.get(ci.partner1());
                  Integer index2 = residueToIndexMap.get(ci.partner2());
                  if (index1 == null || index2 == null) {
                    logger.warn(
                        "Could not map residues of consensus interaction {} to indices. Skipping.",
                        ci);
                    return null;
                  }
                  // Ensure i < j for RChieInteraction if needed, though ConsensusInteraction
                  // already sorts partners.
                  int rchieI = Math.min(index1, index2);
                  int rchieJ = Math.max(index1, index2);
                  return new RChieInteraction(
                      rchieI, rchieJ, Optional.of(getColorForConfidence(ci.probability())));
                })
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparingInt(RChieInteraction::i).thenComparingInt(RChieInteraction::j))
            .toList();
    logger.debug("Prepared {} top interactions for RChieData", topInteractions.size());

    // Prepare bottom interactions (reference structure canonical)
    List<RChieInteraction> bottomInteractions = new ArrayList<>();
    if (referenceStructure != null && referenceStructure.basePairs() != null) {
      bottomInteractions =
          referenceStructure.basePairs().stream()
              .filter(BasePair::isCanonical)
              .map(
                  bp -> {
                    try {
                      PdbNamedResidueIdentifier refNt1 =
                          firstModel.residueToNamedIdentifier(bp.nt1());
                      PdbNamedResidueIdentifier refNt2 =
                          firstModel.residueToNamedIdentifier(bp.nt2());

                      Integer index1 = residueToIndexMap.get(refNt1);
                      Integer index2 = residueToIndexMap.get(refNt2);

                      if (index1 == null || index2 == null) {
                        logger.warn(
                            "Could not map residues of reference base pair {} to indices."
                                + " Skipping.",
                            bp);
                        return null;
                      }
                      // Ensure i < j
                      int rchieI = Math.min(index1, index2);
                      int rchieJ = Math.max(index1, index2);
                      return new RChieInteraction(
                          rchieI, rchieJ, Optional.of(RCHIE_REFERENCE_COLOR));
                    } catch (Exception e) {
                      logger.warn(
                          "Error processing reference base pair {} for RChieData: {}. Skipping.",
                          bp,
                          e.getMessage());
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .sorted(
                  Comparator.comparingInt(RChieInteraction::i)
                      .thenComparingInt(RChieInteraction::j))
              .toList();
    }
    logger.debug("Prepared {} bottom interactions for RChieData", bottomInteractions.size());

    return new RChieData(
        sequence, Optional.ofNullable(firstModel.name()), topInteractions, bottomInteractions);
  }
}
