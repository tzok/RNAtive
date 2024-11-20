package pl.poznan.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import org.apache.commons.cli.*;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import pl.poznan.put.model.BaseInteractions;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.pdb.PdbNamedResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbParser;
import pl.poznan.put.structure.AnalyzedBasePair;
import pl.poznan.put.structure.DotBracketSymbol;
import pl.poznan.put.structure.ImmutableAnalyzedBasePair;
import pl.poznan.put.structure.ImmutableBasePair;
import pl.poznan.put.structure.formats.DefaultDotBracketFromPdb;
import pl.poznan.put.structure.formats.ImmutableDefaultDotBracketFromPdb;
import pl.poznan.put.utility.TabularExporter;

public class App {
  private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

  private final File pdbDirectory;
  private final File jsonDirectory;
  private final File outputDirectory;
  private final ConsensusMode consensusMode;
  private final @Nullable String dotBracket;
  private final double confidenceLevel;

  private final List<AnalyzedModel> analyzedModels = new ArrayList<>();

  private List<PdbNamedResidueIdentifier> residueIdentifiers = Collections.emptyList();
  private String sequence = "";
  private List<AnalyzedBasePair> referenceStructure = Collections.emptyList();
  private Bag<AnalyzedBasePair> canonicalBasePairs = new HashBag<>();
  private Bag<AnalyzedBasePair> nonCanonicalBasePairs = new HashBag<>();
  private Bag<AnalyzedBasePair> stackings = new HashBag<>();
  private Bag<AnalyzedBasePair> basePairsAndStackings = new HashBag<>();
  private int totalModelCount;

  private App(String... args) throws ParseException, IOException {
    super();

    Options helpOptions = OptionsFactory.helpOptions();
    Options mainOptions = OptionsFactory.mainOptions();

    CommandLineParser parser = new DefaultParser();
    CommandLine helpCommandLine = parser.parse(helpOptions, args, true);

    if (helpCommandLine.hasOption(OptionsFactory.HELP.getOpt())) {
      App.printHelp(mainOptions);
      System.exit(0);
    }

    CommandLine commandLine = parser.parse(mainOptions, args);

    pdbDirectory = new File(commandLine.getOptionValue(OptionsFactory.PDB.getOpt()));
    jsonDirectory = new File(commandLine.getOptionValue(OptionsFactory.JSON.getOpt()));
    outputDirectory = new File(commandLine.getOptionValue(OptionsFactory.OUTPUT.getOpt()));
    consensusMode =
        ConsensusMode.valueOf(
            commandLine.getOptionValue(
                OptionsFactory.CONSENSUS_MODE.getOpt(),
                Defaults.DEFAULT_CONSENSUS_MODE.toString()));
    dotBracket = commandLine.getOptionValue(OptionsFactory.DOT_BRACKET.getOpt());
    confidenceLevel =
        Double.parseDouble(
            commandLine.getOptionValue(
                OptionsFactory.CONFIDENCE_LEVEL.getOpt(), Defaults.CONFIDENCE_LEVEL));

    if ((confidenceLevel <= 0) || (confidenceLevel > 1)) {
      App.LOGGER.error("Confidence level must be in range (0, 1]");
      System.exit(1);
    }

    FileUtils.forceMkdir(outputDirectory);
    MDC.put("outputDirectory", outputDirectory.getAbsolutePath());
  }

  private static void printHelp(Options mainOptions) {
    HelpFormatter helpFormatter = new HelpFormatter();
    helpFormatter.printHelp("rnative [OPTIONS] INPUT-1 INPUT-2 ...", mainOptions);
    System.exit(1);
  }

  public static void main(String... args) throws ParseException, IOException {
    App app = new App(args);
    app.run();
  }

