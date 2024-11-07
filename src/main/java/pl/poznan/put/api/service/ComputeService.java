package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.RankedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.repository.TaskRepository;

@Service
public class ComputeService {
  private final TaskRepository taskRepository;
  private final ObjectMapper objectMapper;
  private final CsvGenerationService csvGenerationService;

  public ComputeService(
      TaskRepository taskRepository,
      ObjectMapper objectMapper,
      CsvGenerationService csvGenerationService) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
    this.csvGenerationService = csvGenerationService;
  }

  public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
    Task task = new Task();
    task.setRequest(objectMapper.writeValueAsString(request));
    task = taskRepository.save(task);

    // Start async computation
    processTaskAsync(task.getId());

    return new ComputeResponse(task.getId());
  }

  @Async
  public void processTaskAsync(String taskId) {
    Task task = taskRepository.findById(taskId).orElseThrow();
    try {
      task.setStatus(TaskStatus.PROCESSING);
      taskRepository.save(task);

      // TODO: Implement actual computation logic
      // For now, just simulate processing
      Thread.sleep(5000);

      task.setStatus(TaskStatus.COMPLETED);
      // TODO: Set actual result
      task.setResult("{}");
    } catch (Exception e) {
      task.setStatus(TaskStatus.FAILED);
    }
    taskRepository.save(task);
  }

  public TaskStatusResponse getTaskStatus(String taskId) {
    Task task = taskRepository.findById(taskId).orElseThrow();
    return new TaskStatusResponse(task.getId(), task.getStatus(), task.getCreatedAt());
  }

  public TaskResultResponse getTaskResult(String taskId) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    // TODO: Convert result string to actual result object
    return new TaskResultResponse(task.getId(), null);
  }

  public String getTaskSvg(String taskId) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    if (task.getSvg() == null) {
      throw new IllegalStateException("SVG visualization not available");
    }

    return task.getSvg();
  }

  public CsvTablesResponse getCsvTables(String taskId) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    TaskResultResponse result = getTaskResult(taskId);
    if (result.results() == null || result.results().isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    int totalModelCount = result.results().size();
    var allInteractions =
        result.results().stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::basePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    // Collect all interactions from all models
    var allCanonicalPairs =
        result.results().stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::canonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allNonCanonicalPairs =
        result.results().stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::nonCanonicalBasePairs)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    var allStackings =
        result.results().stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::stackings)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    String rankingCsv = csvGenerationService.generateRankingCsv(result.results());
    String canonicalCsv =
        csvGenerationService.generatePairsCsv(
            allCanonicalPairs, allInteractions, totalModelCount);
    String nonCanonicalCsv =
        csvGenerationService.generatePairsCsv(
            allNonCanonicalPairs, allInteractions, totalModelCount);
    String stackingsCsv =
        csvGenerationService.generateStackingsCsv(
            allStackings, allInteractions, totalModelCount);

    return new CsvTablesResponse(rankingCsv, canonicalCsv, nonCanonicalCsv, stackingsCsv);
  }

  public ModelCsvTablesResponse getModelCsvTables(String taskId, String filename) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();

    if (task.getStatus() != TaskStatus.COMPLETED) {
      throw new IllegalStateException("Task is not completed yet");
    }

    TaskResultResponse result = getTaskResult(taskId);
    if (result.results() == null || result.results().isEmpty()) {
      throw new IllegalStateException("No results available");
    }

    // Find the model with matching filename
    RankedModel targetModel =
        result.results().stream()
            .filter(model -> model.getName().equals(filename))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + filename));

    int totalModelCount = result.results().size();
    var allInteractions =
        result.results().stream()
            .map(RankedModel::getAnalyzedModel)
            .map(AnalyzedModel::basePairsAndStackings)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashBag::new));

    String canonicalCsv =
        csvGenerationService.generatePairsCsv(
            targetModel.getAnalyzedModel().canonicalBasePairs(), allInteractions, totalModelCount);
    String nonCanonicalCsv =
        csvGenerationService.generatePairsCsv(
            targetModel.getAnalyzedModel().nonCanonicalBasePairs(),
            allInteractions,
            totalModelCount);
    String stackingsCsv =
        csvGenerationService.generateStackingsCsv(
            targetModel.getAnalyzedModel().stackings(), allInteractions, totalModelCount);

    return new ModelCsvTablesResponse(canonicalCsv, nonCanonicalCsv, stackingsCsv);
  }
}
