package pl.poznan.put.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Task {
  @Id private String id;

  @Enumerated(EnumType.STRING)
  private TaskStatus status;

  private Instant createdAt;

  @Lob private String request;

  @Lob private String result;

  @Lob private String svg;
  
  @Lob private String message;

  public Task() {
    this.id = UUID.randomUUID().toString();
    this.status = TaskStatus.PENDING;
    this.createdAt = Instant.now();
  }

  // Getters and setters
  public String getId() {
    return id;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getRequest() {
    return request;
  }

  public void setRequest(String request) {
    this.request = request;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getSvg() {
    return svg;
  }

  public void setSvg(String svg) {
    this.svg = svg;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