  private void run() throws IOException {
    // process all input files
    var array = pdbDirectory.listFiles();
    if (array == null) {
      throw new RuntimeException("Failed to list files in directory: " + pdbDirectory);
    }
    Arrays.stream(array)
        .filter(file -> file.getName().endsWith(".pdb"))
        .forEach(this::processSingleFile);

    // get sequence and residue identifiers from the first model
    residueIdentifiers = analyzedModels.get(0).residueIdentifiers();
    sequence =
        residueIdentifiers.stream()
            .map(PdbNamedResidueIdentifier::oneLetterName)
            .map(String::valueOf)
            .collect(Collectors.joining());

    // check if all models are composed of the same residues
    checkModelsIntegrity();

    // read reference dot-bracket (if any)
    readReferenceSecondaryStructure();

    canonicalBasePairs =
        analyzedModels.stream()
            .map(AnalyzedModel::canonicalBasePairs)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(HashBag::new));
    nonCanonicalBasePairs =
        analyzedModels.stream()
            .map(AnalyzedModel::nonCanonicalBasePairs)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(HashBag::new));
    stackings =
        analyzedModels.stream()
            .map(AnalyzedModel::stackings)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(HashBag::new));
    basePairsAndStackings =
        analyzedModels.stream()
            .map(AnalyzedModel::basePairsAndStackings)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(HashBag::new));
    totalModelCount = analyzedModels.size();

    int threshold = (int) FastMath.ceil(confidenceLevel * totalModelCount);
    writeVerboseResults();
    writeVerboseResultsForEveryModel();
    writeModelRanking(threshold);

    // TODO: is it really needed?
    // writeExtendedSecondaryStructure(threshold);
    // writeBasePairCorrectness(threshold);
  }

  private void processSingleFile(File pdbFile) {
    try {
      var jsonFile = new File(jsonDirectory, pdbFile.getName().replace(".pdb", ".json"));

      if (!jsonFile.exists()) {
        throw new RuntimeException("Failed to find JSON file for PDB file: " + pdbFile.getName());
      }

      var pdbContent = Files.readString(pdbFile.toPath(), Charset.defaultCharset());
      var structure3D = new PdbParser(false).parse(pdbContent);

      if (structure3D.isEmpty()) {
        throw new RuntimeException("Failed to parse PDB file: " + pdbFile.getName());
      }

      var jsonContent = Files.readString(jsonFile.toPath(), Charset.defaultCharset());
      var objectMapper = new ObjectMapper();
      objectMapper.registerModule(new Jdk8Module());
      var structure2D = objectMapper.readValue(jsonContent, BaseInteractions.class);
      analyzedModels.add(new AnalyzedModel(pdbFile.getName(), structure3D.get(0), structure2D));
    } catch (IOException e) {
      throw new RuntimeException("Failed to process file", e);
    }
  }

  private void checkModelsIntegrity() {
    checkDotBracketLength();
    checkResidueComposition();
  }

  private void checkDotBracketLength() {
    if (dotBracket != null && (dotBracket.length() != residueIdentifiers.size())) {
      App.LOGGER.error("Dot-bracket length does not match PDB residue count");
    }
  }

  private void checkResidueComposition() {
    List<AnalyzedModel> invalidModels =
        analyzedModels.stream()
            .filter(
                analyzedModel ->
                    !CollectionUtils.isEqualCollection(
                        residueIdentifiers, analyzedModel.residueIdentifiers()))
            .toList();
    if (!invalidModels.isEmpty()) {
      invalidModels.forEach(
          analyzedModel ->
              App.LOGGER.error(
                  "Model {} has different residue composition than the first model",
                  analyzedModel.name()));
    }
  }

  /**
   * Read reference secondary structure from dot-bracket notation. By default it will read all pairs
   * and assume they are canonical.
   */
  private void readReferenceSecondaryStructure() {
    if (dotBracket != null) {
      AnalyzedModel firstModel = analyzedModels.get(0);
      DefaultDotBracketFromPdb structure =
          ImmutableDefaultDotBracketFromPdb.of(sequence, dotBracket, firstModel.structure3D());

      referenceStructure =
          structure.pairs().keySet().stream()
              .map(
                  symbol -> {
                    DotBracketSymbol paired = structure.pairs().get(symbol);
                    PdbNamedResidueIdentifier left =
                        firstModel
                            .findResidue(structure.identifier(symbol))
                            .namedResidueIdentifier();
                    PdbNamedResidueIdentifier right =
                        firstModel
                            .findResidue(structure.identifier(paired))
                            .namedResidueIdentifier();
                    return ImmutableBasePair.of(left, right);
                  })
              .map(ImmutableAnalyzedBasePair::of)
              .collect(Collectors.toList());
    }
  }

  private void writeVerboseResults() throws IOException {
    try (OutputStream stream =
        new FileOutputStream(new File(outputDirectory, "confidence-table-canonical.csv"))) {
      TableModel tableModel = basePairConfidenceTable(canonicalBasePairs.uniqueSet());
      TabularExporter.export(tableModel, stream);
    }
    try (OutputStream stream =
        new FileOutputStream(new File(outputDirectory, "confidence-table-non-canonical.csv"))) {
      TableModel tableModel = basePairConfidenceTable(nonCanonicalBasePairs.uniqueSet());
      TabularExporter.export(tableModel, stream);
    }
    try (OutputStream stream =
        new FileOutputStream(new File(outputDirectory, "confidence-table-stackings.csv"))) {
      TableModel tableModel = stackingConfidenceTable(stackings.uniqueSet());
      TabularExporter.export(tableModel, stream);
    }
  }

  private void writeVerboseResultsForEveryModel() throws IOException {
    for (AnalyzedModel analyzedModel : analyzedModels) {
      try (OutputStream stream =
          new FileOutputStream(
              new File(
                  outputDirectory,
                  "confidence-table-canonical-" + analyzedModel.name() + ".csv"))) {
        TableModel tableModel = basePairConfidenceTable(analyzedModel.canonicalBasePairs());
        TabularExporter.export(tableModel, stream);
      }
      try (OutputStream stream =
          new FileOutputStream(
              new File(
                  outputDirectory,
                  "confidence-table-non-canonical-" + analyzedModel.name() + ".csv"))) {
        TableModel tableModel = basePairConfidenceTable(analyzedModel.nonCanonicalBasePairs());
        TabularExporter.export(tableModel, stream);
      }
      try (OutputStream stream =
          new FileOutputStream(
              new File(
                  outputDirectory,
                  "confidence-table-stackings-" + analyzedModel.name() + ".csv"))) {
        TableModel tableModel = stackingConfidenceTable(analyzedModel.stackings());
        TabularExporter.export(tableModel, stream);
      }
    }
  }

  private TableModel basePairConfidenceTable(
      Collection<? extends AnalyzedBasePair> consideredPairs) {
    Object[][] data =
        consideredPairs.stream()
            .sorted(new BasePairComparator(basePairsAndStackings))
            .map(
                classifiedBasePair ->
                    new Object[] {
                      classifiedBasePair.basePair().left(),
                      classifiedBasePair.basePair().right(),
                      classifiedBasePair.leontisWesthof(),
                      String.format(Locale.US, "%.3f", basePairConfidence(classifiedBasePair)),
                      referenceStructure.contains(classifiedBasePair)
                    })
            .toArray(Object[][]::new);
    Object[] columnNames =
        new String[] {"Nt1", "Nt2", "Leontis-Westhof", "Confidence", "Is reference?"};
    return new DefaultTableModel(data, columnNames);
  }

  private TableModel stackingConfidenceTable(
      Collection<? extends AnalyzedBasePair> consideredPairs) {
    Object[][] data =
        consideredPairs.stream()
            .sorted(new BasePairComparator(basePairsAndStackings))
            .map(
                classifiedBasePair ->
                    new Object[] {
                      classifiedBasePair.basePair().left(),
                      classifiedBasePair.basePair().right(),
                      String.format(Locale.US, "%.3f", basePairConfidence(classifiedBasePair))
                    })
            .toArray(Object[][]::new);
    Object[] columnNames = new String[] {"Nt1", "Nt2", "Confidence"};
    return new DefaultTableModel(data, columnNames);
  }

  /* TODO: is it really needed?

  private void writeExtendedSecondaryStructure( int threshold) throws IOException {
     Set<AnalyzedBasePair> correctBasePairs = correctBasePairs(threshold);

    // check for multi-chain
     long distinctChains =
        correctBasePairs.stream()
            .map(AnalyzedBasePair::basePair)
            .flatMap(basePair -> Stream.of(basePair.left(), basePair.right()))
            .map(PdbNamedResidueIdentifier::chainIdentifier)
            .distinct()
            .count();
    if (distinctChains > 1) {
      App.LOGGER.warn(
          "Currently unable to write or visualize extended secondary structure of a multi-chain RNA");
      return;
    }

     MultiLineDotBracket multiLineDotBracket =
        ImmutableMultiLineDotBracket.of(sequence, correctBasePairs);
     String output = multiLineDotBracket.toString();

    // write in graphic format
     File visualizationFile = new File(outputDirectory, String.format("visualization-%02.0f.svg", (100.0 * threshold) / totalModelCount));
    ExtendedSecondaryStructureDrawer.draw(Arrays.asList(StringUtils.split(output, '\n')), visualizationFile);

    // write in graphic format (the reference structure)
    if (!referenceStructure.isEmpty()) {
         File referenceFile = new File(outputDirectory, String.format("reference-%02.0f.svg", (100.0 * threshold) / totalModelCount));
        ExtendedSecondaryStructureDrawer.draw(Arrays.asList(StringUtils.split(ImmutableMultiLineDotBracket.of(sequence, referenceStructure).toString(), '\n')), referenceFile);
    }

    // write in text format
     File outputFile =
        new File(
            outputDirectory,
            String.format("extended-%02.0f.txt", (100.0 * threshold) / totalModelCount));
    FileUtils.write(outputFile, output, StandardCharsets.UTF_8);
  }*/

  /* TODO: is it really needed?
    private void writeBasePairCorrectness( int threshold) throws IOException {
      try ( OutputStream stream =
          new FileOutputStream(
              new File(
                  outputDirectory,
                  String.format("base-pairs-%d-out-of-%d-ie-%02.0f.csv", threshold, totalModelCount, (100.0 * threshold) / totalModelCount)))) {
         TableModel tableModel = basePairCorrectnessTable(threshold);
        TabularExporter.export(tableModel, stream);
      }
    }
  */

  private void writeModelRanking(int threshold) throws IOException {
    try (OutputStream stream = new FileOutputStream(new File(outputDirectory, "ranking.csv"))) {
      TableModel tableModel = modelRankingTable(threshold);
      TabularExporter.export(tableModel, stream);
    }
  }

  private TableModel modelRankingTable(int threshold) {
    Set<AnalyzedBasePair> correct = correctInteractions(threshold);

    List<RankedModel> rankedModels =
        analyzedModels.stream()
            .map(analyzedModel -> rankModel(analyzedModel, correct))
            .sorted(Comparator.reverseOrder())
            .toList();

    List<Double> infs =
        rankedModels.stream().map(RankedModel::getInteractionNetworkFidelity).toList();
    rankedModels.forEach(
        rankedModel ->
            rankedModel.setRank(infs.indexOf(rankedModel.getInteractionNetworkFidelity()) + 1));

    Object[][] data =
        rankedModels.stream()
            .map(
                rankedModel ->
                    new Object[] {
                      rankedModel.getRank(),
                      rankedModel.getName(),
                      String.format(Locale.US, "%.3f", rankedModel.getInteractionNetworkFidelity())
                    })
            .toArray(Object[][]::new);
    Object[] columnNames = new String[] {"Rank", "File name", "INF"};
    return new DefaultTableModel(data, columnNames);
  }

  private RankedModel rankModel(
      AnalyzedModel analyzedModel, Set<AnalyzedBasePair> referenceInteractions) {
    Set<AnalyzedBasePair> modelInteractions =
        analyzedModel.streamBasePairs(consensusMode).collect(Collectors.toSet());
    double interactionNetworkFidelity =
        InteractionNetworkFidelity.calculate(referenceInteractions, modelInteractions);
    return new RankedModel(analyzedModel, interactionNetworkFidelity);
  }

  private Set<AnalyzedBasePair> correctInteractions(int threshold) {
    var allInteractions =
        switch (consensusMode) {
          case CANONICAL -> canonicalBasePairs;
          case NON_CANONICAL -> nonCanonicalBasePairs;
          case STACKING -> stackings;
          case ALL -> basePairsAndStackings;
        };

    Set<AnalyzedBasePair> candidates =
        allInteractions.stream()
            .filter(
                classifiedBasePair ->
                    referenceStructure.contains(classifiedBasePair)
                        || allInteractions.getCount(classifiedBasePair) >= threshold)
            .collect(Collectors.toSet());

    if (consensusMode != ConsensusMode.STACKING) {
      for (LeontisWesthof leontisWesthof : LeontisWesthof.values()) {
        List<AnalyzedBasePair> conflicting = conflictingBasePairs(candidates, leontisWesthof);

        while (!conflicting.isEmpty()) {
          candidates.remove(conflicting.get(0));
          conflicting = conflictingBasePairs(candidates, leontisWesthof);
        }
      }
    }

    return candidates;
  }

  private List<AnalyzedBasePair> conflictingBasePairs(
      Set<AnalyzedBasePair> candidates, LeontisWesthof leontisWesthof) {
    MultiValuedMap<PdbNamedResidueIdentifier, AnalyzedBasePair> map =
        new ArrayListValuedHashMap<>();

    candidates.stream()
        .filter(candidate -> candidate.leontisWesthof() == leontisWesthof)
        .forEach(
            candidate -> {
              pl.poznan.put.structure.BasePair basePair = candidate.basePair();
              map.put(basePair.left(), candidate);
              map.put(basePair.right(), candidate);
            });

    return map.keySet().stream()
        .filter(key -> map.get(key).size() > 1)
        .flatMap(key -> map.get(key).stream())
        .distinct()
        .sorted(Comparator.comparingInt(t -> basePairsAndStackings.getCount(t)))
        .collect(Collectors.toList());
  }

  /* TODO: is it really needed?
    private TableModel basePairCorrectnessTable( int threshold) {
       Set<AnalyzedBasePair> correctBasePairs = correctBasePairs(threshold);
       Set<PdbNamedResidueIdentifier> canonicalCorrect =
          App.distinctResidues(correctBasePairs, ConsensusMode.CANONICAL);
       Set<PdbNamedResidueIdentifier> nonCanonicalCorrect =
          App.distinctResidues(correctBasePairs, ConsensusMode.NON_CANONICAL);
       Set<PdbNamedResidueIdentifier> stackingCorrect =
          App.distinctResidues(correctBasePairs, ConsensusMode.STACKING);

       Object[][] data =
          analyzedModels.stream()
              .flatMap(
                  analyzedModel ->
                      App.streamResidueCorrectness(
                          analyzedModel, canonicalCorrect, nonCanonicalCorrect, stackingCorrect))
              .toArray(Object[][]::new);
       Object[] columnNames =
          new String[] {
            "File name", "Model number", "Residue", "In canonical", "In non-canonical", "In stacking"
          };
      return new DefaultTableModel(data, columnNames);
    }
  */

  private double basePairConfidence(AnalyzedBasePair basePair) {
    return basePairsAndStackings.getCount(basePair) / (double) totalModelCount;
  }
}
