package pl.poznan.put.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import pl.poznan.put.Analyzer;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.model.MolProbityFilter;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.model.VisualizationTool;
import pl.poznan.put.api.service.ComputeService;

@Component
@ConditionalOnProperty(name = "APP_MODE", havingValue = "cli", matchIfMissing = false)
public class CliRunner implements CommandLineRunner {
  private static final Logger logger = LoggerFactory.getLogger(CliRunner.class);

  private final ComputeService computeService;

  private final Options options;

  @Autowired
  public CliRunner(ComputeService computeService, ConfigurableApplicationContext applicationContext) {
    this.computeService = computeService;
    this.applicationContext = applicationContext;
    this.options = new Options();

    options.addOption(Option.builder("h").longOpt("help").desc("Show this help message").build());

    options.addOption(
        Option.builder("m")
            .longOpt("mol-probity")
            .hasArg()
            .argName("filter")
            .desc("Set MolProbity filter level (GOOD_ONLY, GOOD_AND_CAUTION, ALL)")
            .build());

    options.addOption(
        Option.builder("a")
            .longOpt("analyzer")
            .hasArg()
            .argName("analyzer")
            .desc("Set the analyzer (BARNABA, BPNET, FR3D, MCANNOTATE, RNAPOLIS, RNAVIEW)")
            .build());

    options.addOption(
        Option.builder("c")
            .longOpt("consensus")
            .hasArg()
            .argName("mode")
            .desc("Set consensus mode (CANONICAL, NON_CANONICAL, STACKING, ALL)")
            .build());

    options.addOption(
        Option.builder("l")
            .longOpt("confidence")
            .hasArg()
            .argName("level")
            .desc("Set confidence level (0.0-1.0)")
            .type(Double.class)
            .build());

    options.addOption(
        Option.builder("d")
            .longOpt("dot-bracket")
            .hasArg()
            .argName("structure")
            .desc("Set expected 2D structure in dot-bracket notation")
            .build());

    options.addOption(
        Option.builder("o")
            .longOpt("csv-output")
            .hasArg()
            .argName("file")
            .desc("Save ranking table to CSV file")
            .build());
  }

