package pl.poznan.put.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableStructure2D.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Structure2D {
  List<BasePair> basePairs();

  List<Stacking> stackings();

  List<BaseRibose> baseRiboseInteractions();

  List<BasePhosphate> basePhosphateInteractions();

  List<OtherInteraction> otherInteraction();

  Optional<String> bpseq();

  Optional<String> dotBracket();

  Optional<String> extendedDotBracket();

  List<Stem> stems();

  List<SingleStrand> singleStrands();

  List<Hairpin> hairpins();

  List<Loop> loops();
}
