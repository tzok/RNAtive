package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
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

  public ComputeService(TaskRepository taskRepository, ObjectMapper objectMapper) {
    this.taskRepository = taskRepository;
    this.objectMapper = objectMapper;
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
    
    // Define CSV headers
    String[] pairHeaders = {"Nt1", "Nt2", "Leontis-Westhof", "Confidence"};
    String[] stackingHeaders = {"Nt1", "Nt2", "Confidence"};

    // Calculate total model count
    int totalModelCount = result.results().size();

    // Collect all interactions to calculate frequencies
    var allInteractions = result.results().stream()
        .map(RankedModel::getAnalyzedModel)
        .map(AnalyzedModel::basePairsAndStackings)
        .flatMap(List::stream)
        .collect(Collectors.toCollection(HashBag::new));

    // Generate CSV for canonical pairs
    StringWriter canonicalWriter = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(canonicalWriter, 
        CSVFormat.Builder.create().setHeader(pairHeaders).build())) {
      bestModel.getAnalyzedModel().canonicalBasePairs().forEach(pair -> {
        try {
          double confidence = allInteractions.getCount(pair) / (double) totalModelCount;
          printer.printRecord(
              pair.basePair().left(),
              pair.basePair().right(),
              pair.leontisWesthof(),
              confidence);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    // Generate CSV for non-canonical pairs
    StringWriter nonCanonicalWriter = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(nonCanonicalWriter,
        CSVFormat.Builder.create().setHeader(pairHeaders).build())) {
      bestModel.getAnalyzedModel().nonCanonicalBasePairs().forEach(pair -> {
        try {
          double confidence = allInteractions.getCount(pair) / (double) totalModelCount;
          printer.printRecord(
              pair.basePair().left(),
              pair.basePair().right(),
              pair.leontisWesthof(),
              confidence);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    // Generate CSV for stackings
    StringWriter stackingsWriter = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(stackingsWriter,
        CSVFormat.Builder.create().setHeader(stackingHeaders).build())) {
      bestModel.getAnalyzedModel().stackings().forEach(stacking -> {
        try {
          double confidence = allInteractions.getCount(stacking) / (double) totalModelCount;
          printer.printRecord(
              stacking.basePair().left(),
              stacking.basePair().right(),
              confidence);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    return new CsvTablesResponse(
        canonicalWriter.toString(),
        nonCanonicalWriter.toString(),
        stackingsWriter.toString());
  }

}
