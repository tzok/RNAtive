package pl.poznan.put.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.poznan.put.api.dto.*;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.model.VisualizationTool;
import pl.poznan.put.api.service.ComputeService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import pl.poznan.put.Analyzer;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.api.model.MolProbityFilter;
import pl.poznan.put.api.repository.TaskRepository;
import pl.poznan.put.api.service.AnalysisClient;
import pl.poznan.put.api.service.ConversionClient;
import pl.poznan.put.api.service.VisualizationClient;
import pl.poznan.put.api.service.VisualizationService;
import pl.poznan.put.api.util.DrawerVarnaTz;

@Component
@ConditionalOnProperty(name = "APP_MODE", havingValue = "cli", matchIfMissing = false)
public class CliRunner implements CommandLineRunner {
  private static final Logger logger = LoggerFactory.getLogger(CliRunner.class);

  private final ComputeService computeService;

  private MolProbityFilter molProbityFilter = MolProbityFilter.GOOD_ONLY; // default value
  private Analyzer analyzer = Analyzer.BPNET; // default value
  private ConsensusMode consensusMode = ConsensusMode.CANONICAL; // default value
  private Double confidenceLevel = null; // default value
  private String dotBracket = null; // default value
  private String csvOutput = null; // default value

  @Autowired
  public CliRunner(ComputeService computeService) {
    this.computeService = computeService;
  }

