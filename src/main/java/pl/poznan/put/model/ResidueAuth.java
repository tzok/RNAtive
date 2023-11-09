package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableResidueAuth.class)
public interface ResidueAuth {
  String chain();

  int number();

  Optional<String> icode();

  String name();
}
