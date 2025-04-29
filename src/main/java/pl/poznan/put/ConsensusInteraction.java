package pl.poznan.put;

import java.util.Optional;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;

/**
 * Represents an interaction (base pair or stacking) identified during consensus analysis.
 *
 * @param partner1 The identifier of the first residue in the interaction.
 * @param partner2 The identifier of the second residue in the interaction.
 * @param category The main category of the interaction (BASE_PAIR or STACKING).
 * @param leontisWesthof Optional detailed classification (Leontis-Westhof) for base pairs.
 * @param modelCount The number of models in which this interaction was observed.
 * @param isReference Whether this interaction is part of the provided reference structure.
 */
public record ConsensusInteraction(
    PdbNamedResidueIdentifier partner1,
    PdbNamedResidueIdentifier partner2,
    InteractionCategory category,
    Optional<LeontisWesthof> leontisWesthof,
    int modelCount,
    boolean isReference) {

  /** Defines the main categories of interactions considered. */
  public enum InteractionCategory {
    BASE_PAIR,
    STACKING
  }
}
