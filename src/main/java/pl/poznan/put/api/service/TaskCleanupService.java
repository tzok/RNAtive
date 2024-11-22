package pl.poznan.put.api.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.poznan.put.api.repository.TaskRepository;

@Service
public class TaskCleanupService {
  private final TaskRepository taskRepository;

  public TaskCleanupService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Scheduled(cron = "0 0 0 * * ?") // Every day at midnight
  public void cleanUpOldTasks() {
    Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
    taskRepository.deleteTasksOlderThan(cutoff);
  }
}
