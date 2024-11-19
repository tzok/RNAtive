package pl.poznan.put.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.service.ComputeService;

@RestController
@RequestMapping("/api/compute")
public class ComputeController {
  private static final Logger logger = LoggerFactory.getLogger(ComputeController.class);
  private final ComputeService computeService;

  public ComputeController(ComputeService computeService) {
    this.computeService = computeService;
  }

  @PostMapping
  public ComputeResponse compute(@RequestBody ComputeRequest request) throws Exception {
    logger.info("Received computation request");
    return computeService.submitComputation(request);
  }

  @GetMapping("/{taskId}/status")
  public TaskStatusResponse getStatus(@PathVariable String taskId) {
    logger.debug("Checking status for task {}", taskId);
    return computeService.getTaskStatus(taskId);
  }

  @GetMapping("/{taskId}/result")
  public TablesResponse getResult(@PathVariable String taskId) throws Exception {
    return computeService.getTables(taskId);
  }

  @GetMapping("/{taskId}/result/{filename}")
  public ModelTablesResponse getModelTables(
      @PathVariable String taskId, @PathVariable String filename) throws Exception {
    return computeService.getModelTables(taskId, filename);
  }

  @GetMapping(value = "/{taskId}/svg", produces = "image/svg+xml")
  public String getSvg(@PathVariable String taskId) throws Exception {
    return computeService.getTaskSvg(taskId);
  }
}