  @Override
  public void run(String... args) throws Exception {
    if (args.length == 0) {
      System.out.println("No arguments provided. Use --help for usage information.");
      return;
    }

    List<String> filePaths = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--help":
          printHelp();
          return;
        case "--mol-probity":
          if (i + 1 < args.length) {
            try {
              molProbityFilter = MolProbityFilter.valueOf(args[i + 1].toUpperCase());
              i++; // skip the next argument since we consumed it
            } catch (IllegalArgumentException e) {
              System.err.println(
                  "Invalid MolProbity filter value. Valid values are: "
                      + String.join(", ", getMolProbityValues()));
              return;
            }
          } else {
            System.err.println("--mol-probity requires a value");
            return;
          }
          break;
        case "--analyzer":
          if (i + 1 < args.length) {
            try {
              analyzer = Analyzer.valueOf(args[i + 1].toUpperCase());
              i++; // skip the next argument since we consumed it
            } catch (IllegalArgumentException e) {
              System.err.println(
                  "Invalid analyzer value. Valid values are: "
                      + String.join(", ", getAnalyzerValues()));
              return;
            }
          } else {
            System.err.println("--analyzer requires a value");
            return;
          }
          break;
        case "--consensus":
          if (i + 1 < args.length) {
            try {
              consensusMode = ConsensusMode.valueOf(args[i + 1].toUpperCase());
              i++; // skip the next argument since we consumed it
            } catch (IllegalArgumentException e) {
              System.err.println(
                  "Invalid consensus mode value. Valid values are: "
                      + String.join(", ", getConsensusModeValues()));
              return;
            }
          } else {
            System.err.println("--consensus requires a value");
            return;
          }
          break;
        case "--confidence":
          if (i + 1 < args.length) {
            try {
              double value = Double.parseDouble(args[i + 1]);
              if (value < 0.0 || value > 1.0) {
                System.err.println("Confidence level must be between 0.0 and 1.0");
                return;
              }
              confidenceLevel = value;
              i++; // skip the next argument since we consumed it
            } catch (NumberFormatException e) {
              System.err.println("Invalid confidence level. Must be a number between 0.0 and 1.0");
              return;
            }
          } else {
            System.err.println("--confidence requires a value");
            return;
          }
          break;
        case "--dot-bracket":
          if (i + 1 < args.length) {
            dotBracket = args[i + 1];
            i++; // skip the next argument since we consumed it
          } else {
            System.err.println("--dot-bracket requires a value");
            return;
          }
          break;
        case "--csv-output":
          if (i + 1 < args.length) {
            csvOutput = args[i + 1];
            i++; // skip the next argument since we consumed it
          } else {
            System.err.println("--csv-output requires a value");
            return;
          }
          break;
        default:
          System.err.println("Unknown option: " + args[i]);
          printHelp();
          return;
      }
    }

    // Collect remaining arguments as file paths
    for (; i < args.length; i++) {
      if (args[i].startsWith("--")) {
        System.err.println("Unexpected option after files: " + args[i]);
        printHelp();
        return;
      }
      filePaths.add(args[i]);
    }

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

    try {
      // Create compute request
      ComputeRequest request = new ComputeRequest(
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
      System.out.println("- Confidence level: " + (request.confidenceLevel() != null ? request.confidenceLevel() : "not set"));
      System.out.println("- Dot-bracket structure: " + (request.dotBracket() != null ? request.dotBracket() : "not set"));
      System.out.println("- Input files: " + files.stream().map(FileData::name).collect(Collectors.joining(", ")));

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
          printTable(tables.rankingTable());
          
          // Write CSV if output path was specified
          if (csvOutput != null) {
            writeTableToCsv(tables.rankingTable(), csvOutput);
            System.out.println("\nRanking table saved to: " + csvOutput);
          }
          break;
        } else if (status.status() == TaskStatus.FAILED) {
          System.err.println("Task failed: " + status.message());
          if (!status.removalReasons().isEmpty()) {
            System.err.println("\nModel removal reasons:");
            status.removalReasons().forEach((model, reasons) -> {
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
      System.exit(1);
    }
  }

  private void printHelp() {
    System.out.println("Usage: java -jar application.jar [options] <file1> <file2> ...");
    System.out.println("Options:");
    System.out.println("  --help                    Show this help message");
    System.out.println("  --mol-probity <filter>    Set MolProbity filter level");
    System.out.println(
        "                            Valid values: " + String.join(", ", getMolProbityValues()));
    System.out.println("  --analyzer <analyzer>      Set the analyzer to use");
    System.out.println(
        "                            Valid values: " + String.join(", ", getAnalyzerValues()));
    System.out.println("  --consensus <mode>         Set the consensus mode");
    System.out.println(
        "                            Valid values: " + String.join(", ", getConsensusModeValues()));
    System.out.println("  --confidence <level>       Set the confidence level (0.0-1.0)");
    System.out.println("                            Optional, default: not set");
    System.out.println(
        "  --dot-bracket <structure>  Set the expected 2D structure in dot-bracket notation");
    System.out.println("                            Optional, default: not set");
    System.out.println("");
    System.out.println("  --csv-output <file>       Save ranking table to CSV file");
    System.out.println("                            Optional, default: not set");
    System.out.println("");
    System.out.println("Arguments:");
    System.out.println("  <file1> <file2> ...       One or more PDB files to analyze");
  }

  private String[] getMolProbityValues() {
    return new String[] {"GOOD_ONLY", "GOOD_AND_CAUTION", "ALL"};
  }

  private String[] getAnalyzerValues() {
    return new String[] {"BARNABA", "BPNET", "FR3D", "MCANNOTATE", "RNAPOLIS", "RNAVIEW"};
  }

  private String[] getConsensusModeValues() {
    return new String[] {"CANONICAL", "NON_CANONICAL", "STACKING", "ALL"};
  }
}
  private void printTable(TableData table) {
    // Print headers
    System.out.println(String.join("\t", table.headers()));
    
    // Print rows
    for (List<Object> row : table.rows()) {
      System.out.println(row.stream()
          .map(Object::toString)
          .collect(Collectors.joining("\t")));
    }
  }

  private void writeTableToCsv(TableData table, String filePath) throws IOException {
    var csvContent = new StringBuilder();
    
    // Write headers
    csvContent.append(String.join(",", table.headers())).append("\n");
    
    // Write rows
    for (List<Object> row : table.rows()) {
      csvContent.append(
          row.stream()
              .map(Object::toString)
              .map(this::escapeCsvField)
              .collect(Collectors.joining(","))
      ).append("\n");
    }
    
    Files.writeString(Path.of(filePath), csvContent.toString());
  }

  private String escapeCsvField(String field) {
    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
      return "\"" + field.replace("\"", "\"\"") + "\"";
    }
    return field;
  }
