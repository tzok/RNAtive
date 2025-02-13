package pl.poznan.put.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
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
public class CliRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(CliRunner.class);
    
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final AnalysisClient analysisClient;
    private final VisualizationClient visualizationClient;
    private final DrawerVarnaTz drawerVarnaTz;
    private final VisualizationService visualizationService;
    private final ConversionClient conversionClient;

    private MolProbityFilter molProbityFilter = MolProbityFilter.GOOD_ONLY; // default value
    private Analyzer analyzer = Analyzer.BPNET; // default value
    private ConsensusMode consensusMode = ConsensusMode.CANONICAL; // default value

    @Autowired
    public CliRunner(
            TaskRepository taskRepository,
            ObjectMapper objectMapper,
            AnalysisClient analysisClient,
            VisualizationClient visualizationClient,
            VisualizationService visualizationService,
            ConversionClient conversionClient,
            DrawerVarnaTz drawerVarnaTz) {
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.analysisClient = analysisClient;
        this.visualizationClient = visualizationClient;
        this.visualizationService = visualizationService;
        this.conversionClient = conversionClient;
        this.drawerVarnaTz = drawerVarnaTz;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("No arguments provided. Use --help for usage information.");
            return;
        }

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
                            System.err.println("Invalid MolProbity filter value. Valid values are: " +
                                String.join(", ", getMolProbityValues()));
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
                            System.err.println("Invalid analyzer value. Valid values are: " +
                                String.join(", ", getAnalyzerValues()));
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
                            System.err.println("Invalid consensus mode value. Valid values are: " +
                                String.join(", ", getConsensusModeValues()));
                            return;
                        }
                    } else {
                        System.err.println("--consensus requires a value");
                        return;
                    }
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printHelp();
                    return;
            }
        }

        // Process with selected options
        System.out.println("Processing with MolProbity filter: " + molProbityFilter);
        System.out.println("Using analyzer: " + analyzer);
        System.out.println("Using consensus mode: " + consensusMode);
    }

    private void printHelp() {
        System.out.println("Usage: java -jar application.jar [options]");
        System.out.println("Options:");
        System.out.println("  --help                    Show this help message");
        System.out.println("  --mol-probity <filter>    Set MolProbity filter level");
        System.out.println("                            Valid values: " + String.join(", ", getMolProbityValues()));
        System.out.println("  --analyzer <analyzer>      Set the analyzer to use");
        System.out.println("                            Valid values: " + String.join(", ", getAnalyzerValues()));
        System.out.println("  --consensus <mode>         Set the consensus mode");
        System.out.println("                            Valid values: " + String.join(", ", getConsensusModeValues()));
    }

    private String[] getMolProbityValues() {
        return new String[]{"GOOD_ONLY", "GOOD_AND_CAUTION", "ALL"};
    }

    private String[] getAnalyzerValues() {
        return new String[]{"BARNABA", "BPNET", "FR3D", "MCANNOTATE", "RNAPOLIS", "RNAVIEW"};
    }

    private String[] getConsensusModeValues() {
        return new String[]{"CANONICAL", "NON_CANONICAL", "STACKING", "ALL"};
    }
}
