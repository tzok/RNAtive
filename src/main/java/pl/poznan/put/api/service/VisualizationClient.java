package pl.poznan.put.api.service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.poznan.put.api.model.VisualizationTool;

@Service
public class VisualizationClient {
  private static final Logger logger = LoggerFactory.getLogger(VisualizationClient.class);
  private final String baseUrl;
  private final RestClient restClient;

  public VisualizationClient(
      @Value("${analysis.service.host:localhost}") String host,
      @Value("${analysis.service.port:8000}") int port) {
    this.baseUrl = String.format("http://%s:%d/visualization-api/v1", host, port);
    this.restClient = RestClient.create();
  }

  public String visualize(String jsonContent, VisualizationTool tool) throws IOException {
    logger.info("Generating visualization using {}", tool);
    String endpoint =
        switch (tool) {
          case PSEUDOVIEWER -> "pseudoviewer";
          case RCHIE -> "rchie";
          case RNAPUZZLER -> "rnapuzzler";
          case VARNA -> throw new UnsupportedOperationException(
              "VARNA visualization not supported");
        };

    java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/debug.json"), jsonContent);

    return restClient
        .post()
        .uri(baseUrl + "/" + endpoint)
        .contentType(MediaType.APPLICATION_JSON)
        .body(jsonContent)
        .retrieve()
        .body(String.class);
  }
}
