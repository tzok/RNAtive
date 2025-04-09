package pl.poznan.put.api.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.structure.*;
import pl.poznan.put.structure.formats.DefaultDotBracket;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;

public class ReferenceStructureUtil {
  public record ReferenceParseResult(
      List<BasePair> basePairs, List<PdbNamedResidueIdentifier> markedResidues) {}

  public static ReferenceParseResult readReferenceStructure(
      String dotBracketInput, AnalyzedModel model) {
    if (dotBracketInput == null || dotBracketInput.isBlank()) {
      return new ReferenceParseResult(Collections.emptyList(), Collections.emptyList());
    }

    // Replace 'x' or 'X' with '-' which signifies a missing residue in DotBracketSymbol
    String modifiedDotBracketInput = dotBracketInput.replace('x', '-').replace('X', '-');

    // DefaultDotBracket.fromString handles the multi-line format
    var dotBracketObj = DefaultDotBracket.fromString(modifiedDotBracketInput);
    int modelResidueCount = model.structure3D().residues().size();

    if (dotBracketObj.sequence().length() != modelResidueCount) {
      throw new InvalidSequenceLengthException(
          modelResidueCount, dotBracketObj.sequence().length());
    }

    String modelSequence =
        model.structure3D().residues().stream()
            .map(PdbResidue::oneLetterName)
            .map(String::valueOf)
            .map(String::toUpperCase)
            .collect(Collectors.joining());

    if (!dotBracketObj.sequence().equalsIgnoreCase(modelSequence)) {
      throw new InvalidSequenceException(modelSequence, dotBracketObj.sequence());
    }

    var structure =
        ImmutableDefaultDotBracketFromPdb.of(
            dotBracketObj.sequence(), dotBracketObj.structure(), model.structure3D());

    List<BasePair> basePairs =
        structure.pairs().keySet().stream()
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

    List<PdbNamedResidueIdentifier> markedResidues =
        structure.symbols().stream()
            .filter(DotBracketSymbol::isMissing)
            .map(
                dotBracketSymbol ->
                    model
                        .findResidue(structure.identifier(dotBracketSymbol))
                        .namedResidueIdentifier())
            .toList();

    return new ReferenceParseResult(basePairs, markedResidues);
  }
}
