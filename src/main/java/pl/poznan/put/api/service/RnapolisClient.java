package pl.poznan.put.api.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import pl.poznan.put.api.dto.FileData;

@Service
public class RnapolisClient {
  private static final Logger logger = LoggerFactory.getLogger(RnapolisClient.class);
  private static final String RUN_COMMAND_PATH = "/run-command";

  private final RestTemplate restTemplate;
  private final String serviceUrl;

  public RnapolisClient(@Value("${rnapolis.service.url}") String serviceUrl) {
    this.restTemplate = new RestTemplate();
    this.serviceUrl = serviceUrl;
    logger.info("RnapolisClient initialized with service URL: {}", serviceUrl);
  }

  /**
   * Splits a single file into multiple files using RNApolis splitter-wrapper.py.
   *
   * @param fileData The file to split
   * @return List of split files, or a list containing only the original file if splitting fails
   */
  public List<FileData> splitFile(FileData fileData) {
    try {
      logger.info("Splitting file: {}", fileData.name());
      
      // Check if the file is an archive
      if (isArchive(fileData.name())) {
        logger.info("File is an archive, extracting and splitting contents");
        return splitArchive(fileData);
      }

      // Process a single file
      return splitSingleFile(fileData);
    } catch (Exception e) {
      logger.error("Unexpected error during RNApolis splitting", e);
      // Return original file if any error occurred
      return List.of(fileData);
    }
  }
  
  /**
   * Determines if a file is an archive based on its extension.
   *
   * @param filename The filename to check
   * @return true if the file is a zip or tar.gz archive
   */
  private boolean isArchive(String filename) {
    String lowerName = filename.toLowerCase();
    return lowerName.endsWith(".zip") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz");
  }
  
  /**
   * Extracts files from an archive and splits each PDB/CIF file.
   *
   * @param archiveData The archive file data
   * @return List of split files from all PDB/CIF files in the archive
   */
  private List<FileData> splitArchive(FileData archiveData) throws IOException {
    List<FileData> extractedFiles;
    String filename = archiveData.name().toLowerCase();
    
    // Extract files from the archive
    if (filename.endsWith(".zip")) {
      extractedFiles = extractZipArchive(archiveData.content().getBytes(StandardCharsets.UTF_8));
    } else if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")) {
      extractedFiles = extractTarGzArchive(archiveData.content().getBytes(StandardCharsets.UTF_8));
    } else {
      logger.warn("Unsupported archive format: {}", filename);
      return List.of(archiveData);
    }
    
    logger.info("Extracted {} files from archive", extractedFiles.size());
    
    // Filter for PDB and CIF files
    List<FileData> pdbCifFiles = extractedFiles.stream()
        .filter(file -> {
          String name = file.name().toLowerCase();
          return name.endsWith(".pdb") || name.endsWith(".cif");
        })
        .toList();
    
    if (pdbCifFiles.isEmpty()) {
      logger.warn("No PDB or CIF files found in archive");
      return List.of(archiveData);
    }
    
    logger.info("Found {} PDB/CIF files in archive", pdbCifFiles.size());
    
    // Split each PDB/CIF file and collect all results
    List<FileData> allSplitFiles = new ArrayList<>();
    for (FileData pdbFile : pdbCifFiles) {
      List<FileData> splitFiles = splitSingleFile(pdbFile);
      allSplitFiles.addAll(splitFiles);
    }
    
