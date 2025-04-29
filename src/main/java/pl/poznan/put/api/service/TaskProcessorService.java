package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.orsay.lri.varna.models.rna.ModeleBP;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.mahdilamb.colormap.Colormap;
import net.mahdilamb.colormap.Colormaps;
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
import pl.poznan.put.api.model.*;
import pl.poznan.put.api.model.MolProbityFilter;
import pl.poznan.put.api.model.Task;
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
      Collection<AnalyzedBasePair> nonConflictingInteractions;

      Map<AnalyzedBasePair, Double> fuzzyConsideredInteractions = null;
      if (request.confidenceLevel() == null) {
        logger.info("Computing fuzzy interactions");
        var fuzzyCanonicalPairs =
            computeFuzzyInteractions(canonicalPairsBag, referenceStructure, modelCount);
        var fuzzyNonCanonicalPairs =
            computeFuzzyInteractions(nonCanonicalPairsBag, referenceStructure, modelCount);
        var fuzzyStackings = computeFuzzyInteractions(stackingsBag, referenceStructure, modelCount);
        var fuzzyAllInteractions =
            computeFuzzyInteractions(allInteractionsBag, referenceStructure, modelCount);
        fuzzyConsideredInteractions =
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
        dotBracket = generateFuzzyDotBracket(firstModel, fuzzyCanonicalPairs, canonicalPairsBag);

        logger.info("Compute correct fuzzy interaction (for visualization)");
        nonConflictingInteractions =
            nonConflictingFuzzyInteractions(
                fuzzyCanonicalPairs,
                fuzzyNonCanonicalPairs,
                fuzzyStackings,
                allInteractionsBag // Pass bag
                ); // Pass bag
      } else {
        logger.info("Computing correct interactions");
        int threshold = request.confidenceLevel();
        Set<AnalyzedBasePair> correctConsideredInteractions =
            computeCorrectInteractions(
                request.consensusMode(), consideredInteractionsBag, referenceStructure, threshold);
        nonConflictingInteractions =
            computeCorrectInteractions(
                ConsensusMode.ALL, allInteractionsBag, referenceStructure, threshold);

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

        // Log correct interactions if TRACE is enabled
        if (logger.isTraceEnabled()) {
          logger.trace("Correct Interactions (Threshold: {}):", threshold);
          correctConsideredInteractions.stream()
              .sorted() // AnalyzedBasePair implements Comparable
              .collect(Collectors.groupingBy(AnalyzedBasePair::interactionType))
              .forEach(
                  (type, interactions) -> {
                    logger.trace("  Type: {}", type);
                    interactions.forEach(
                        interaction -> logger.trace("    {}", interaction.toString()));
                  });
        }
      }

      logger.info("Creating task result");
      var taskResult =
          new TaskResult(rankedModels, referenceStructure, dotBracket.toStringWithStrands());
      var resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      logger.info("Generating visualization");
      // Pass context needed for confidence coloring
      var svg =
          generateVisualization(
              request.visualizationTool(),
              firstModel,
              nonConflictingInteractions,
              dotBracket,
              consideredInteractionsBag, // Pass the bag
              modelCount, // Pass the model count
              (request.confidenceLevel() == null)
                  ? fuzzyConsideredInteractions
                  : null // Pass fuzzy map only if in fuzzy mode
              );
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

  /** Internal record to hold intermediate parsing results. */
  private record ParsedModel(String name, String content, PdbModel structure3D) {}

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
                      "Failed to save content of {} to {}",
                      fileData.name(),
                      tempFilePath,
                      ioEx);
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
        boolean isValid = true; // Assume valid unless proven otherwise or analysis fails
        try {
          response = rnalyzerClient.analyzePdbContent(model.content(), model.name());

          // Store the MolProbity response JSON in the task
          try {
            String responseJson = objectMapper.writeValueAsString(response);
            task.addMolProbityResponse(model.name(), responseJson);
          } catch (JsonProcessingException e) {
            logger.error(
                "Failed to serialize MolProbityResponse for model {}", model.name(), e);
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
                String.format(
                    "{\"error\": \"MolProbity analysis failed: %s\"}", e.getMessage()));
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
                logger.debug("Analyzing model: {}", model.name());
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

  private DefaultDotBracketFromPdb generateFuzzyDotBracket(
      AnalyzedModel model,
      Map<AnalyzedBasePair, Double> fuzzyCanonicalPairs,
      HashBag<AnalyzedBasePair> canonicalPairsBag) { // Add bag parameter
    var residues = model.residueIdentifiers();
    // Pass bag to fuzzy correction method
    var canonicalPairs = correctFuzzyCanonicalPairs(fuzzyCanonicalPairs, canonicalPairsBag);
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
      Map<AnalyzedBasePair, Double> fuzzyCanonicalPairs,
      HashBag<AnalyzedBasePair> canonicalPairsBag) { // Add bag parameter

    // Filter out pairs with probability 0 before conflict resolution
    var positiveProbabilityPairs =
        sortFuzzySet(fuzzyCanonicalPairs).stream()
            .filter(pair -> pair.getRight() > 0.0)
            .map(Pair::getKey) // Get the AnalyzedBasePair
            .collect(Collectors.toSet()); // Collect into a Set
    logger.trace(
        "Found {} fuzzy canonical pairs with probability > 0 before conflict resolution",
        positiveProbabilityPairs.size());

    // Resolve conflicts using the helper method
    var resolvedPairs =
        resolveInteractionConflicts(
            positiveProbabilityPairs, canonicalPairsBag, ConsensusMode.CANONICAL);
    logger.trace(
        "{} fuzzy canonical pairs remaining after conflict resolution", resolvedPairs.size());

    return new ArrayList<>(resolvedPairs); // Return as list
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
    logger.debug(
        "Number of interactions after initial filtering (threshold {}, reference): {}",
        threshold,
        correctConsideredInteractions.size());

    // Resolve conflicts using the helper method
    var resolvedInteractions =
        resolveInteractionConflicts(
            correctConsideredInteractions, consideredInteractionsBag, consensusMode);

    logger.info("Finished computing correct interactions");
    return resolvedInteractions; // Return the resolved set
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

  /**
   * Resolves conflicts among a set of interactions based on the consensus mode and interaction
   * counts. For base-base interactions (non-stacking modes), it iteratively removes the lower-count
   * interaction involved in a conflict for each Leontis-Westhof type until no conflicts remain.
   *
   * @param interactions The initial set of interactions to resolve.
   * @param interactionCounts A bag containing the counts (frequency or confidence source) for each
   *     interaction, used for tie-breaking.
   * @param mode The consensus mode, determining if base-base conflict resolution is needed.
   * @return A new set containing the interactions after conflict resolution.
   */
  private Set<AnalyzedBasePair> resolveInteractionConflicts(
      Set<AnalyzedBasePair> interactions,
      HashBag<AnalyzedBasePair> interactionCounts,
      ConsensusMode mode) {
    logger.debug("Resolving conflicts for {} interactions (mode: {})", interactions.size(), mode);
    var resolvedInteractions = new HashSet<>(interactions); // Work on a mutable copy

    if (mode != ConsensusMode.STACKING) {
      logger.debug("Resolving base-base conflicts");
      for (var leontisWesthof : LeontisWesthof.values()) {
        while (true) {
          MultiValuedMap<PdbNamedResidueIdentifier, AnalyzedBasePair> map =
              new ArrayListValuedHashMap<>();
          resolvedInteractions.stream() // Operate on the mutable set
              .filter(candidate -> candidate.interactionType() == InteractionType.BASE_BASE)
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
                  .sorted(
                      Comparator.comparingInt(interactionCounts::getCount)) // Use passed bag count
                  .toList();

          if (conflicting.isEmpty()) {
            break; // No more conflicts for this LeontisWesthof type
          }

          // Remove the lowest-count conflicting pair from the mutable set
          logger.trace(
              "Conflict detected for LW {}. Removing lowest count pair: {}",
              leontisWesthof,
              conflicting.get(0));
          resolvedInteractions.remove(conflicting.get(0));
          // Loop will recalculate conflicts in the next iteration
        }
      }
    }

    logger.debug(
        "Number of interactions after conflict resolution: {}", resolvedInteractions.size());
    return resolvedInteractions;
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
      Collection<AnalyzedBasePair> correctConsideredInteractions) {
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
      Collection<AnalyzedBasePair> correctConsideredInteractions,
      DotBracketFromPdb dotBracket,
      // Context for confidence coloring
      HashBag<AnalyzedBasePair> consideredInteractionsBag,
      int modelCount,
      Map<AnalyzedBasePair, Double> fuzzyConsideredInteractions) {
    try {
      String svg;
      if (visualizationTool == VisualizationTool.VARNA) {
        // Note: Local VARNA visualization does not currently support confidence coloring
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
        var structureData =
            createStructureData(
                firstModel,
                correctConsideredInteractions,
                consideredInteractionsBag,
                modelCount,
                fuzzyConsideredInteractions); // Pass context
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

  private List<AnalyzedBasePair> nonConflictingFuzzyInteractions(
      Map<AnalyzedBasePair, Double> fuzzyCanonicalPairs,
      Map<AnalyzedBasePair, Double> fuzzyNonCanonicalPairs,
      Map<AnalyzedBasePair, Double> fuzzyStackings,
      HashBag<AnalyzedBasePair> allInteractionsBag) {
    // Combine positive-probability canonical and non-canonical pairs
    var positiveBasePairs = new HashSet<AnalyzedBasePair>();
    fuzzyCanonicalPairs.entrySet().stream()
        .filter(entry -> entry.getValue() > 0.0)
        .map(Map.Entry::getKey)
        .forEach(positiveBasePairs::add);
    fuzzyNonCanonicalPairs.entrySet().stream()
        .filter(entry -> entry.getValue() > 0.0)
        .map(Map.Entry::getKey)
        .forEach(positiveBasePairs::add);
    logger.trace(
        "Found {} fuzzy base pairs (canonical + non-canonical) with probability > 0 before"
            + " conflict resolution",
        positiveBasePairs.size());

    // Resolve conflicts for base pairs using the helper method
    var resolvedBasePairs =
        resolveInteractionConflicts(positiveBasePairs, allInteractionsBag, ConsensusMode.ALL);
    logger.trace(
        "{} fuzzy base pairs remaining after conflict resolution", resolvedBasePairs.size());

    // Initialize result with resolved base pairs
    var filtered = new ArrayList<>(resolvedBasePairs);

    // Add stackings with positive probability (no conflict resolution needed for them here)
    var positiveStackings =
        sortFuzzySet(fuzzyStackings).stream()
            .filter(pair -> pair.getRight() > 0.0)
            .map(Pair::getLeft)
            .toList();
    logger.trace("Adding {} fuzzy stackings with probability > 0", positiveStackings.size());
    filtered.addAll(positiveStackings);

    logger.trace("Total {} fuzzy interactions (resolved base pairs + stackings)", filtered.size());
    return filtered;
  }

  private StructureData createStructureData(
      AnalyzedModel model,
      Collection<AnalyzedBasePair> interactionsToVisualize,
      HashBag<AnalyzedBasePair> consideredInteractionsBag,
      int modelCount,
      Map<AnalyzedBasePair, Double> fuzzyConsideredInteractions) {
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
    var basePairs =
        interactionsToVisualize.stream()
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

                  Optional<ModeleBP.Edge> edge5 = translateEdge(lw.edge5());
                  Optional<ModeleBP.Edge> edge3 = translateEdge(lw.edge3());
                  Optional<ModeleBP.Stericity> stericity = translateStericity(lw.stericity());

                  // Skip if any part is UNKNOWN
                  if (edge5.isEmpty() || edge3.isEmpty() || stericity.isEmpty()) {
                    logger.warn(
                        "Skipping base pair due to UNKNOWN edge or stericity: {}", analyzedPair);
                    return null;
                  }

                  varnaBp.edge5 = edge5.get();
                  varnaBp.edge3 = edge3.get();
                  varnaBp.stericity = stericity.get();
                  varnaBp.canonical = analyzedPair.isCanonical();

                  // Calculate confidence and set color
                  double confidence;
                  if (fuzzyConsideredInteractions != null) {
                    // Fuzzy mode: confidence is pre-calculated
                    confidence = fuzzyConsideredInteractions.getOrDefault(analyzedPair, 0.0);
                  } else {
                    // Threshold mode: confidence is frequency
                    confidence =
                        (modelCount > 0)
                            ? (double) consideredInteractionsBag.getCount(analyzedPair) / modelCount
                            : 0.0;
                  }
                  varnaBp.color = getColorForConfidence(confidence);
                  // Thickness is left null

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
            .filter(Objects::nonNull) // Remove skipped pairs
            .collect(Collectors.toList());

    // Sort base pairs by id1, then id2
    basePairs.sort(
        Comparator.comparingInt((pl.poznan.put.varna.model.BasePair bp) -> bp.id1)
            .thenComparingInt(bp -> bp.id2));

    structureData.basePairs = basePairs;
    logger.debug("Generated and sorted {} base pairs for Varna", basePairs.size());

    // Log sorted base pairs at TRACE level
    if (logger.isTraceEnabled()) {
      logger.trace("Sorted Varna Base Pairs:");
      basePairs.forEach(bp -> logger.trace("  {}", bp));
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

  private static final Colormap COLORMAP = Colormaps.get("Algae");

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
}
