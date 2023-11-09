package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableStem.class)
public interface Stem {
  Strand strand5p();

  Strand strand3p();
}
