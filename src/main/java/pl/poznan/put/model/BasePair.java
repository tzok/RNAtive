package pl.poznan.put.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableBasePair.class)
public interface BasePair extends Interaction {
  Optional<Saenger> saenger();

  default boolean isCanonical() {
    if (lw() == LeontisWesthof.cWW) {
      var sequence =
          Stream.concat(nt1().auth().stream(), nt2().auth().stream())
              .map(ResidueAuth::name)
              .map(String::toUpperCase)
              .sorted()
              .collect(Collectors.joining());
      return "AU".equals(sequence) || "CG".equals(sequence) || "GU".equals(sequence);
    }
    return false;
  }

  LeontisWesthof lw();
}
