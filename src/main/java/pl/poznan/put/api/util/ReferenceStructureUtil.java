package pl.poznan.put.api.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.DotBracketSymbol;
import pl.poznan.put.structure.ImmutableAnalyzedBasePair;
import pl.poznan.put.structure.ImmutableBasePair;
import pl.poznan.put.structure.formats.DefaultDotBracketFromPdb;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;

public class ReferenceStructureUtil {
  public static List<AnalyzedBasePair> readReferenceStructure(
      String dotBracket, String sequence, AnalyzedModel model) {
    if (dotBracket == null || dotBracket.isEmpty()) {
      return Collections.emptyList();
    }

    DefaultDotBracketFromPdb structure =
        ImmutableDefaultDotBracketFromPdb.of(sequence, dotBracket, model.structure3D());

    return structure.pairs().keySet().stream()
        .map(
            symbol -> {
              DotBracketSymbol paired = structure.pairs().get(symbol);
              PdbNamedResidueIdentifier left =
                  model.findResidue(structure.identifier(symbol)).namedResidueIdentifier();
              PdbNamedResidueIdentifier right =
                  model.findResidue(structure.identifier(paired)).namedResidueIdentifier();
              return ImmutableBasePair.of(left, right);
            })
        .map(ImmutableAnalyzedBasePair::of)
        .collect(Collectors.toList());
  }
}
