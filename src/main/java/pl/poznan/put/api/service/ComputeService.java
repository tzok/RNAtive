package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.RankedModel;
import java.io.IOException;
import java.io.UncheckedIOException;
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

  public FileData getTaskFile(String taskId, String filename) throws Exception {
    Task task = taskRepository.findById(taskId).orElseThrow();
    ComputeRequest request = objectMapper.readValue(task.getRequest(), ComputeRequest.class);
    
    return request.files().stream()
        .filter(file -> file.name().equals(filename))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("File not found: " + filename));
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

    // Get the best ranked model
    RankedModel bestModel = result.results().get(0);
    
    int totalModelCount = result.results().size();
    var allInteractions = result.results().stream()
        .map(RankedModel::getAnalyzedModel)
        .map(AnalyzedModel::basePairsAndStackings)
        .flatMap(List::stream)
        .collect(Collectors.toCollection(HashBag::new));

    String rankingCsv = csvGenerationService.generateRankingCsv(result.results());
    String canonicalCsv = csvGenerationService.generatePairsCsv(
        bestModel.getAnalyzedModel().canonicalBasePairs(), allInteractions, totalModelCount);
    String nonCanonicalCsv = csvGenerationService.generatePairsCsv(
        bestModel.getAnalyzedModel().nonCanonicalBasePairs(), allInteractions, totalModelCount);
    String stackingsCsv = csvGenerationService.generateStackingsCsv(
        bestModel.getAnalyzedModel().stackings(), allInteractions, totalModelCount);

    return new CsvTablesResponse(rankingCsv, canonicalCsv, nonCanonicalCsv, stackingsCsv);
  }

}
