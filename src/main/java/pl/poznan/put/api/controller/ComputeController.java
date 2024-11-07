package pl.poznan.put.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.poznan.put.api.dto.ComputeRequest;
import pl.poznan.put.api.dto.ComputeResponse;
import pl.poznan.put.api.dto.TaskResultResponse;
import pl.poznan.put.api.dto.TaskStatusResponse;
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
}
