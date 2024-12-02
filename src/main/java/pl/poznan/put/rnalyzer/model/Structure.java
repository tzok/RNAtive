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

    public Structure() {
    }

    public Structure(List<String> atoms) {
        this.atoms = atoms;
    }

    public Structure(String content) {
        this.atoms = content != null ? 
            List.of(content.split("\n")) :
            List.of();
    }

    public List<String> getAtoms() {
        return atoms;
    }

    public void setAtoms(List<String> atoms) {
        this.atoms = atoms;
    }
}
