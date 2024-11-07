package pl.poznan.put.api.dto;

import pl.poznan.put.api.model.TaskStatus;
import java.time.Instant;

public record TaskStatusResponse(
    String taskId,
    TaskStatus status,
    Instant createdAt
) {}
