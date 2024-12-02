package pl.poznan.put.rnalyzer.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "structure")
public class Structure {
    @XmlElement(name = "atoms")
    private String atoms;

    public Structure() {
    }

    public Structure(String atoms) {
        this.atoms = atoms;
    }

    public String getAtoms() {
        return atoms;
    }

    public void setAtoms(String atoms) {
        this.atoms = atoms;
    }
}
