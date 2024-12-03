package pl.poznan.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import pl.poznan.put.model.*;
import pl.poznan.put.pdb.ChainNumberICode;
import pl.poznan.put.pdb.ImmutablePdbResidueIdentifier;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.rna.InteractionType;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.ImmutableAnalyzedBasePair;
import pl.poznan.put.structure.ImmutableBasePair;

public record AnalyzedModel(String name, PdbModel structure3D, BaseInteractions structure2D) {
  public Stream<AnalyzedBasePair> streamBasePairs(final ConsensusMode mode) {
    return switch (mode) {
      case CANONICAL -> canonicalBasePairs().stream();
      case NON_CANONICAL -> nonCanonicalBasePairs().stream();
      case STACKING -> stackings().stream();
      case ALL -> basePairsAndStackings().stream();
    };
  }

  public Set<PdbNamedResidueIdentifier> distinctResidues(final ConsensusMode mode) {
    return streamBasePairs(mode)
        .map(AnalyzedBasePair::basePair)
        .flatMap(basePair -> Stream.of(basePair.left(), basePair.right()))
        .collect(Collectors.toSet());
  }

  public List<AnalyzedBasePair> basePairsAndStackings() {
    var result = new ArrayList<>(canonicalBasePairs());
    result.addAll(nonCanonicalBasePairs());
    result.addAll(stackings());
    return result;
  }

  public List<AnalyzedBasePair> canonicalBasePairs() {
    return structure2D.basePairs().stream()
        .filter(BasePair::isCanonical)
        .map(this::basePairToAnalyzed)
        .collect(Collectors.toList());
  }

  public List<AnalyzedBasePair> nonCanonicalBasePairs() {
    return structure2D.basePairs().stream()
        .filter(basePair -> !basePair.isCanonical())
        .map(this::basePairToAnalyzed)
        .collect(Collectors.toList());
  }

  public List<AnalyzedBasePair> stackings() {
    return structure2D.stackings().stream()
        .map(this::stackingToAnalyzed)
        .collect(Collectors.toList());
  }

  public AnalyzedBasePair basePairToAnalyzed(BasePair basePair) {
    return ImmutableAnalyzedBasePair.builder()
        .basePair(
            ImmutableBasePair.of(
                residueToNamedIdentifier(basePair.nt1()), residueToNamedIdentifier(basePair.nt2())))
        .interactionType(InteractionType.BASE_BASE)
        .leontisWesthof(basePair.lw().toBioCommons())
        .saenger(
            basePair.saenger().stream()
                .map(Saenger::toBioCommons)
                .findFirst()
                .orElse(pl.poznan.put.notation.Saenger.UNKNOWN))
        .build();
  }

  public AnalyzedBasePair stackingToAnalyzed(Stacking stacking) {
    return ImmutableAnalyzedBasePair.builder()
        .basePair(
            ImmutableBasePair.of(
                residueToNamedIdentifier(stacking.nt1()), residueToNamedIdentifier(stacking.nt2())))
        .interactionType(InteractionType.STACKING)
        .build();
  }

  private PdbNamedResidueIdentifier residueToNamedIdentifier(Residue residue) {
    assert residue.auth().isPresent();
    var auth = residue.auth().orElseThrow();
    var identifier =
        ImmutablePdbResidueIdentifier.builder()
            .chainIdentifier(auth.chain())
            .residueNumber(auth.number())
            .insertionCode(auth.icode())
            .build();
    return findResidue(identifier).namedResidueIdentifier();
  }

  public PdbResidue findResidue(ChainNumberICode query) {
    return structure3D().findResidue(query);
  }

  public int modelNumber() {
    return structure3D().modelNumber();
  }

  public List<PdbNamedResidueIdentifier> residueIdentifiers() {
    return structure3D().namedResidueIdentifiers();
  }
}
