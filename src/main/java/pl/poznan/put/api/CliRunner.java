package pl.poznan.put.api;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pl.poznan.put.Analyzer;
import pl.poznan.put.api.model.MolProbityFilter;

@Component
public class CliRunner implements CommandLineRunner {
    private MolProbityFilter molProbityFilter = MolProbityFilter.GOOD_ONLY; // default value
    private Analyzer analyzer = Analyzer.BPNET; // default value

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
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printHelp();
                    return;
            }
        }

        // Process with selected options
        System.out.println("Processing with MolProbity filter: " + molProbityFilter);
        System.out.println("Using analyzer: " + analyzer);
    }

    private void printHelp() {
        System.out.println("Usage: java -jar application.jar [options]");
        System.out.println("Options:");
        System.out.println("  --help                    Show this help message");
        System.out.println("  --mol-probity <filter>    Set MolProbity filter level");
        System.out.println("                            Valid values: " + String.join(", ", getMolProbityValues()));
        System.out.println("  --analyzer <analyzer>      Set the analyzer to use");
        System.out.println("                            Valid values: " + String.join(", ", getAnalyzerValues()));
    }

    private String[] getMolProbityValues() {
        return new String[]{"GOOD_ONLY", "GOOD_AND_CAUTION", "ALL"};
    }

    private String[] getAnalyzerValues() {
        return new String[]{"BARNABA", "BPNET", "FR3D", "MCANNOTATE", "RNAPOLIS", "RNAVIEW"};
    }
}