  @Override
  public void run(String... args) throws Exception {
    if (args.length == 0) {
      printHelp();
      return;
    }

    CommandLineParser parser = new DefaultParser();
    try {
      CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("help")) {
        printHelp();
        return;
      }

      // Get remaining arguments as file paths
      List<String> filePaths = cmd.getArgList();
      if (filePaths.isEmpty()) {
        System.err.println("No input files provided");
        printHelp();
        return;
      }

      // Create file data objects from paths
      List<FileData> files = new ArrayList<>();
      for (String path : filePaths) {
        try {
          String content = Files.readString(Path.of(path));
          String filename = Path.of(path).getFileName().toString();
          files.add(new FileData(filename, content));
        } catch (IOException e) {
          System.err.println("Error reading file " + path + ": " + e.getMessage());
          return;
        }
      }

      // Parse options with validation
      MolProbityFilter molProbityFilter = MolProbityFilter.ALL;
      if (cmd.hasOption("mol-probity")) {
        try {
          molProbityFilter =
              MolProbityFilter.valueOf(cmd.getOptionValue("mol-probity").toUpperCase());
        } catch (IllegalArgumentException e) {
          System.err.println("Invalid MolProbity filter value");
          printHelp();
          return;
        }
      }

      Analyzer analyzer = Analyzer.BPNET;
      if (cmd.hasOption("analyzer")) {
        try {
          analyzer = Analyzer.valueOf(cmd.getOptionValue("analyzer").toUpperCase());
        } catch (IllegalArgumentException e) {
          System.err.println("Invalid analyzer value");
          printHelp();
          return;
        }
      }

      ConsensusMode consensusMode = ConsensusMode.ALL;
      if (cmd.hasOption("consensus")) {
        try {
          consensusMode = ConsensusMode.valueOf(cmd.getOptionValue("consensus").toUpperCase());
        } catch (IllegalArgumentException e) {
          System.err.println("Invalid consensus mode value");
          printHelp();
          return;
        }
      }

      Double confidenceLevel = null;
      if (cmd.hasOption("confidence")) {
        try {
          double value = Double.parseDouble(cmd.getOptionValue("confidence"));
          if (value < 0.0 || value > 1.0) {
            System.err.println("Confidence level must be between 0.0 and 1.0");
            return;
          }
          confidenceLevel = value;
        } catch (NumberFormatException e) {
          System.err.println("Invalid confidence level. Must be a number between 0.0 and 1.0");
          return;
        }
      }

      String dotBracket = cmd.getOptionValue("dot-bracket");
      String csvOutput = cmd.getOptionValue("csv-output");

      // Create compute request
      ComputeRequest request =
          new ComputeRequest(
              files,
              confidenceLevel,
              analyzer,
              consensusMode,
              dotBracket,
              molProbityFilter,
              VisualizationTool.VARNA // Default visualization tool for CLI
              );

      System.out.println("Processing with options:");
      System.out.println("- MolProbity filter: " + request.molProbityFilter());
      System.out.println("- Analyzer: " + request.analyzer());
      System.out.println("- Consensus mode: " + request.consensusMode());
      System.out.println(
          "- Confidence level: "
              + (request.confidenceLevel() != null ? request.confidenceLevel() : "not set"));
      System.out.println(
          "- Dot-bracket structure: "
              + (request.dotBracket() != null ? request.dotBracket() : "not set"));
      System.out.println(
          "- Input files: " + files.stream().map(FileData::name).collect(Collectors.joining(", ")));

      // Submit computation
      ComputeResponse response = computeService.submitComputation(request);
      String taskId = response.taskId();
      System.out.println("Task submitted with ID: " + taskId);

      // Poll for results
      while (true) {
        TaskStatusResponse status = computeService.getTaskStatus(taskId);
        System.out.println("Task status: " + status.status());

        if (status.status() == TaskStatus.COMPLETED) {
          // Get the results
          TablesResponse tables = computeService.getTables(taskId);
          System.out.println("\nResults:");
          System.out.println("Dot-bracket structure: " + tables.dotBracket());
          System.out.println("\nRanking table:");
          printTable(tables.ranking());

          // Write CSV if output path was specified
          if (csvOutput != null) {
            writeTableToCsv(tables.ranking(), csvOutput);
            System.out.println("\nRanking table saved to: " + csvOutput);
          }
          applicationContext.close();
          break;
        } else if (status.status() == TaskStatus.FAILED) {
          System.err.println("Task failed: " + status.message());
          if (!status.removalReasons().isEmpty()) {
            System.err.println("\nModel removal reasons:");
            status
                .removalReasons()
                .forEach(
                    (model, reasons) -> {
                      System.err.println(model + ":");
                      reasons.forEach(reason -> System.err.println("  - " + reason));
                    });
          }
          break;
        }

        // Wait before next poll
        Thread.sleep(1000);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      applicationContext.close();
      System.exit(1);
    }
  }

  private void printHelp() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(100);
    formatter.printHelp(
        "java -jar application.jar [options] <file1> <file2> ...",
        "\nAnalyze RNA structure files and generate consensus information.\n\n",
        options,
        "\nArguments:\n  <file1> <file2> ...  One or more PDB files to analyze\n");
  }

  private void printTable(TableData table) {
    // Print headers
    System.out.println(String.join("\t", table.headers()));

    // Print rows
    for (List<Object> row : table.rows()) {
      System.out.println(row.stream().map(Object::toString).collect(Collectors.joining("\t")));
    }
  }

  private void writeTableToCsv(TableData table, String filePath) throws IOException {
    var csvContent = new StringBuilder();

    // Write headers
    csvContent.append(String.join(",", table.headers())).append("\n");

    // Write rows
    for (List<Object> row : table.rows()) {
      csvContent
          .append(
              row.stream()
                  .map(Object::toString)
                  .map(this::escapeCsvField)
                  .collect(Collectors.joining(",")))
          .append("\n");
    }

    Files.writeString(Path.of(filePath), csvContent.toString());
  }

  private String escapeCsvField(String field) {
    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
      return "\"" + field.replace("\"", "\"\"") + "\"";
    }
    return field;
  }
}
