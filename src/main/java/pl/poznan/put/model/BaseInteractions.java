package pl.poznan.put.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableBaseInteractions.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface BaseInteractions {
  List<BasePair> basePairs();

  List<Stacking> stackings();

  List<BaseRibose> baseRiboseInteractions();

  List<BasePhosphate> basePhosphateInteractions();

  List<OtherInteraction> otherInteractions();
}
