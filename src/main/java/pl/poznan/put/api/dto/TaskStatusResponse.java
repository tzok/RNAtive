package pl.poznan.put.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import pl.poznan.put.api.model.TaskStatus;

public record TaskStatusResponse(
    String taskId,
    TaskStatus status,
    Instant createdAt,
    String message,
    Map<String, List<String>> removalReasons,
    int currentProgress,
    int totalProgressSteps,
    String progressMessage) {}
