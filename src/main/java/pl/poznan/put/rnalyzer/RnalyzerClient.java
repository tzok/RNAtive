package pl.poznan.put.rnalyzer;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class RnalyzerClient implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RnalyzerClient.class);
  private static final String BASE_URL = "https://domgen.cs.put.poznan.pl/PUTWSs/services/rnalyzer";
  private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile(".*/([^/]+)$");

  private final RestTemplate restTemplate;
  private String resourceId;

  public RnalyzerClient() {
    this.restTemplate = new RestTemplate();
  }

  public void initializeSession() {
    ResponseEntity<Void> response = restTemplate.postForEntity(BASE_URL, null, Void.class);
    String location = Objects.requireNonNull(response.getHeaders().getLocation()).toString();
    Matcher matcher = RESOURCE_ID_PATTERN.matcher(location);
    if (matcher.find()) {
      resourceId = matcher.group(1);
      LOGGER.info("Initialized session with resource ID: {}", resourceId);
    } else {
      throw new IllegalStateException("Could not extract resource ID from location: " + location);
    }
  }

  public MolProbityResponse analyzePdbContent(String pdbContent) {
    if (resourceId == null) {
      throw new IllegalStateException("Session not initialized. Call initializeSession() first.");
    }

    String xmlContent =
        String.format(
            "<structures><structure><atoms>%s</atoms></structure></structures>", pdbContent);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_XML);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));

    HttpEntity<String> requestEntity = new HttpEntity<>(xmlContent, headers);
    String url = String.format("%s/%s/molprobity", BASE_URL, resourceId);

    ResponseEntity<MolProbityResponse> response =
        restTemplate.exchange(url, HttpMethod.PUT, requestEntity, MolProbityResponse.class);

    return response.getBody();
  }

  @Override
  public void close() {
    if (resourceId != null) {
      String url = String.format("%s/%s", BASE_URL, resourceId);
      restTemplate.delete(url);
      LOGGER.info("Cleaned up session with resource ID: {}", resourceId);
      resourceId = null;
    }
  }
}
