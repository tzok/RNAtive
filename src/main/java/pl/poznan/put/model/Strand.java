package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableStrand.class)
public interface Strand {
  int first();

  int last();

  String sequence();

  String structure();
}
