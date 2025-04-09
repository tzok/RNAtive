package pl.poznan.put.api.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import pl.poznan.put.AnalyzedModel;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.structure.*;
import java.util.ArrayList;
import java.util.stream.IntStream;
import pl.poznan.put.structure.formats.DefaultDotBracket;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;

public class ReferenceStructureUtil {

  public record ReferenceParseResult(
      List<BasePair> basePairs, List<PdbNamedResidueIdentifier> markedResidues) {}

  public static ReferenceParseResult readReferenceStructure(String dotBracketInput, AnalyzedModel model) {
    if (dotBracketInput == null || dotBracketInput.isBlank()) {
      return new ReferenceParseResult(Collections.emptyList(), Collections.emptyList());
    }

    String[] lines = dotBracketInput.trim().split("\\R", 2); // Split into max 2 lines
    String sequenceLine = lines[0];
    String structureLine = (lines.length > 1) ? lines[1] : ""; // Use second line if present

    List<Integer> xIndices = new ArrayList<>();
    StringBuilder modifiedStructureLineBuilder = new StringBuilder();
    for (int i = 0; i < structureLine.length(); i++) {
      char c = structureLine.charAt(i);
      if (c == 'x' || c == 'X') {
        xIndices.add(i);
        modifiedStructureLineBuilder.append('.'); // Replace 'x' with '.' for parsing
      } else {
        modifiedStructureLineBuilder.append(c);
      }
    }
    String modifiedStructureLine = modifiedStructureLineBuilder.toString();

    // Use sequence and modified structure for parsing
    String dotBracketStringForParsing = sequenceLine + "\n" + modifiedStructureLine;
    var dotBracketObj = DefaultDotBracket.fromString(dotBracketStringForParsing);
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

    List<BasePair> basePairs = structure.pairs().keySet().stream()
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
        xIndices.stream()
            .map(index -> structure.symbols().get(index))
            .map(symbol -> model.findResidue(structure.identifier(symbol)).namedResidueIdentifier())
            .collect(Collectors.toList());

    return new ReferenceParseResult(basePairs, markedResidues);
  }
}
