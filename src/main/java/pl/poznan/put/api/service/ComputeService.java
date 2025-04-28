package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    task.setStatus(TaskStatus.PENDING); // Explicitly set initial status
    task = taskRepository.save(task);
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
        task.getRemovalReasons());
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
    // logger.debug("TASK REQUEST: {}", task.getRequest());
    var requestJson = task.getRequest();

    ObjectMapper mapper = new ObjectMapper();
    // Parse the JSON string into a JsonNode tree
    JsonNode rootNode = mapper.readTree(requestJson);
    // Cast to ObjectNode so we can remove fields
    if (rootNode.isObject()) {
      ObjectNode objectNode = (ObjectNode) rootNode;
      objectNode.remove("files");
    }
    // Convert the modified JsonNode back to string
    String requestParsed = mapper.writeValueAsString(rootNode);
    logger.debug("TASK REQUEST: {}", requestParsed);
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
        rankingTable,
        canonicalTable,
        nonCanonicalTable,
        stackingsTable,
        fileNames,
        taskResult.dotBracket(),
        requestParsed);
  }

  private TableData generateRankingTable(List<RankedModel> models) {
    var headers = List.of("Rank", "File name", "INF", "F1");
    var rows =
        models.stream()
            .map(
                model ->
                    List.<Object>of(
                        model.getRank(),
                        model.getName(),
                        model.getInteractionNetworkFidelity(),
                        model.getF1score()))
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

    return new ModelTablesResponse(
        canonicalTable, nonCanonicalTable, stackingsTable, targetModel.getDotBracket());
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
