package pl.poznan.put.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Base64;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record FileData(
    String name, String content, boolean isBinary, Optional<String> sequence) {
  // Constructor with default isBinary=false and no sequence
  public FileData(String name, String content) {
    this(name, content, false, Optional.empty());
  }

  // Constructor with specified isBinary and no sequence
  public FileData(String name, String content, boolean isBinary) {
    this(name, content, isBinary, Optional.empty());
  }

  // Helper method to get content as bytes
  @JsonIgnore
  public byte[] getContentBytes() {
    if (isBinary) {
      return Base64.getDecoder().decode(content);
    } else {
      return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  // Method to create a new FileData instance with sequence added
  public FileData withSequence(String sequence) {
    return new FileData(this.name, this.content, this.isBinary, Optional.ofNullable(sequence));
  }
}
