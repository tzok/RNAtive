package pl.poznan.put.api.controller;

import org.springframework.web.bind.annotation.*;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.service.ComputeService;

@RestController
@RequestMapping("/api/compute")
public class ComputeController {
  private final ComputeService computeService;

  public ComputeController(ComputeService computeService) {
    this.computeService = computeService;
  }

  @PostMapping
  public ComputeResponse compute(@RequestBody ComputeRequest request) throws Exception {
    return computeService.submitComputation(request);
  }

  @GetMapping("/{taskId}/status")
  public TaskStatusResponse getStatus(@PathVariable String taskId) {
    return computeService.getTaskStatus(taskId);
  }

  @GetMapping("/{taskId}/result")
  public TaskResultResponse getResult(@PathVariable String taskId) throws Exception {
    return computeService.getTaskResult(taskId);
  }

  @GetMapping(value = "/{taskId}/svg", produces = "image/svg+xml")
  public String getSvg(@PathVariable String taskId) throws Exception {
    return computeService.getTaskSvg(taskId);
  }

  @GetMapping("/{taskId}/file")
  public FileData getFile(@PathVariable String taskId, @RequestParam String filename)
      throws Exception {
    return computeService.getTaskFile(taskId, filename);
  }

  @GetMapping("/{taskId}/model-csv-tables")
  public ModelCsvTablesResponse getModelCsvTables(
      @PathVariable String taskId, @RequestParam String filename) throws Exception {
    return computeService.getModelCsvTables(taskId, filename);
  }
}
