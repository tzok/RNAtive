package pl.poznan.put.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.formats.DotBracketFromPdb;

@Service
public class VisualizationService {

  public VisualizationInput prepareVisualizationInput(
      AnalyzedModel model, DotBracketFromPdb dotBracket) {
    PdbModel structure3D = model.structure3D();
    BaseInteractions structure2D = model.structure2D();

    // Group residues by chain
    Map<String, List<PdbResidue>> residuesByChain =
        structure3D.residues().stream().collect(Collectors.groupingBy(PdbResidue::chainIdentifier));

    // Create residues list
    List<Residue> residues = new ArrayList<>();
    List<Chain> chains = new ArrayList<>();

    List<Strand> strands =
        dotBracket.strands().stream()
            .map(strand -> new Strand(strand.name(), strand.sequence(), strand.structure()))
            .toList();

    residuesByChain.forEach(
        (chainId, chainResidues) -> {
          // Create residues for this chain
          List<Residue> chainResiduesList =
              chainResidues.stream()
                  .map(r -> new Residue(r.chainIdentifier(), r.residueNumber(), r.oneLetterName()))
                  .toList();

          residues.addAll(chainResiduesList);
          chains.add(new Chain(chainId, chainResiduesList));
        });

    // Handle non-canonical interactions
    List<NonCanonicalInteraction> nonCanonical =
        model.nonCanonicalBasePairs().stream().map(this::convertToNonCanonicalInteraction).toList();

    NonCanonicalInteractions interactions = new NonCanonicalInteractions(List.of(), nonCanonical);

    return new VisualizationInput(strands, residues, chains, interactions);
  }

  private NonCanonicalInteraction convertToNonCanonicalInteraction(AnalyzedBasePair basePair) {
    var left = basePair.basePair().left();
    var right = basePair.basePair().right();

    return new NonCanonicalInteraction(
        new Residue(left.chainIdentifier(), left.residueNumber(), left.oneLetterName()),
        new Residue(right.chainIdentifier(), right.residueNumber(), right.oneLetterName()),
        basePair.leontisWesthof().toString());
  }
}
