package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableBasePhosphate.class)
public interface BasePhosphate extends Interaction {
  Optional<String> bph();
}
