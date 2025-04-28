package pl.poznan.put.api.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.exception.ResourceNotFoundException;
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
  public String getSvg(@PathVariable String taskId) {
    try {
      return computeService.getTaskSvg(taskId);
    } catch (ResourceNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
  }

  @GetMapping("/{taskId}/request")
  public String getRequest(@PathVariable String taskId) {
    return computeService.getTaskRequest(taskId);
  }

  @GetMapping("/{taskId}/molprobity")
  public java.util.Map<String, String> getMolProbityResponses(@PathVariable String taskId) {
    return computeService.getTaskMolProbityResponses(taskId);
  }

  @PostMapping(value = "/split", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SplitFileResponse splitFile(@RequestParam("file") MultipartFile file) {
    logger.info("Received file splitting request for file: {}", file.getOriginalFilename());

    try {
      // Get filename
      String filename = file.getOriginalFilename();
      if (filename == null || filename.isEmpty()) {
        filename = "input_file";
      }

      // Check if file is binary (zip, tar.gz, etc)
      boolean isBinary =
          filename.toLowerCase().endsWith(".zip")
              || filename.toLowerCase().endsWith(".tar.gz")
              || filename.toLowerCase().endsWith(".tgz");

      FileData fileData;
      if (isBinary) {
        // For binary files, use Base64 encoding
        String content = Base64.getEncoder().encodeToString(file.getBytes());
        fileData = new FileData(filename, content, true);
        logger.info("Processing binary file: {}, size: {} bytes", filename, file.getSize());
      } else {
        // For text files, use UTF-8
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        fileData = new FileData(filename, content, false);
      }

      // Log file type information
      logger.info("Processing file: {}, size: {} bytes", filename, file.getSize());
      if (filename.toLowerCase().endsWith(".zip")
          || filename.toLowerCase().endsWith(".tar.gz")
          || filename.toLowerCase().endsWith(".tgz")) {
        logger.info("Detected archive file: {}", filename);
      }

      // Use RnapolisClient to split the file (now handles archives)
      List<FileData> splitFiles = computeService.splitFile(fileData);
      logger.info("Split operation returned {} files", splitFiles.size());

      return new SplitFileResponse(splitFiles);
    } catch (IOException e) {
      logger.error("Error reading uploaded file", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Error reading uploaded file: " + e.getMessage(), e);
    } catch (Exception e) {
      logger.error("Error splitting file", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error splitting file: " + e.getMessage(), e);
    }
  }
}
