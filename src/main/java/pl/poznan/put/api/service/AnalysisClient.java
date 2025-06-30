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

  public AnalysisClient(@Value("${analysis.service.url}") String serviceUrl) {
    this.baseUrl = serviceUrl + "/analysis-api/v1";
    this.restClient = RestClient.create();
  }

  public String analyze(String filename, String pdbContent, Analyzer analyzer) {
    logger.info("Analyzing {} structure with {}", filename, analyzer);
    return restClient
        .post()
        .uri(baseUrl + "/" + analyzer.urlPart())
        .contentType(MediaType.TEXT_PLAIN)
        .body(pdbContent)
        .retrieve()
        .body(String.class);
  }
}
