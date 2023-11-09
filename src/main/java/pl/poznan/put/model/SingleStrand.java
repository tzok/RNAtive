package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableSingleStrand.class)
public interface SingleStrand {
  Strand strand();

  boolean is5p();

  boolean is3p();
}
