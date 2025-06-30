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
  private final String serviceUrl;

  public ConversionClient(
      RestTemplate restTemplate, @Value("${analysis.service.url}") String serviceUrl) {
    this.restTemplate = restTemplate;
    this.serviceUrl = serviceUrl + "/conversion-api/v1/bpseq2dbn";
  }

  public String convertBpseqToDotBracket(String bpseq) {
    logger.debug("Converting BPSEQ to dot-bracket notation");
    var headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
    var request = new org.springframework.http.HttpEntity<>(bpseq, headers);
    return restTemplate.postForObject(serviceUrl, request, String.class);
  }
}
