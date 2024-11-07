package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.poznan.put.api.dto.ComputeRequest;
import pl.poznan.put.api.dto.ComputeResponse;
import pl.poznan.put.api.dto.TaskResultResponse;
import pl.poznan.put.api.dto.TaskStatusResponse;
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
}
