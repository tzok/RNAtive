package pl.poznan.put.api.exception;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String taskId) {
        super("Task not found: " + taskId);
    }
}
