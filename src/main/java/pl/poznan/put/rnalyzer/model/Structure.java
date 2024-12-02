package pl.poznan.put.rnalyzer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "structure")
@XmlAccessorType(XmlAccessType.FIELD)
public class Structure {
  @XmlElementWrapper(name = "atoms")
  @XmlElement(name = "atom")
  @JsonProperty("atoms")
  private List<String> atoms;

  @XmlElement(name = "filename")
  @JsonProperty("filename")
  private String filename;

  public Structure() {}

  public Structure(List<String> atoms, String filename) {
    this.atoms = atoms;
    this.filename = filename;
  }

  public Structure(String content, String filename) {
    this.atoms = content != null ? List.of(content.split("\n")) : List.of();
    this.filename = filename;
  }

  public List<String> getAtoms() {
    return atoms;
  }

  public void setAtoms(List<String> atoms) {
    this.atoms = atoms;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }
}
