package pl.poznan.put.api;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CliRunner implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("No arguments provided. Use --help for usage information.");
            return;
        }

        if (args[0].equals("--help")) {
            printHelp();
            return;
        }

        // TODO: Add your CLI logic here
        System.out.println("Processing arguments: " + String.join(" ", args));
    }

    private void printHelp() {
        System.out.println("Usage: java -jar application.jar [options]");
        System.out.println("Options:");
        System.out.println("  --help     Show this help message");
        // TODO: Add more options documentation
    }
}
