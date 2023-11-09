package pl.poznan.put;

import java.util.Comparator;
import org.apache.commons.collections4.Bag;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.ClassifiedBasePair;

public record BasePairComparator(Bag<AnalyzedBasePair> pairs)
    implements Comparator<ClassifiedBasePair> {
  @Override
  public int compare(final ClassifiedBasePair t, final ClassifiedBasePair t1) {
    final int countMine = pairs().getCount(t);
    final int countTheirs = pairs().getCount(t1);
    return countMine == countTheirs ? t.compareTo(t1) : -Integer.compare(countMine, countTheirs);
  }
}
