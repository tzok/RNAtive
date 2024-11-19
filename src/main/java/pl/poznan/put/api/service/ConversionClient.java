package pl.poznan.put.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ConversionClient {
  private static final Logger logger = LoggerFactory.getLogger(ConversionClient.class);
  private final RestTemplate restTemplate;
  private final String conversionApiUrl;

  public ConversionClient(
      RestTemplate restTemplate,
      @Value("${conversion.api.url:http://localhost:8000}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.conversionApiUrl = baseUrl + "/conversion-api/v1/bpseq2dbn";
  }

  public String convertBpseqToDotBracket(String bpseq) {
    logger.debug("Converting BPSEQ to dot-bracket notation");
    return restTemplate.postForObject(conversionApiUrl, bpseq, String.class);
  }
}