    return allSplitFiles.isEmpty() ? List.of(archiveData) : allSplitFiles;
  }
  
  /**
   * Extracts files from a ZIP archive.
   *
   * @param data The ZIP archive data
   * @return List of extracted files
   */
  private List<FileData> extractZipArchive(byte[] data) throws IOException {
    List<FileData> extractedFiles = new ArrayList<>();
    
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
         java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(bais)) {
      
      java.util.zip.ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int len;
          
          while ((len = zipIn.read(buffer)) != -1) {
            fileContent.write(buffer, 0, len);
          }
          
          String content = fileContent.toString(StandardCharsets.UTF_8);
          String filename = entry.getName();
          
          // Extract just the filename without path
          int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
          if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
          }
          
          if (!filename.isEmpty()) {
            extractedFiles.add(new FileData(filename, content));
          }
        }
        zipIn.closeEntry();
      }
    }
    
    return extractedFiles;
  }
  
  /**
   * Splits a single file using RNApolis splitter-wrapper.py.
   *
   * @param fileData The file to split
   * @return List of split files
   */
  private List<FileData> splitSingleFile(FileData fileData) {
    try {
      // Prepare request
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("arguments", "splitter-wrapper.py");
      body.add("arguments", fileData.name());
      body.add("output_files", "output.tar.gz");

      // Add the input file
      ByteArrayResource fileResource =
          new ByteArrayResource(fileData.content().getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
              return fileData.name();
            }
          };
      body.add("input_files", fileResource);

      HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

      // Execute request
      String url = serviceUrl + RUN_COMMAND_PATH;
      logger.debug("Sending split request to RNApolis service at: {}", url);

      Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);

      // Process response
      if (response != null) {
        logger.debug("Received response from RNApolis service for splitting");

        // Log stdout and stderr
        if (response.containsKey("stdout")) {
          logger.debug("RNApolis stdout: {}", response.get("stdout"));
        }

        if (response.containsKey("stderr")) {
          String stderr = (String) response.get("stderr");
          if (stderr != null && !stderr.isEmpty()) {
            logger.warn("RNApolis stderr: {}", stderr);
          }
        }

        // Check exit code
        Integer exitCode = (Integer) response.get("exit_code");
        if (exitCode != null && exitCode != 0) {
          logger.error("RNApolis split command failed with exit code: {}", exitCode);
          return List.of(fileData);
        }

        // Process output files
        if (response.containsKey("output_files") && response.get("output_files") != null) {
          List<Map<String, String>> outputFiles =
              (List<Map<String, String>>) response.get("output_files");

          for (Map<String, String> file : outputFiles) {
            String relativePath = file.get("relative_path");
            if ("output.tar.gz".equals(relativePath) && file.containsKey("content_base64")) {
              // Decode and extract the tar.gz file
              byte[] decodedData = Base64.getDecoder().decode(file.get("content_base64"));
              List<FileData> extractedFiles = extractTarGzArchive(decodedData);
              // Return original file if no files were extracted
              return extractedFiles.isEmpty() ? List.of(fileData) : extractedFiles;
            }
          }
        }

        logger.warn("No output.tar.gz file found in the response for splitting");
      } else {
        logger.error("Received null response from RNApolis service for splitting");
      }
    } catch (RestClientException e) {
      logger.error("Error communicating with RNApolis service during splitting", e);
    } catch (Exception e) {
      logger.error("Unexpected error during RNApolis splitting", e);
    }

    // Return original file if any error occurred
    return List.of(fileData);
  }

  public List<FileData> processFiles(List<FileData> files) {
    try {
      // Create tar.gz archive with input files
      byte[] tarGzData = createTarGzArchive(files);

      // Prepare request
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("arguments", "unifier-wrapper.py");
      body.add("arguments", "--format");
      body.add("arguments", "PDB");
      body.add("arguments", "input.tar.gz");
      body.add("output_files", "output.tar.gz");

      // Add the tar.gz file
      ByteArrayResource fileResource =
          new ByteArrayResource(tarGzData) {
            @Override
            public String getFilename() {
              return "input.tar.gz";
            }
          };
      body.add("input_files", fileResource);

      HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

      // Execute request
      String url = serviceUrl + RUN_COMMAND_PATH;
      logger.debug("Sending request to RNApolis service at: {}", url);

      Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);

      // Process response
      if (response != null) {
        logger.debug("Received response from RNApolis service");

        // Log stdout and stderr
        if (response.containsKey("stdout")) {
          logger.debug("RNApolis stdout: {}", response.get("stdout"));
        }

        if (response.containsKey("stderr")) {
          String stderr = (String) response.get("stderr");
          if (stderr != null && !stderr.isEmpty()) {
            logger.warn("RNApolis stderr: {}", stderr);
          }
        }

        // Check exit code
        Integer exitCode = (Integer) response.get("exit_code");
        if (exitCode != null && exitCode != 0) {
          logger.error("RNApolis command failed with exit code: {}", exitCode);
          return files;
        }

        // Process output files
        if (response.containsKey("output_files") && response.get("output_files") != null) {
          List<Map<String, String>> outputFiles =
              (List<Map<String, String>>) response.get("output_files");

          for (Map<String, String> file : outputFiles) {
            String relativePath = file.get("relative_path");
            if ("output.tar.gz".equals(relativePath) && file.containsKey("content_base64")) {
              // Decode and extract the tar.gz file
              byte[] decodedData = Base64.getDecoder().decode(file.get("content_base64"));
              List<FileData> extractedFiles = extractTarGzArchive(decodedData);
              // Return original files if no files were extracted
              return extractedFiles.isEmpty() ? files : extractedFiles;
            }
          }
        }

        logger.warn("No output.tar.gz file found in the response");
      } else {
        logger.error("Received null response from RNApolis service");
      }
    } catch (RestClientException e) {
      logger.error("Error communicating with RNApolis service", e);
    } catch (IOException e) {
      logger.error("Error processing tar.gz archive", e);
    } catch (Exception e) {
      logger.error("Unexpected error during RNApolis processing", e);
    }

    // Return original files if any error occurred
    return files;
  }

  private byte[] createTarGzArchive(List<FileData> files) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(baos);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {

      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

      for (FileData file : files) {
        byte[] content = file.content().getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(file.name());
        entry.setSize(content.length);

        tarOut.putArchiveEntry(entry);
        tarOut.write(content);
        tarOut.closeArchiveEntry();
      }
    }

    return baos.toByteArray();
  }

  private List<FileData> extractTarGzArchive(byte[] data) throws IOException {
    List<FileData> extractedFiles = new ArrayList<>();

    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        GzipCompressorInputStream gzIn = new GzipCompressorInputStream(bais);
        TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {

      TarArchiveEntry entry;
      while ((entry = tarIn.getNextTarEntry()) != null) {
        if (!entry.isDirectory()) {
          ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
          byte[] buffer = new byte[1024];
          int len;

          while ((len = tarIn.read(buffer)) != -1) {
            fileContent.write(buffer, 0, len);
          }

          String content = fileContent.toString(StandardCharsets.UTF_8);

          // Remove leading "./" from filename if present
          String filename = entry.getName();
          if (filename.startsWith("./")) {
            filename = filename.substring(2);
          }

          extractedFiles.add(new FileData(filename, content));
        }
      }
    }

    return extractedFiles;
  }
}
