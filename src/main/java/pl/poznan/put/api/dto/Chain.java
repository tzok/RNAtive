package pl.poznan.put.api.dto;

import java.util.List;

public record Chain(String name, List<Residue> residues) {}
