package pl.poznan.put.api.dto;

import java.time.Instant;
import pl.poznan.put.api.model.TaskStatus;

public record TaskStatusResponse(String taskId, TaskStatus status, Instant createdAt) {}
