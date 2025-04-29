package pl.poznan.put.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
public class Task {
  @Id private String id;

  @Enumerated(EnumType.STRING)
  private TaskStatus status;

  private Instant createdAt;

  @Lob private String request;

  @Lob private String result;

  @Lob private String message;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "model_svgs", joinColumns = @JoinColumn(name = "task_id"))
  @MapKeyColumn(name = "model_name")
  @Column(name = "svg_content", columnDefinition = "TEXT")
  private Map<String, String> modelSvgs = new HashMap<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "removal_reasons", joinColumns = @JoinColumn(name = "task_id"))
  @MapKeyColumn(name = "model_name")
  @Column(name = "reason", length = 1000)
  private Map<String, List<String>> removalReasons = new HashMap<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "molprobity_responses", joinColumns = @JoinColumn(name = "task_id"))
  @MapKeyColumn(name = "model_name")
  @Column(name = "response_json", columnDefinition = "TEXT")
  private Map<String, String> molprobityResponses = new HashMap<>();

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

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Map<String, List<String>> getRemovalReasons() {
    return removalReasons;
  }

  public void addRemovalReason(String modelName, String reason) {
    removalReasons.computeIfAbsent(modelName, k -> new ArrayList<>()).add(reason);
  }

  public Map<String, String> getMolprobityResponses() {
    return molprobityResponses;
  }

  public void addMolProbityResponse(String modelName, String responseJson) {
    molprobityResponses.put(modelName, responseJson);
  }

  public Map<String, String> getModelSvgs() {
    return modelSvgs;
  }

  public void addModelSvg(String modelName, String svgContent) {
    modelSvgs.put(modelName, svgContent);
  }
}
