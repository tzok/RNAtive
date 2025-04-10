package pl.poznan.put.api.service;

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
import pl.poznan.put.api.model.FileData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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

    public List<FileData> processFiles(List<FileData> files) {
        try {
            // Create tar.gz archive with input files
            byte[] tarGzData = createTarGzArchive(files);
            
            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("arguments", "[\"unifier-wrapper.py\", \"--format\", \"PDB\", \"input.tar.gz\"]");
            body.add("output_files", "[\"output.tar.gz\"]");
            
            // Add the tar.gz file
            ByteArrayResource fileResource = new ByteArrayResource(tarGzData) {
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
            
            Map<String, Object> response = restTemplate.postForObject(
                url, 
                requestEntity, 
                Map.class
            );
            
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
                    List<Map<String, String>> outputFiles = (List<Map<String, String>>) response.get("output_files");
                    
                    for (Map<String, String> file : outputFiles) {
                        String relativePath = file.get("relative_path");
                        if ("output.tar.gz".equals(relativePath) && file.containsKey("content_base64")) {
                            // Decode and extract the tar.gz file
                            byte[] decodedData = Base64.getDecoder().decode(file.get("content_base64"));
                            return extractTarGzArchive(decodedData);
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
                    extractedFiles.add(new FileData(entry.getName(), content));
                }
            }
        }
        
        return extractedFiles;
    }
}
