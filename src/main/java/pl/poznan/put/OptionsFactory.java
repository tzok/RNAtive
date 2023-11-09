package pl.poznan.put;

import java.util.Arrays;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public final class OptionsFactory {
  public static final Option PDB =
      Option.builder("p")
          .longOpt("pdb-input-directory")
          .desc("(required) path where input PDB files are stored")
          .required()
          .hasArg()
          .build();
  public static final Option JSON =
      Option.builder("j")
          .longOpt("json-input-directory")
          .desc("(required) path where input JSON files (results from RNApdbee 3) are stored")
          .required()
          .hasArg()
          .build();
  public static final Option OUTPUT =
      Option.builder("o")
          .longOpt("output-directory")
          .desc("(required) path where output files will be stored")
          .required()
          .hasArg()
          .build();
  public static final Option CONSENSUS_MODE =
      Option.builder("m")
          .longOpt("consensus-mode")
          .desc(
              String.format(
                  "mode of INF calculation, one of %s, default is %s",
                  Arrays.toString(ConsensusMode.values()), Defaults.DEFAULT_CONSENSUS_MODE))
          .hasArg()
          .build();
  public static final Option DOT_BRACKET =
      Option.builder("d")
          .longOpt("dot-bracket")
          .desc("(optional) reference dot-bracket structure if known")
          .hasArg()
          .build();
  public static final Option CONFIDENCE_LEVEL =
      Option.builder("l")
          .longOpt("confidence-level")
          .desc(
              String.format(
                  "(optional) confidence level (0-1] used to distinguish valid from invalid base"
                      + " pairs. Default: %s",
                  Defaults.CONFIDENCE_LEVEL))
          .hasArg()
          .build();
  public static final Option HELP =
      Option.builder("h")
          .longOpt("help")
          .desc("print help (this information)")
          .hasArg(false)
          .build();

  private OptionsFactory() {
    super();
  }

  public static Options mainOptions() {
    final Options options = new Options();
    options.addOption(OptionsFactory.PDB);
    options.addOption(OptionsFactory.JSON);
    options.addOption(OptionsFactory.OUTPUT);
    options.addOption(OptionsFactory.CONSENSUS_MODE);
    options.addOption(OptionsFactory.DOT_BRACKET);
    options.addOption(OptionsFactory.CONFIDENCE_LEVEL);
    options.addOption(OptionsFactory.HELP);
    return options;
  }

  public static Options helpOptions() {
    final Options options = new Options();
    options.addOption(OptionsFactory.HELP);
    return options;
  }
}
