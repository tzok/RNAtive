package pl.poznan.put.rnalyzer.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "structures")
public class Structures {
    @XmlElement(name = "structure")
    private List<Structure> structures;

    public Structures() {
    }

    public Structures(List<Structure> structures) {
        this.structures = structures;
    }

    public List<Structure> getStructures() {
        return structures;
    }

    public void setStructures(List<Structure> structures) {
        this.structures = structures;
    }
}
