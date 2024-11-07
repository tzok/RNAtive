package pl.poznan.put.api.service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import pl.poznan.put.RankedModel;
import pl.poznan.put.structure.AnalyzedBasePair;

@Service
public class CsvGenerationService {
  private static final String[] PAIR_HEADERS = {
    "Nt1", "Nt2", "Leontis-Westhof", "Confidence", "Is reference?"
  };
  private static final String[] STACKING_HEADERS = {"Nt1", "Nt2", "Confidence"};
  private static final String[] RANKING_HEADERS = {"Rank", "File name", "INF"};

  public String generateRankingCsv(List<RankedModel> models) {
    StringWriter writer = new StringWriter();
    try (CSVPrinter printer =
        new CSVPrinter(writer, CSVFormat.Builder.create().setHeader(RANKING_HEADERS).build())) {
      models.forEach(
          model -> {
            try {
              printer.printRecord(
                  model.getRank(),
                  model.getName(),
                  String.format("%.3f", model.getInteractionNetworkFidelity()));
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  public String generatePairsCsv(
      List<? extends AnalyzedBasePair> pairs,
      HashBag<AnalyzedBasePair> allInteractions,
      int totalModelCount,
      List<AnalyzedBasePair> referenceStructure) {
    StringWriter writer = new StringWriter();
    try (CSVPrinter printer =
        new CSVPrinter(writer, CSVFormat.Builder.create().setHeader(PAIR_HEADERS).build())) {
      pairs.forEach(
          pair -> {
            try {
              double confidence = allInteractions.getCount(pair) / (double) totalModelCount;
              printer.printRecord(
                  pair.basePair().left(),
                  pair.basePair().right(),
                  pair.leontisWesthof(),
                  String.format("%.3f", confidence),
                  referenceStructure.contains(pair));
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  public String generateStackingsCsv(
      List<? extends AnalyzedBasePair> stackings,
      HashBag<AnalyzedBasePair> allInteractions,
      int totalModelCount) {
    StringWriter writer = new StringWriter();
    try (CSVPrinter printer =
        new CSVPrinter(writer, CSVFormat.Builder.create().setHeader(STACKING_HEADERS).build())) {
      stackings.forEach(
          stacking -> {
            try {
              double confidence = allInteractions.getCount(stacking) / (double) totalModelCount;
              printer.printRecord(
                  stacking.basePair().left(),
                  stacking.basePair().right(),
                  String.format("%.3f", confidence));
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }
}
