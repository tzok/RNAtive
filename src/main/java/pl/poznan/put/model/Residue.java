package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableResidue.class)
public interface Residue {
  Optional<ResidueLabel> label();

  Optional<ResidueAuth> auth();
}
