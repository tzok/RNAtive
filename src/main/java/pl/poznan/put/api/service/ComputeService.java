package pl.poznan.put.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.exception.ResourceNotFoundException;
import pl.poznan.put.api.exception.TaskNotFoundException;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.util.ReferenceStructureUtil;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.MoleculeType;
import pl.poznan.put.pdb.analysis.PdbParser;
import pl.poznan.put.structure.AnalyzedBasePair;

@Service
public class ComputeService {
  private static final Logger logger = LoggerFactory.getLogger(ComputeService.class);
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final TaskProcessorService taskProcessorService;
  private final RnapolisClient rnapolisClient;

  @Autowired
  public ComputeService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      TaskProcessorService taskProcessorService,
      RnapolisClient rnapolisClient) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.taskProcessorService = taskProcessorService;
    this.rnapolisClient = rnapolisClient;
  }

  public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
    logger.info("Submitting new computation task with {} files", request.files().size());
    var task = new Task();
    task.setRequest(objectMapper.writeValueAsString(request));
    task.setStatus(TaskStatus.PENDING); // Initial status

    // Calculate total estimated steps based on the request
    int initialFileCount = request.files().size();
    int totalSteps = 0;
    totalSteps += 1; // 1. Fetching task from repo (conceptual step in TaskProcessorService)
    totalSteps += 1; // 2. Parsing task request (conceptual step in TaskProcessorService)
    if (initialFileCount > 0) {
      totalSteps += 1; // 3. RNApolis unification service (block)
    }
    // parseAndAnalyzeFiles block:
    totalSteps += initialFileCount; // 4. PDB Parsing (per file)
    totalSteps += 1; // 5. Model Unification (block)
    totalSteps += initialFileCount; // 6. MolProbity Filtering (per file)
    totalSteps += initialFileCount; // 7. Secondary Structure Analysis (per file)
    if (request.dotBracket() != null && !request.dotBracket().isBlank()) {
      totalSteps += 1; // 8. Reading reference structure
    }
    totalSteps += 1; // 9. Collecting and sorting all interactions
    totalSteps += 1; // 10. Ranking models
    totalSteps += 1; // 11. Generating dot bracket for consensus
    totalSteps += 1; // 12. Creating task result object
    totalSteps += 1; // 13. Generating consensus Varna SVG
    totalSteps += 1; // 14. Preparing RChieData for consensus
    totalSteps += 1; // 15. Generating RChie visualization for consensus
    totalSteps += initialFileCount * 2; // 16. (Varna + RChie) for each model
    totalSteps += 1; // 17. Storing all generated SVGs
    totalSteps += 1; // 18. Finalizing task (COMPLETED/FAILED)

    task.setTotalProgressSteps(totalSteps);
    task.setCurrentProgress(0);
    task.setProgressMessage("Task submitted, awaiting processing...");

    task = taskRepository.save(task); // Save with initial progress info
    var taskId = task.getId();

    // Schedule async processing without waiting
    taskProcessorService.processTaskAsync(taskId);

    return new ComputeResponse(taskId);
  }

  public TaskStatusResponse getTaskStatus(String taskId) {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    return new TaskStatusResponse(
        task.getId(),
        task.getStatus(),
        task.getCreatedAt(),
        task.getMessage(),
        task.getRemovalReasons(),
        task.getCurrentProgress(),
        task.getTotalProgressSteps(),
        task.getProgressMessage());
  }

  public String getTaskSvg(String taskId) {
    return getModelSvg(taskId, "consensus");
  }

  public String getModelSvg(String taskId, String modelName) {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task " + taskId + " is not completed yet");
    }

    var modelSvgs = task.getModelSvgs();
    if (modelSvgs == null || !modelSvgs.containsKey(modelName)) {
      throw new ResourceNotFoundException(
          String.format(
              "SVG visualization not available for model '%s' in task %s", modelName, taskId));
    }
    return modelSvgs.get(modelName);
  }

  /**
   * Splits a file into multiple files using RNApolis service.
   *
   * @param fileData The file to split
   * @return List of split files, each potentially including its RNA sequence.
   */
  public List<FileData> splitFile(FileData fileData) {
    List<FileData> splitFiles = rnapolisClient.splitFile(fileData);

    // Process split files in parallel to extract sequence
    return splitFiles.parallelStream()
        .map(
            split -> {
              try {
                var parser = new PdbParser();
                var sequence =
                    parser.parse(split.content()).stream()
                        .findFirst()
                        .map(model -> model.filteredNewInstance(MoleculeType.RNA))
                        .map(
                            rnaModel ->
                                rnaModel.namedResidueIdentifiers().stream()
                                    .map(PdbNamedResidueIdentifier::oneLetterName)
                                    .map(String::valueOf)
                                    .map(String::toUpperCase)
                                    .collect(Collectors.joining()))
                        .orElse(null); // Return null if parsing or sequence extraction fails
                return split.withSequence(sequence);
              } catch (Exception e) {
                logger.warn(
                    "Failed to parse or extract sequence for split file: {}. Error: {}",
                    split.name(),
                    e.getMessage());
                return split.withSequence(
                    null); // Return original FileData without sequence on error
              }
            })
        .collect(Collectors.toList());
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
    // var requestedMode = taskResult.requestedConsensusMode(); // No longer needed here

    var totalModelCount = results.size();
    var allInteractions =
        results.stream()
            .map(RankedModel::basePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    // Collect all interactions from all models
    var allCanonicalPairs =
        results.stream()
            .map(RankedModel::canonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allNonCanonicalPairs =
        results.stream()
            .map(RankedModel::nonCanonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allStackings =
        results.stream()
            .map(RankedModel::stackings)
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

    var fileNames = results.stream().map(RankedModel::name).collect(Collectors.toList());

    return new TablesResponse(
        rankingTable,
        canonicalTable,
        nonCanonicalTable,
        stackingsTable,
        fileNames,
        taskResult.dotBracket());
  }

  private TableData generateRankingTable(List<RankedModel> models) {
    var headers = new ArrayList<String>();
    headers.add("File name");

    // Define the desired order for ConsensusModes
    List<ConsensusMode> orderedModes = new ArrayList<>();
    orderedModes.add(ConsensusMode.ALL);
    for (ConsensusMode mode : ConsensusMode.values()) {
      if (mode != ConsensusMode.ALL) {
        orderedModes.add(mode);
      }
    }

    for (ConsensusMode mode : orderedModes) {
      headers.add(String.format("Rank (%s)", mode.name()));
      headers.add(String.format("INF (%s)", mode.name()));
      headers.add(String.format("F1 (%s)", mode.name()));
    }

    var rows =
        models.stream()
            .map(
                model -> {
                  var row = new ArrayList<>();
                  row.add(model.name());
                  for (ConsensusMode mode : orderedModes) {
                    row.add(model.rank().getOrDefault(mode, -1));
                    row.add(model.interactionNetworkFidelity().getOrDefault(mode, Double.NaN));
                    row.add(model.f1score().getOrDefault(mode, Double.NaN));
                  }
                  return (List<Object>) row;
                })
            .collect(Collectors.toList());
    return new TableData(headers, rows);
  }

  private TableData generatePairsTable(
      List<? extends AnalyzedBasePair> pairs,
      HashBag<AnalyzedBasePair> allInteractions,
      int totalModelCount,
      ReferenceStructureUtil.ReferenceParseResult referenceStructure) {
    var headers =
        List.of(
            "Nt1",
            "Nt2",
            "LW class",
            "Confidence",
            "Constraint match"); // "Paired in reference", "Unpaired in reference");
    var rows =
        pairs.stream()
            .distinct()
            .map(
                pair -> {
                  var confidence = allInteractions.getCount(pair) / (double) totalModelCount;
                  String constraint_match =
                      "n/a"; // if the pair existence was not stated within the reference structure
                  if (referenceStructure
                      .basePairs()
                      .contains(pair.basePair())) { // pair was in ref struct and is here too
                    constraint_match = "+";
                  }
                  if (referenceStructure.markedResidues().contains(pair.basePair().left())
                      || referenceStructure
                          .markedResidues()
                          .contains(
                              pair.basePair()
                                  .right())) { // pair exists here, but was forbidden in ref
                    // structure
                    constraint_match = "-";
                  }
                  return List.<Object>of(
                      pair.basePair().left().toString(),
                      pair.basePair().right().toString(),
                      pair.leontisWesthof().toString(),
                      confidence,
                      constraint_match
                      // referenceStructure.basePairs().contains(pair.basePair()),
                      // referenceStructure.markedResidues().contains(pair.basePair().left())
                      //     ||
                      // referenceStructure.markedResidues().contains(pair.basePair().right())
                      );
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
            .distinct()
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
            .filter(model -> model.name().equals(filename))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + filename));

    var totalModelCount = results.size();
    var allInteractions =
        results.stream()
            .map(RankedModel::basePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    var canonicalTable =
        generatePairsTable(
            targetModel.canonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    var nonCanonicalTable =
        generatePairsTable(
            targetModel.nonCanonicalBasePairs(),
            allInteractions,
            totalModelCount,
            taskResult.referenceStructure());
    var stackingsTable =
        generateStackingsTable(targetModel.stackings(), allInteractions, totalModelCount);

    return new ModelTablesResponse(
        canonicalTable,
        nonCanonicalTable,
        stackingsTable,
        targetModel.dotBracket()); // Already correct
  }

  public JsonNode getTaskRequest(String taskId) throws IOException {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    var requestJson = task.getRequest();
    if (requestJson == null || requestJson.isBlank()) {
      return objectMapper.createObjectNode(); // Return empty JSON object if no request stored
    }

    // Parse the JSON string into a JsonNode tree
    JsonNode rootNode = objectMapper.readTree(requestJson);

    // Remove the "files" field if the root is an object
    if (rootNode.isObject()) {
      ObjectNode objectNode = (ObjectNode) rootNode;
      objectNode.remove("files");
    }

    return rootNode; // Return the modified JsonNode
  }

  public java.util.Map<String, JsonNode> getTaskMolProbityResponses(String taskId) {
    var task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    var stringMap = task.getMolprobityResponses();
    var jsonNodeMap = new java.util.HashMap<String, JsonNode>();

    for (var entry : stringMap.entrySet()) {
      try {
        JsonNode node = objectMapper.readTree(entry.getValue());
        jsonNodeMap.put(entry.getKey(), node);
      } catch (JsonProcessingException e) {
        logger.error(
            "Failed to parse MolProbity JSON string for model {} in task {}: {}",
            entry.getKey(),
            taskId,
            entry.getValue(),
            e);
        // Optionally put an error node or skip the entry
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("error", "Failed to parse stored JSON");
        errorNode.put("originalValue", entry.getValue());
        jsonNodeMap.put(entry.getKey(), errorNode);
      }
    }
    return jsonNodeMap;
  }
}
