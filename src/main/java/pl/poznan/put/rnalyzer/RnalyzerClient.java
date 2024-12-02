package pl.poznan.put.rnalyzer;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import pl.poznan.put.rnalyzer.model.Structure;
import pl.poznan.put.rnalyzer.model.Structures;

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
    LOGGER.trace("Initializing RNAlyzer session");
    ResponseEntity<Void> response = restTemplate.postForEntity(BASE_URL, null, Void.class);
    LOGGER.trace("Received response with status: {}", response.getStatusCode());

    String location = Objects.requireNonNull(response.getHeaders().getLocation()).toString();
    LOGGER.trace("Location header: {}", location);

    Matcher matcher = RESOURCE_ID_PATTERN.matcher(location);
    if (matcher.find()) {
      resourceId = matcher.group(1);
      LOGGER.info("Initialized session with resource ID: {}", resourceId);
    } else {
      LOGGER.error("Failed to extract resource ID from location: {}", location);
      throw new IllegalStateException("Could not extract resource ID from location: " + location);
    }
  }

  public MolProbityResponse analyzePdbContent(String pdbContent, String filename) {
    if (resourceId == null) {
      LOGGER.error("Attempt to analyze PDB content without initialized session");
      throw new IllegalStateException("Session not initialized. Call initializeSession() first.");
    }

    LOGGER.trace("Preparing XML content for PDB analysis");
    Structures structures = new Structures(List.of(new Structure(pdbContent, filename)));
    StringWriter writer = new StringWriter();
    try {
      JAXBContext context = JAXBContext.newInstance(Structures.class);
      Marshaller marshaller = context.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      marshaller.marshal(structures, writer);
    } catch (JAXBException e) {
      throw new RuntimeException("Failed to generate XML content", e);
    }

    String xmlContent = writer.toString();
    LOGGER.trace("XML content length: {} characters", xmlContent.length());
    LOGGER.trace("XML content:\n{}", xmlContent);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_XML);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    LOGGER.trace(
        "Set headers - Content-Type: {}, Accept: {}",
        headers.getContentType(),
        headers.getAccept());

    HttpEntity<String> requestEntity = new HttpEntity<>(xmlContent, headers);
    String url = String.format("%s/%s/molprobity", BASE_URL, resourceId);
    LOGGER.trace("Sending request to URL: {}", url);

    ResponseEntity<MolProbityResponse> response =
        restTemplate.exchange(url, HttpMethod.PUT, requestEntity, MolProbityResponse.class);
    LOGGER.trace("Received response with status: {}", response.getStatusCode());

    MolProbityResponse molProbityResponse = response.getBody();
    if (molProbityResponse != null) {
      LOGGER.trace(
          "Received MolProbity analysis for structure: {}",
          molProbityResponse.structure().description().filename());
    } else {
      LOGGER.warn("Received null response body from MolProbity analysis");
    }

    return molProbityResponse;
  }

  @Override
  public void close() {
    if (resourceId != null) {
      String url = String.format("%s/%s", BASE_URL, resourceId);
      LOGGER.trace("Cleaning up session at URL: {}", url);
      try {
        restTemplate.delete(url);
        LOGGER.info("Cleaned up session with resource ID: {}", resourceId);
      } catch (Exception e) {
        LOGGER.error("Failed to clean up session with resource ID: {}", resourceId, e);
      } finally {
        resourceId = null;
      }
    } else {
      LOGGER.trace("No session to clean up");
    }
  }
}
