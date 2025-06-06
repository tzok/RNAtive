package pl.poznan.put.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.poznan.put.api.exception.TaskNotFoundException;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.repository.TaskRepository;

@Service
public class TaskProgressPersistenceService {
  private static final Logger logger =
      LoggerFactory.getLogger(TaskProgressPersistenceService.class);
  private final TaskRepository taskRepository;

  @Autowired
  public TaskProgressPersistenceService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistProgressUpdate(
      String taskId, int currentStep, int totalSteps, String progressMessage) {
    Task task =
        taskRepository
            .findById(taskId)
            .orElseThrow(
                () ->
                    new TaskNotFoundException(
                        taskId + " (while attempting to persist progress update)"));
    task.setCurrentProgress(currentStep);
    task.setTotalProgressSteps(totalSteps); // Ensure consistency
    task.setProgressMessage(progressMessage);

    logger.info(
        "Task {} [persistProgressUpdate]: Attempting to persist: currentStep={}, totalSteps={}, message='{}'",
        taskId,
        currentStep,
        totalSteps,
        progressMessage);
    if (totalSteps <= 0) {
      logger.error("Task {} [persistProgressUpdate]: totalSteps is {} during persist. This is problematic for progress display.", taskId, totalSteps);
    }
    // It's possible currentStep was clamped to totalSteps if currentStepCounter > totalSteps.
    // Math.min(currentStepCounterValue, totalSteps) was used to calculate 'currentStep' passed here.
    // So, currentStep should not be > totalSteps unless totalSteps is 0 or negative.

    taskRepository.saveAndFlush(task); // Ensures data hits DB and new transaction commits.
    logger.info(
        "Task {} progress (persisted in new tx): [{}/{}] {}",
        task.getId(),
        task.getCurrentProgress(),
        task.getTotalProgressSteps(),
        progressMessage);
  }
}
