package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
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
import org.w3c.dom.svg.SVGDocument;
import pl.poznan.put.api.exception.VisualizationException;
import pl.poznan.put.varna.model.StructureData;

@Service
public class VarnaTzClient {
  private static final Logger logger = LoggerFactory.getLogger(VarnaTzClient.class);
  private static final String RUN_COMMAND_PATH = "/run-command";

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String serviceUrl;

  public VarnaTzClient(
      @Value("${varna-tz.service.url}") String serviceUrl, ObjectMapper objectMapper) {
    this.restTemplate = new RestTemplate();
    this.objectMapper = objectMapper;
    this.serviceUrl = serviceUrl;
    logger.info("VarnaTzClient initialized with service URL: {}", serviceUrl);
  }

  /**
   * Visualizes the given structure data using the varna-tz service.
   *
   * @param structureData The structure data to visualize.
   * @return An SVGDocument representing the visualization.
   * @throws VisualizationException If the visualization process fails.
   */
  public SVGDocument visualize(StructureData structureData) throws VisualizationException {
    try {
      // Serialize StructureData to JSON
      String jsonInput = objectMapper.writeValueAsString(structureData);
      logger.debug("Serialized StructureData to JSON: {}", jsonInput);

      // Prepare request
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("arguments", "wrapper.sh");
      body.add("arguments", "input.json");
      body.add("output_files", "clean.svg");

      // Add the JSON input file
      ByteArrayResource jsonResource =
          new ByteArrayResource(jsonInput.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
              return "input.json";
            }
          };
      body.add("input_files", jsonResource);

      HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

      // Execute request
      String url = serviceUrl + RUN_COMMAND_PATH;
      logger.debug("Sending visualization request to Varna-TZ service at: {}", url);

      Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);

      // Process response
      if (response != null) {
        logger.debug("Received response from Varna-TZ service");

        // Log stdout and stderr
        logStdOutErr(response);

        // Check exit code
        Integer exitCode = (Integer) response.get("exit_code");
        if (exitCode != null && exitCode != 0) {
          String stderr = (String) response.getOrDefault("stderr", "No stderr provided.");
          throw new VisualizationException(
              String.format(
                  "Varna-TZ command failed with exit code: %d. Stderr: %s", exitCode, stderr),
              null);
        }

        // Process output files
        if (response.containsKey("output_files") && response.get("output_files") != null) {
          List<Map<String, String>> outputFiles =
              (List<Map<String, String>>) response.get("output_files");

          for (Map<String, String> file : outputFiles) {
            String relativePath = file.get("relative_path");
            if ("clean.svg".equals(relativePath) && file.containsKey("content_base64")) {
              // Decode Base64 SVG content
              byte[] decodedData = Base64.getDecoder().decode(file.get("content_base64"));
              String svgContent = new String(decodedData, StandardCharsets.UTF_8);
              logger.debug(
                  "Received SVG content (first 100 chars): {}",
                  svgContent.substring(0, Math.min(100, svgContent.length())));

              // Parse SVG string into SVGDocument
              return parseSvgContent(svgContent);
            }
          }
        }
        throw new VisualizationException("No output.svg file found in the Varna-TZ response", null);
      } else {
        throw new VisualizationException("Received null response from Varna-TZ service", null);
      }
    } catch (RestClientException e) {
      logger.error("Error communicating with Varna-TZ service", e);
      throw new VisualizationException("Error communicating with Varna-TZ service", e);
    } catch (IOException e) {
      logger.error("Error processing JSON or SVG content", e);
      throw new VisualizationException("Error processing JSON or SVG content", e);
    } catch (Exception e) {
      logger.error("Unexpected error during Varna-TZ visualization", e);
      throw new VisualizationException("Unexpected error during Varna-TZ visualization", e);
    }
  }

  private void logStdOutErr(Map<String, Object> response) {
    if (response.containsKey("stdout")) {
      String stdout = (String) response.get("stdout");
      if (stdout != null && !stdout.trim().isEmpty()) {
        logger.debug("Varna-TZ stdout: {}", stdout);
      }
    }
    if (response.containsKey("stderr")) {
      String stderr = (String) response.get("stderr");
      if (stderr != null && !stderr.trim().isEmpty()) {
        logger.warn("Varna-TZ stderr: {}", stderr);
      }
    }
  }

  private SVGDocument parseSvgContent(String svgContent) throws IOException {
    String parser = XMLResourceDescriptor.getXMLParserClassName();
    SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
    try (StringReader reader = new StringReader(svgContent)) {
      return (SVGDocument) factory.createDocument(null, reader);
    }
  }
}
