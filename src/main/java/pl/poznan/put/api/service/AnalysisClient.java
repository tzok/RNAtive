package pl.poznan.put.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pl.poznan.put.Analyzer;

@Service
public class AnalysisClient {
  private static final Logger logger = LoggerFactory.getLogger(AnalysisClient.class);
  private final String baseUrl;
  private final RestClient restClient;

  public AnalysisClient(
      @Value("${analysis.service.host:localhost}") String host,
      @Value("${analysis.service.port:8000}") int port) {
    this.baseUrl = String.format("http://%s:%d/analysis-api/v1", host, port);
    this.restClient = RestClient.create();
  }

  public String analyze(String pdbContent, Analyzer analyzer) {
    logger.info("Analyzing structure with {}", analyzer);
    return restClient
        .post()
        .uri(baseUrl + "/" + analyzer.urlPart())
        .contentType(MediaType.TEXT_PLAIN)
        .body(pdbContent)
        .retrieve()
        .body(String.class);
  }
}
