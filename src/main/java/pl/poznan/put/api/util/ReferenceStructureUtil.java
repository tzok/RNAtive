package pl.poznan.put.api.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.structure.*;
import pl.poznan.put.structure.formats.DefaultDotBracket;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;

public class ReferenceStructureUtil {
  public static List<BasePair> readReferenceStructure(String dotBracket, AnalyzedModel model) {
    if (dotBracket == null || dotBracket.isEmpty()) {
      return Collections.emptyList();
    }

    var dotBracketObj = DefaultDotBracket.fromString(dotBracket);
    int modelResidueCount = model.structure3D().residues().size();

    if (dotBracketObj.sequence().length() != modelResidueCount) {
      throw new InvalidSequenceLengthException(
          modelResidueCount, dotBracketObj.sequence().length());
    }

    var structure =
        ImmutableDefaultDotBracketFromPdb.of(
            dotBracketObj.sequence(), dotBracketObj.structure(), model.structure3D());

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
        .collect(Collectors.toList());
  }
}
