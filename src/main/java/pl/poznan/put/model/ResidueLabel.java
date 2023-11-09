package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableResidueLabel.class)
public interface ResidueLabel {
  String chain();

  int number();

  String name();
}
