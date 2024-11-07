package pl.poznan.put.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.poznan.put.api.dto.ComputeRequest;
import pl.poznan.put.api.dto.ComputeResponse;
import pl.poznan.put.api.service.ComputeService;

@RestController
@RequestMapping("/api/compute")
public class ComputeController {
  private final ComputeService computeService;

  public ComputeController(ComputeService computeService) {
    this.computeService = computeService;
  }

  @PostMapping
  public ResponseEntity<ComputeResponse> compute(@RequestBody ComputeRequest request) {
    ComputeResponse response = computeService.compute(request);
    return ResponseEntity.ok(response);
  }
}
