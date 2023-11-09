package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableLoop.class)
public interface Loop {
  List<Strand> strands();
}
