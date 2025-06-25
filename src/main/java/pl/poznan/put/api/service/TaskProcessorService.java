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
import pl.poznan.put.structure.ClassifiedBasePair;
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
  private static final String FORBIDDEN_INTERACTION_COLOR = "#FF0000"; // Red

  /**
   * Determines if a base pair is canonical by checking if it has cWW Leontis-Westhof classification
   * and the nucleotides form one of the canonical pairs: CG, AU, or GU.
   *
   * @param basePair The base pair to check
   * @return true if the base pair is canonical, false otherwise
   */
  private static boolean isCanonical(ClassifiedBasePair basePair) {
    if (basePair.leontisWesthof() != LeontisWesthof.CWW) {
      return false;
    }

    char leftNt = basePair.basePair().left().oneLetterName();
    char rightNt = basePair.basePair().right().oneLetterName();

    String sequence = leftNt < rightNt ? "" + leftNt + rightNt : "" + rightNt + leftNt;

    return "AU".equals(sequence) || "CG".equals(sequence) || "GU".equals(sequence);
  }

  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final AnalysisClient analysisClient;
  private final ConversionClient conversionClient;
  private final RnapolisClient rnapolisClient;
  private final VarnaTzClient varnaTzClient;
  private final RChieClient rChieClient;
  private final TaskProgressPersistenceService taskProgressPersistenceService; // Inject new service

  @Autowired
  public TaskProcessorService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      AnalysisClient analysisClient,
      ConversionClient conversionClient,
      RnapolisClient rnapolisClient,
      VarnaTzClient varnaTzClient,
      RChieClient rChieClient,
      TaskProgressPersistenceService taskProgressPersistenceService) { // Add to constructor
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.analysisClient = analysisClient;
    this.conversionClient = conversionClient;
    this.rnapolisClient = rnapolisClient;
    this.varnaTzClient = varnaTzClient;
    this.rChieClient = rChieClient;
    this.taskProgressPersistenceService = taskProgressPersistenceService; // Assign injected service
  }

  private void updateTaskProgress(
      Task task,
      AtomicInteger currentStepCounter,
      int totalSteps,
      String messageFormat,
      Object... args) {
    int currentStepValue = currentStepCounter.incrementAndGet();
    int finalCurrentStep = Math.min(currentStepValue, totalSteps);
    String formattedMessage = String.format(messageFormat, args);

    // Call the persistProgressUpdate method on the new dedicated service.
    // This ensures the update runs in a new transaction and is visible immediately.
    taskProgressPersistenceService.persistProgressUpdate(
        task.getId(), finalCurrentStep, totalSteps, formattedMessage);

    // Also, update the state of the 'task' object instance being used within processTaskAsync.
    // This ensures that any subsequent logic in processTaskAsync that reads these fields
    // from its local 'task' instance sees the updated values.
    task.setCurrentProgress(finalCurrentStep);
    task.setProgressMessage(formattedMessage);
    // The logger line from persistProgressUpdate will now cover the persisted state.
    // We can add a local log if desired, but it might be redundant.
    // logger.info("Task {} progress (local instance updated): [{}/{}] {}", task.getId(),
    // task.getCurrentProgress(), task.getTotalProgressSteps(), formattedMessage);
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
    AtomicInteger currentStepCounter = new AtomicInteger(0);
    Task task = null;
    int totalSteps = 0; // Declare and initialize totalSteps here

    try {
      task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
      // totalProgressSteps is already set by ComputeService.submitComputation

      var request = objectMapper.readValue(task.getRequest(), ComputeRequest.class);
      int initialFileCount = request.files().size();
      totalSteps = task.getTotalProgressSteps(); // Use pre-calculated total steps
      logger.info(
          "Task {} [processTaskAsync]: Initial totalSteps fetched from task entity: {}",
          taskId,
          totalSteps);
      if (totalSteps <= 0) {
        logger.error(
            "Task {} [processTaskAsync]: totalSteps is {} which is invalid. Progress will likely"
                + " not advance correctly.",
            taskId,
            totalSteps);
      }

      // Now, start actual processing with progress updates.
      // The status will be set to PROCESSING by the first call to updateTaskProgress
      // (via TaskProgressPersistenceService) if it's currently PENDING.
      // The totalSteps calculation included conceptual steps for fetching and parsing.
      // We'll explicitly update progress for these to align the counter.
      // currentStepCounter is already 0.
      updateTaskProgress(task, currentStepCounter, totalSteps, "Fetching task data"); // Step 1
      updateTaskProgress(task, currentStepCounter, totalSteps, "Parsing task parameters"); // Step 2

      // Process files through RNApolis to unify their format
      List<FileData> processedFiles = request.files(); // Initialize with original files
      if (initialFileCount > 0) {
        updateTaskProgress(
            task, currentStepCounter, totalSteps, "Unifying file formats with RNApolis"); // Step 3
        processedFiles = processFilesWithRnapolis(request.files());
      }
      // Note: The totalSteps calculation in submitComputation handles not allocating
      // a step for RNApolis if initialFileCount is 0.

      // Create a new request with the processed files
      ComputeRequest processedRequest =
          new ComputeRequest(
              processedFiles,
              request.confidenceLevel(),
              request.analyzer(),
              request.dotBracket(),
              request.molProbityFilter());

      // parseAndAnalyzeFiles will handle steps 4, 5, 6, 7
      var analyzedModels =
          parseAndAnalyzeFiles(
              processedRequest, task, currentStepCounter, totalSteps, initialFileCount);

      if (analyzedModels.stream().anyMatch(Objects::isNull)) {
        // This specific error condition might be caught earlier or handled by parseAndAnalyzeFiles
        // For now, assume parseAndAnalyzeFiles updates progress correctly for its scope.
        // If it returns nulls, it's an internal error not well-handled by progress steps.
        String failureMsg = "Internal error: Failed to parse one or more models.";
        updateTaskProgress(
            task, currentStepCounter, totalSteps, failureMsg); // Consume a step for failure
        task.setStatus(TaskStatus.FAILED);
        task.setMessage(failureMsg);
        taskRepository.save(task);
        return CompletableFuture.completedFuture(null);
      }

      var modelCount = analyzedModels.size();
      if (modelCount < 2
          && initialFileCount
              > 0) { // Check initialFileCount to ensure this is a meaningful failure
        String failureMsg =
            analyzedModels.isEmpty()
                ? "All models were filtered out before comparison."
                : "Only one model remained after filtering; comparison requires at least two.";
        // Consume remaining steps before failing
        while (currentStepCounter.get() < totalSteps - 1) { // -1 for the final "failed" step
          updateTaskProgress(
              task,
              currentStepCounter,
              totalSteps,
              "Skipping remaining steps due to insufficient models.");
        }
        updateTaskProgress(task, currentStepCounter, totalSteps, failureMsg);
        task.setStatus(TaskStatus.FAILED);
        task.setMessage(failureMsg);
        taskRepository.saveAndFlush(task);
        return CompletableFuture.completedFuture(null);
      }
      if (initialFileCount == 0) { // No files to process
        String msg = "No input files provided for analysis.";
        updateTaskProgress(task, currentStepCounter, totalSteps, msg);
        task.setStatus(
            TaskStatus.COMPLETED); // Or FAILED, depending on desired outcome for no input
        task.setMessage(msg);
        taskRepository.saveAndFlush(task);
        return CompletableFuture.completedFuture(null);
      }

      var firstModel = analyzedModels.get(0); // Assuming at least one model after previous checks
      ReferenceStructureUtil.ReferenceParseResult referenceStructure =
          new ReferenceStructureUtil.ReferenceParseResult(
              Collections.emptyList(), Collections.emptyList());
      if (request.dotBracket() != null && !request.dotBracket().isBlank()) {
        updateTaskProgress(
            task, currentStepCounter, totalSteps, "Reading reference structure from dot-bracket");
        referenceStructure =
            ReferenceStructureUtil.readReferenceStructure(request.dotBracket(), firstModel);
      } else {
        // If this step was allocated in totalSteps but skipped, the counter was NOT incremented.
        // The totalSteps calculation correctly adds this step only if dotBracket is present.
        // So, no adjustment needed here for the counter itself if the step is skipped.
      }

      updateTaskProgress(
          task, currentStepCounter, totalSteps, "Collecting and aggregating interactions");
      var fullInteractionResult = collectInteractions(analyzedModels, referenceStructure);
      var aggregatedInteractionResult = fullInteractionResult.aggregatedResult();

      updateTaskProgress(task, currentStepCounter, totalSteps, "Ranking models");
      Set<ConsensusInteraction> requiredInteractionSet =
          generateReferenceConsensusInteractions(referenceStructure, fullInteractionResult);
      Set<ConsensusInteraction> forbiddenInteractionSet =
          generateForbiddenConsensusInteractions(referenceStructure, fullInteractionResult);
      var rankedModels =
          generateRankedModels(
              analyzedModels,
              fullInteractionResult,
              request,
              requiredInteractionSet,
              forbiddenInteractionSet);

      updateTaskProgress(
          task, currentStepCounter, totalSteps, "Generating dot-bracket for consensus structure");
      var consensusDotBracket =
          generateDotBracket(
              firstModel,
              determineConsensusSet(
                  aggregatedInteractionResult.sortedInteractions,
                  request.confidenceLevel(),
                  ConsensusMode.CANONICAL));

      updateTaskProgress(
          task, currentStepCounter, totalSteps, "Preparing final task result object");
      var taskResult =
          new TaskResult(
              rankedModels, referenceStructure, consensusDotBracket.toStringWithStrands());
      var resultJson = objectMapper.writeValueAsString(taskResult);
      task.setResult(resultJson);

      updateTaskProgress(
          task,
          currentStepCounter,
          totalSteps,
          "Generating 2D visualization for consensus (Varna)");

      // Determine consensus interactions and forbidden interactions for consensus visualization
      Set<ConsensusInteraction> consensusInteractionsToVisualize =
          determineConsensusSet(
              aggregatedInteractionResult.sortedInteractions(),
              request.confidenceLevel(),
              ConsensusMode.ALL);

      // Add consensus-specific interactions that use forbidden residues from reference structure
      ReferenceStructureUtil.ReferenceParseResult finalReferenceStructure = referenceStructure;
      Set<ConsensusInteraction> consensusForbiddenInteractions =
          aggregatedInteractionResult.sortedInteractions().stream()
              .filter(ConsensusInteraction::forbiddenInReference)
              .collect(Collectors.toSet());

      var consensusSvg =
          generateVisualization(
              firstModel, // Use first model as template for consensus
              consensusInteractionsToVisualize,
              consensusForbiddenInteractions, // Forbidden interactions for consensus
              referenceStructure.markedResidues()); // Marked residues for consensus
      logger.debug("Generated SVG for consensus");

      // Prepare RChieData
      RChieData rChieData =
          prepareRChieData(firstModel, aggregatedInteractionResult, referenceStructure, request.confidenceLevel());
      // At this point, rChieData is prepared.
      updateTaskProgress(
          task, currentStepCounter, totalSteps, "Preparing RChie data for consensus structure");
      logger.info(
          "Prepared RChieData with {} top and {} bottom interactions.",
          rChieData.top().size(),
          rChieData.bottom().size());

      // Generate RChie visualization
      try {
        updateTaskProgress(
            task, currentStepCounter, totalSteps, "Generating RChie visualization for consensus");
        org.w3c.dom.svg.SVGDocument rChieSvgDoc = rChieClient.visualize(rChieData);
        byte[] rChieSvgBytes = SVGHelper.export(rChieSvgDoc, Format.SVG);
        String rChieSvgString = new String(rChieSvgBytes);
        task.addModelSvg("rchie-consensus", rChieSvgString);
        logger.info("Successfully generated and stored RChie visualization SVG.");
      } catch (Exception e) {
        logger.error("Failed to generate or store RChie visualization SVG", e);
        // Optionally, add a message to the task or handle the error appropriately
        task.setMessage(
            (task.getMessage() == null ? "" : task.getMessage() + "; ")
                + "Failed to generate RChie visualization: "
                + e.getMessage());
      }

      // Generate model-specific SVGs. This block is estimated as (initialFileCount * 2) steps.
      ConcurrentMap<String, String> modelSvgMap =
          rankedModels.parallelStream()
              .flatMap(
                  rankedModel -> {
                    List<Map.Entry<String, String>> svgEntries = new ArrayList<>();
                    AnalyzedModel correspondingAnalyzedModel =
                        analyzedModels.stream()
                            .filter(am -> am.name().equals(rankedModel.name()))
                            .findFirst()
                            .orElse(null);

                    if (correspondingAnalyzedModel != null) {
                      InteractionCollectionResult modelInteractionResult =
                          fullInteractionResult.perModelResults().get(rankedModel.name());

                      if (modelInteractionResult != null) {
                        // 1. Generate standard model visualization (Varna/VisualizationClient)
                        try {
                          Set<ConsensusInteraction> modelInteractionsToVisualize =
                              determineConsensusSet(
                                  modelInteractionResult.sortedInteractions(),
                                  request.confidenceLevel(),
                                  ConsensusMode.ALL);

                          // Add model-specific interactions that use forbidden residues from
                          // reference structure
                          Set<ConsensusInteraction> forbiddenInteractions =
                              modelInteractionResult.sortedInteractions().stream()
                                  .filter(ConsensusInteraction::forbiddenInReference)
                                  .collect(Collectors.toSet());

                          String modelSvg =
                              generateVisualization(
                                  correspondingAnalyzedModel,
                                  modelInteractionsToVisualize,
                                  forbiddenInteractions,
                                  finalReferenceStructure.markedResidues());
                          logger.debug("Generated standard SVG for model: {}", rankedModel.name());
                          svgEntries.add(Map.entry(rankedModel.name(), modelSvg));
                        } catch (Exception e) {
                          logger.warn(
                              "Failed to generate standard Varna visualization for model {}: {}",
                              rankedModel.name(),
                              e.getMessage());
                        }

                        // 2. Generate RChie SVG for the model
                        try {
                          logger.debug(
                              "Generating RChie visualization for model: {}", rankedModel.name());
                          RChieData rChieModelData =
                              prepareRChieData(
                                  correspondingAnalyzedModel,
                                  modelInteractionResult,
                                  finalReferenceStructure,
                                  request.confidenceLevel());

                          org.w3c.dom.svg.SVGDocument rChieModelSvgDoc =
                              rChieClient.visualize(rChieModelData);
                          byte[] rChieModelSvgBytes =
                              SVGHelper.export(rChieModelSvgDoc, Format.SVG);
                          String rChieModelSvgString = new String(rChieModelSvgBytes);
                          String rChieSvgKey = "rchie-" + rankedModel.name();
                          svgEntries.add(Map.entry(rChieSvgKey, rChieModelSvgString));
                          logger.debug(
                              "Successfully generated RChie visualization SVG for model {}.",
                              rankedModel.name());
                        } catch (Exception e) {
                          logger.error(
                              "Failed to generate RChie visualization SVG for model {}",
                              rankedModel.name(),
                              e);
                        }
                      } else {
                        logger.warn(
                            "Could not find interaction results for model {} to generate SVGs.",
                            rankedModel.name());
                      }
                    } else {
                      logger.warn(
                          "Could not find corresponding AnalyzedModel for RankedModel {} to"
                              + " generate SVGs.",
                          rankedModel.name());
                    }
                    return svgEntries.stream();
                  })
              .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

      // After parallel SVG generation, iterate to update progress for each allocated step
      int actualRankedModelsCount = rankedModels.size();
      for (int i = 0; i < actualRankedModelsCount; i++) {
        RankedModel rankedModel = rankedModels.get(i);
        updateTaskProgress(
            task,
            currentStepCounter,
            totalSteps,
            "Generated Varna SVG for model %d of %d: %s",
            i + 1,
            actualRankedModelsCount,
            rankedModel.name());
        updateTaskProgress(
            task,
            currentStepCounter,
            totalSteps,
            "Generated RChie SVG for model %d of %d: %s",
            i + 1,
            actualRankedModelsCount,
            rankedModel.name());
      }
      // Consume any remaining allocated steps if fewer models were ranked than initialFileCount
      int stepsConsumedForModelSVGs = actualRankedModelsCount * 2;
      int stepsAllocatedForModelSVGs = initialFileCount * 2;
      for (int i = stepsConsumedForModelSVGs; i < stepsAllocatedForModelSVGs; i++) {
        // Increment counter without specific message, or use a generic one
        updateTaskProgress(
            task,
            currentStepCounter,
            totalSteps,
            "Adjusting progress for model SVG generation step.");
      }

      updateTaskProgress(task, currentStepCounter, totalSteps, "Storing all generated SVGs");
      task.addModelSvg("consensus", consensusSvg);
      task.getModelSvgs().putAll(modelSvgMap);
      logger.debug("Stored consensus SVG and {} model-specific SVGs", modelSvgMap.size());

      updateTaskProgress(
          task, currentStepCounter, totalSteps, "Task processing completed successfully");
      task.setStatus(TaskStatus.COMPLETED);
      taskRepository.saveAndFlush(task); // Final save for COMPLETED
    } catch (Exception e) {
      logger.error("Task {} failed with error", taskId, e);
      // Ensure task is not null if exception happened before task was fetched
      if (task == null) {
        var taskOptional = taskRepository.findById(taskId);
        if (taskOptional.isPresent()) task = taskOptional.get();
      }

      if (task != null) {
        String finalMessage = "Task failed: " + e.getMessage();
        // Try to update progress one last time if system is initialized
        if (currentStepCounter != null && totalSteps > 0 && currentStepCounter.get() < totalSteps) {
          updateTaskProgress(task, currentStepCounter, totalSteps, finalMessage);
        } else {
          task.setProgressMessage(finalMessage);
        }
        task.setStatus(TaskStatus.FAILED);
        task.setMessage(finalMessage); // Overwrites progressMessage if needed for final display
        taskRepository.saveAndFlush(task);
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
                      boolean isCanonical = isCanonical(analyzedPair);
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
            .distinct()
            .collect(Collectors.toCollection(HashBag::new));
    logger.trace("Model {}: Found {} canonical base pairs", model.name(), canonicalPairsBag.size());

    var nonCanonicalPairsBag =
        model.structure2D().basePairs().stream()
            .filter(basePair -> !basePair.isCanonical())
            .map(model::basePairToAnalyzed)
            .distinct()
            .collect(Collectors.toCollection(HashBag::new));
    logger.trace(
        "Model {}: Found {} non-canonical base pairs", model.name(), nonCanonicalPairsBag.size());

    var stackingsBag =
        model.structure2D().stackings().stream()
            .map(model::stackingToAnalyzed)
            .distinct()
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
   * @param task The task entity for progress updates.
   * @param currentStepCounter The atomic counter for current step.
   * @param totalSteps The total estimated steps for the task.
   * @return A list of ParsedModel objects.
   */
  private List<ParsedModel> parsePdbFiles(
      List<FileData> files, Task task, AtomicInteger currentStepCounter, int totalSteps) {
    logger.info("Parsing {} PDB files in parallel", files.size());
    List<ParsedModel> result =
        files.parallelStream()
            .map(
                fileData -> {
                  // Inside parallel stream, avoid calling updateTaskProgress directly
                  // to prevent concurrent DB updates. Logging is fine.
                  logger.debug("Attempting to parse file in parallel: {}", fileData.name());
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

    // After parallel processing, update progress for each file parsed.
    for (int i = 0; i < files.size(); i++) {
      updateTaskProgress(
          task,
          currentStepCounter,
          totalSteps,
          "Parsed PDB file %d of %d: %s",
          i + 1,
          files.size(),
          files.get(i).name());
    }
    return result;
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
   * @param currentStepCounter The atomic counter for current step.
   * @param totalSteps The total estimated steps for the task.
   * @param initialFileCountForProgress The count used by parent for step allocation.
   * @return A list of models that passed the filter.
   */
  private List<ParsedModel> filterModelsWithMolProbity(
      List<ParsedModel> models,
      MolProbityFilter filter,
      Task task,
      AtomicInteger currentStepCounter,
      int totalSteps,
      int initialFileCountForProgress) {
    if (filter == MolProbityFilter.ALL) {
      logger.info(
          "MolProbity filtering is set to ALL, skipping actual filtering and progress updates for"
              + " this stage.");
      // Steps for MolProbity were not added to totalSteps if filter is ALL,
      // so no need to call updateTaskProgress here.
      return new ArrayList<>(models); // Return a mutable copy
    }

    logger.info(
        "Attempting MolProbity filtering with level: {} for {} models (allocated steps: {})",
        filter,
        models.size(),
        initialFileCountForProgress);
    var validModels = new ArrayList<ParsedModel>(); // Use mutable list

    try (var rnalyzerClient = new RnalyzerClient()) {
      rnalyzerClient.initializeSession();

      int modelIndex = 0;
      for (ParsedModel model : models) {
        modelIndex++;
        updateTaskProgress(
            task,
            currentStepCounter,
            totalSteps,
            "Applying MolProbity filter to model %d of %d: %s",
            modelIndex,
            models.size(), // Current batch size
            model.name());
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

      // Adjust counter for any discrepancy if models.size() < initialFileCountForProgress
      for (int i = models.size(); i < initialFileCountForProgress; i++) {
        updateTaskProgress(
            task,
            currentStepCounter,
            totalSteps,
            "Adjusting progress for MolProbity step (unprocessed allocation %d of %d)",
            i + 1,
            initialFileCountForProgress);
      }
      return validModels;

    } catch (Exception e) {
      logger.warn(
          "MolProbity filtering failed due to an error initializing or using the RNAlyzer service:"
              + " {}. Proceeding without MolProbity filtering.",
          e.getMessage());
      // If the RNAlyzer service fails entirely, return all original models
      // and consume allocated steps if they were part of totalSteps.
      // If MolProbityFilter was not ALL, steps were allocated.
      if (filter != MolProbityFilter.ALL) {
        for (int i = 0; i < initialFileCountForProgress; i++) {
          String modelName = (i < models.size()) ? models.get(i).name() : "N/A";
          updateTaskProgress(
              task,
              currentStepCounter,
              totalSteps,
              "Skipping MolProbity due to service error for model (estimate %d of %d): %s",
              i + 1,
              initialFileCountForProgress,
              modelName);
        }
      }
      return new ArrayList<>(models); // Return a mutable copy
    }
  }

  /**
   * Analyzes the secondary structure (2D) of the given models using the specified analyzer.
   *
   * @param models The list of models (3D structures) to analyze.
   * @param analyzer The secondary structure analysis tool to use.
   * @param task The task entity for progress updates.
   * @param currentStepCounter The atomic counter for current step.
   * @param totalSteps The total estimated steps for the task.
   * @param initialFileCountForProgress The count used by parent for step allocation.
   * @return A list of AnalyzedModel objects containing both 3D and 2D information.
   */
  private List<AnalyzedModel> analyzeSecondaryStructures(
      List<ParsedModel> models,
      pl.poznan.put.Analyzer analyzer,
      Task task,
      AtomicInteger currentStepCounter,
      int totalSteps,
      int initialFileCountForProgress) {
    logger.info(
        "Analyzing secondary structures for {} models using {} (in parallel, allocated steps: {})",
        models.size(),
        analyzer.name(),
        initialFileCountForProgress);
    List<AnalyzedModel> result =
        models.parallelStream()
            .map(
                model -> {
                  logger.debug("Attempting to analyze 2D for model in parallel: {}", model.name());
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

    // After parallel processing, update progress for each model that was input to this stage.
    for (int i = 0; i < models.size(); i++) {
      updateTaskProgress(
          task,
          currentStepCounter,
          totalSteps,
          "Analyzed 2D structure for model %d of %d: %s",
          i + 1,
          models.size(),
          models.get(i).name());
    }
    // Adjust counter for any discrepancy if models.size() < initialFileCountForProgress
    for (int i = models.size(); i < initialFileCountForProgress; i++) {
      updateTaskProgress(
          task,
          currentStepCounter,
          totalSteps,
          "Adjusting progress for 2D analysis step (unprocessed allocation %d of %d)",
          i + 1,
          initialFileCountForProgress);
    }
    return result;
  }

  /**
   * Orchestrates the parsing, validation, filtering, and analysis of input files.
   *
   * @param request The compute request containing files and parameters.
   * @param task The task entity to update with progress and results.
   * @param currentStepCounter The atomic counter for current step.
   * @param totalSteps The total estimated steps for the task.
   * @param initialFileCountForProgress The number of files based on which parent allocated steps.
   * @return A list of fully analyzed models.
   */
  private List<AnalyzedModel> parseAndAnalyzeFiles(
      ComputeRequest request,
      Task task,
      AtomicInteger currentStepCounter,
      int totalSteps,
      int initialFileCountForProgress) {

    // 1. Parse PDB files into 3D structures
    List<ParsedModel> parsedModels =
        parsePdbFiles(request.files(), task, currentStepCounter, totalSteps);
    // parsePdbFiles internally updates currentStepCounter for each file in request.files()

    // 2. Check consistency and unify models if needed
    updateTaskProgress(task, currentStepCounter, totalSteps, "Unifying model structures");
    List<ParsedModel> consistentModels = unifyModelsIfNeeded(parsedModels);
    // This block is 1 step.

    // 3. Filter models using MolProbity
    List<ParsedModel> validModels =
        filterModelsWithMolProbity(
            consistentModels,
            request.molProbityFilter(),
            task,
            currentStepCounter,
            totalSteps,
            initialFileCountForProgress); // Pass initialFileCountForProgress for adjustment

    // 4. Analyze secondary structures for valid models
    List<AnalyzedModel> analyzedModels =
        analyzeSecondaryStructures(
            validModels,
            request.analyzer(),
            task,
            currentStepCounter,
            totalSteps,
            initialFileCountForProgress); // Pass initialFileCountForProgress for adjustment

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
                  return switch (consensusMode) {
                    case CANONICAL -> interaction.category() == InteractionCategory.BASE_PAIR
                        && interaction.isCanonical();
                    case NON_CANONICAL -> interaction.category() == InteractionCategory.BASE_PAIR
                        && !interaction.isCanonical();
                    case STACKING -> interaction.category() == InteractionCategory.STACKING;
                    case ALL -> true;
                  };
                })
            .filter(interaction -> isInteractionConsidered(interaction, confidenceLevel))
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
   * @param requiredInteractionSet
   * @param forbiddenInteractionSet
   * @return A list of RankedModel objects, sorted by rank.
   */
  private List<RankedModel> generateRankedModels(
      List<AnalyzedModel> analyzedModels,
      FullInteractionCollectionResult fullInteractionResult,
      ComputeRequest request,
      Set<ConsensusInteraction> requiredInteractionSet,
      Set<ConsensusInteraction> forbiddenInteractionSet) {
    logger.info("Starting generation of ranked models for all consensus modes");
    Integer confidenceLevel = request.confidenceLevel();

    // Temporary storage for per-model, per-mode scores and ranks
    Map<String, Map<ConsensusMode, Double>> infScoresPerModel = new HashMap<>();
    Map<String, Map<ConsensusMode, Double>> f1ScoresPerModel = new HashMap<>();
    Map<String, Map<ConsensusMode, Integer>> ranksPerModel = new HashMap<>();
    Map<String, DefaultDotBracketFromPdb> dotBracketsPerModel = new HashMap<>();

    // Initialize maps for each model
    for (AnalyzedModel model : analyzedModels) {
      infScoresPerModel.put(model.name(), new EnumMap<>(ConsensusMode.class));
      f1ScoresPerModel.put(model.name(), new EnumMap<>(ConsensusMode.class));
      ranksPerModel.put(model.name(), new EnumMap<>(ConsensusMode.class));

      // Generate dot-bracket once per model (based on its canonical pairs)
      // This is independent of the consensus mode used for scoring.
      List<ConsensusInteraction> modelCanonicalConsensusInteractions =
          fullInteractionResult.perModelResults().get(model.name()).sortedInteractions();
      DefaultDotBracketFromPdb dotBracket =
          generateDotBracket(
              model,
              determineConsensusSet(
                  modelCanonicalConsensusInteractions, confidenceLevel, ConsensusMode.CANONICAL));
      dotBracketsPerModel.put(model.name(), dotBracket);
    }

    // Iterate through each ConsensusMode to calculate scores and ranks
    for (ConsensusMode modeToAnalyze : ConsensusMode.values()) {
      logger.info("Processing models for ConsensusMode: {}", modeToAnalyze);

      // 1. Determine the target consensus set for the current modeToAnalyze
      Set<ConsensusInteraction> targetConsensusSet =
          determineConsensusSet(
              fullInteractionResult.aggregatedResult().sortedInteractions(),
              confidenceLevel,
              modeToAnalyze);
      logger.debug(
          "Mode {}: Prepared target consensus set with {} entries",
          modeToAnalyze,
          targetConsensusSet.size());

      Map<String, Double> currentModeInfScores = new HashMap<>();

      // 2. Calculate scores for each model for the current modeToAnalyze
      for (AnalyzedModel model : analyzedModels) {
        List<ConsensusInteraction> modelInteractions =
            fullInteractionResult.perModelResults().get(model.name()).sortedInteractions();
        Set<ConsensusInteraction> modelConsensusSet =
            determineConsensusSet(modelInteractions, confidenceLevel, modeToAnalyze);

        double inf, f1;
        if (confidenceLevel == null) { // Fuzzy mode
          inf =
              InteractionNetworkFidelity.calculateFuzzy(
                  targetConsensusSet,
                  modelConsensusSet,
                  requiredInteractionSet,
                  forbiddenInteractionSet);
          f1 =
              F1score.calculateFuzzy(
                  targetConsensusSet,
                  modelConsensusSet,
                  requiredInteractionSet,
                  forbiddenInteractionSet);
        } else { // Threshold mode
          inf =
              InteractionNetworkFidelity.calculate(
                  targetConsensusSet,
                  modelConsensusSet,
                  requiredInteractionSet,
                  forbiddenInteractionSet);
          f1 =
              F1score.calculate(
                  targetConsensusSet,
                  modelConsensusSet,
                  requiredInteractionSet,
                  forbiddenInteractionSet);
        }
        infScoresPerModel.get(model.name()).put(modeToAnalyze, inf);
        f1ScoresPerModel.get(model.name()).put(modeToAnalyze, f1);
        currentModeInfScores.put(model.name(), inf);
        logger.debug("Model {}: Mode {}: INF = {}, F1 = {}", model.name(), modeToAnalyze, inf, f1);
      }

      // 3. Determine ranks for the current modeToAnalyze based on INF scores
      final List<Double> sortedInfsForMode =
          currentModeInfScores.values().stream().sorted(Comparator.reverseOrder()).toList();

      for (AnalyzedModel model : analyzedModels) {
        double modelInf = currentModeInfScores.get(model.name());
        int rank = sortedInfsForMode.indexOf(modelInf) + 1;
        ranksPerModel.get(model.name()).put(modeToAnalyze, rank);
        logger.trace("Model {}: Mode {}: Assigned rank {}", model.name(), modeToAnalyze, rank);
      }
    }

    // 4. Create RankedModel records
    List<RankedModel> rankedModelsResult =
        analyzedModels.stream()
            .map(
                model ->
                    new RankedModel(
                        model.name(),
                        model.basePairsAndStackings(),
                        model.canonicalBasePairs(),
                        model.nonCanonicalBasePairs(),
                        model.stackings(),
                        infScoresPerModel.get(model.name()),
                        f1ScoresPerModel.get(model.name()),
                        ranksPerModel.get(model.name()),
                        dotBracketsPerModel.get(model.name()).toStringWithStrands()))
            .collect(Collectors.toList());

    // 5. Sort the final list based on the rank of the originally requested consensusMode
    rankedModelsResult.sort(
        Comparator.comparingInt(
            rm -> rm.rank().getOrDefault(ConsensusMode.ALL, Integer.MAX_VALUE)));

    logger.info(
        "Finished generating ranked models, sorted by requested mode: {}", ConsensusMode.ALL);
    return rankedModelsResult;
  }

  private String generateVisualization(
      AnalyzedModel model, Set<ConsensusInteraction> interactionsToVisualize) {
    return generateVisualization(
        model, interactionsToVisualize, Collections.emptySet(), Collections.emptyList());
  }

  private String generateVisualization(
      AnalyzedModel model,
      Set<ConsensusInteraction> interactionsToVisualize,
      Set<ConsensusInteraction> forbiddenInteractions,
      List<PdbNamedResidueIdentifier> markedResidues) {
    try {
      logger.info("Generating visualization using VarnaTzClient (remote varna-tz service)");
      var structureData =
          createStructureData(
              model, interactionsToVisualize, forbiddenInteractions, markedResidues);
      var svgDoc = varnaTzClient.visualize(structureData);
      var svgBytes = SVGHelper.export(svgDoc, Format.SVG);
      return new String(svgBytes);
    } catch (Exception e) {
      logger.warn("Visualization generation failed", e);
      throw new RuntimeException("Visualization generation failed: " + e.getMessage(), e);
    }
  }

  private StructureData createStructureData(
      AnalyzedModel model, Set<ConsensusInteraction> interactionsToVisualize) {
    return createStructureData(
        model, interactionsToVisualize, Collections.emptySet(), Collections.emptyList());
  }

  private StructureData createStructureData(
      AnalyzedModel model,
      Set<ConsensusInteraction> interactionsToVisualize,
      Set<ConsensusInteraction> forbiddenInteractions,
      List<PdbNamedResidueIdentifier> markedResidues) {
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

              // Set outline color for marked residues
              if (markedResidues.contains(residueIdentifier)) {
                nucleotide.outlineColor = FORBIDDEN_INTERACTION_COLOR;
              }

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
                  if (forbiddenInteractions.contains(interaction)) {
                    varnaBp.color = FORBIDDEN_INTERACTION_COLOR;
                  } else {
                    varnaBp.color = getColorForConfidence(confidence);
                  }

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

                  logger.trace(
                      "Created Varna Stacking: id1={}, id2={}, color={}",
                      varnaStacking.id1,
                      varnaStacking.id2,
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
    if (confidenceLevel != null) {
      // Threshold Mode: Consider if present in reference OR meets the threshold count
      return interaction.modelCount() >= confidenceLevel;
    }

    // Fuzzy Mode: Consider if present in reference (implicit probability 1.0) or has positive
    // probability
    return interaction.probability() > 0.0;
  }

  /**
   * Generates a Set of ConsensusInteraction objects from the reference structure containing only
   * base pairs. Uses fullInteractionResult to get actual model counts and probabilities, but
   * ensures all reference pairs are included (with zero probability if not found in models).
   *
   * @param referenceStructure The parsed reference structure from dot-bracket notation.
   * @param fullInteractionResult The complete interaction results containing all discovered
   *     interactions.
   * @return A set of ConsensusInteraction objects representing the reference base pairs.
   */
  private Set<ConsensusInteraction> generateReferenceConsensusInteractions(
      ReferenceStructureUtil.ReferenceParseResult referenceStructure,
      FullInteractionCollectionResult fullInteractionResult) {
    if (referenceStructure == null || referenceStructure.basePairs().isEmpty()) {
      return Collections.emptySet();
    }

    // Create a map for quick lookup of existing interactions by partner pair
    Map<String, ConsensusInteraction> existingInteractionsMap =
        fullInteractionResult.aggregatedResult().sortedInteractions().stream()
            .filter(
                interaction ->
                    interaction.category() == ConsensusInteraction.InteractionCategory.BASE_PAIR)
            .collect(
                Collectors.toMap(
                    interaction -> {
                      // Create a consistent key for partner pairs
                      var p1 = interaction.partner1();
                      var p2 = interaction.partner2();
                      return p1.compareTo(p2) <= 0 ? p1 + ":" + p2 : p2 + ":" + p1;
                    },
                    interaction -> interaction,
                    (existing, replacement) -> existing)); // Keep first if duplicates

    return referenceStructure.basePairs().stream()
        .map(
            basePair -> {
              // Ensure partner1 is always "less than" partner2 for consistent sorting
              var p1 = basePair.left();
              var p2 = basePair.right();
              if (p1.compareTo(p2) > 0) {
                var temp = p1;
                p1 = p2;
                p2 = temp;
              }

              // Create lookup key
              String lookupKey = p1 + ":" + p2;

              // Check if this pair exists in the full interaction results
              ConsensusInteraction existingInteraction = existingInteractionsMap.get(lookupKey);

              if (existingInteraction != null) {
                // Use the existing interaction data but mark as present in reference
                return new ConsensusInteraction(
                    existingInteraction.partner1(),
                    existingInteraction.partner2(),
                    existingInteraction.category(),
                    existingInteraction.leontisWesthof(),
                    existingInteraction.isCanonical(),
                    existingInteraction.modelCount(),
                    existingInteraction.probability(),
                    true, // Present in reference by definition
                    existingInteraction.forbiddenInReference());
              } else {
                // This reference pair was not found in any model - create with zero probability
                var classifiedBasePair = ImmutableAnalyzedBasePair.of(ImmutableBasePair.of(p1, p2));
                boolean isCanonical = isCanonical(classifiedBasePair);

                return new ConsensusInteraction(
                    p1,
                    p2,
                    ConsensusInteraction.InteractionCategory.BASE_PAIR,
                    Optional.of(LeontisWesthof.CWW), // Reference structure assumes Watson-Crick
                    isCanonical,
                    0, // Not found in any model
                    0.0, // Zero probability since not found in models
                    true, // Present in reference by definition
                    false // Not forbidden in reference by definition
                    );
              }
            })
        .collect(Collectors.toSet());
  }

  /**
   * Generates a Set of ConsensusInteraction objects representing forbidden base pairs. These are
   * interactions that involve residues marked as forbidden in the reference structure.
   *
   * @param referenceStructure The parsed reference structure containing marked (forbidden)
   *     residues.
   * @param fullInteractionResult The complete interaction results containing all discovered
   *     interactions.
   * @return A set of ConsensusInteraction objects representing forbidden base pairs.
   */
  private Set<ConsensusInteraction> generateForbiddenConsensusInteractions(
      ReferenceStructureUtil.ReferenceParseResult referenceStructure,
      FullInteractionCollectionResult fullInteractionResult) {
    if (referenceStructure == null || referenceStructure.markedResidues().isEmpty()) {
      return Collections.emptySet();
    }

    Set<PdbNamedResidueIdentifier> markedResidues =
        new HashSet<>(referenceStructure.markedResidues());

    return fullInteractionResult.aggregatedResult().sortedInteractions().stream()
        .filter(
            interaction ->
                interaction.category() == ConsensusInteraction.InteractionCategory.BASE_PAIR)
        .filter(
            interaction ->
                markedResidues.contains(interaction.partner1())
                    || markedResidues.contains(interaction.partner2()))
        .collect(Collectors.toSet());
  }

  private RChieData prepareRChieData(
      AnalyzedModel firstModel,
      InteractionCollectionResult interactionCollectionResult,
      ReferenceStructureUtil.ReferenceParseResult referenceStructure,
      Integer confidenceLevel) {
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
        interactionCollectionResult.sortedInteractions().stream()
            .filter(
                ci ->
                    ci.category() == ConsensusInteraction.InteractionCategory.BASE_PAIR
                        && ci.isCanonical())
            .filter(ci -> isInteractionConsidered(ci, confidenceLevel))
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

                  String color;
                  if (referenceStructure != null
                      && (referenceStructure.markedResidues().contains(ci.partner1())
                          || referenceStructure.markedResidues().contains(ci.partner2()))) {
                    color = FORBIDDEN_INTERACTION_COLOR;
                  } else {
                    color = getColorForConfidence(ci.probability());
                  }
                  return new RChieInteraction(rchieI, rchieJ, Optional.of(color));
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
              .map(
                  bp -> {
                    try {
                      PdbNamedResidueIdentifier refNt1 = bp.left();
                      PdbNamedResidueIdentifier refNt2 = bp.right();

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
                          rchieI, rchieJ, Optional.of(getColorForConfidence(1.0)));
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
