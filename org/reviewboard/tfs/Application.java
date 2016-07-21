package org.reviewboard.tfs;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;

import com.microsoft.tfs.jni.loader.NativeLoader;
import com.microsoft.tfs.jni.PlatformMiscUtils;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.reviewboard.tfs.Command;
import org.reviewboard.tfs.CommandDiff;
import org.reviewboard.tfs.CommandGetCollection;
import org.reviewboard.tfs.CommandParseRevision;


/**
 * The main application.
 */
public class Application {
    private static final HashMap<String, Command> commandClasses;
    static {
        commandClasses = new HashMap<String, Command>();
        commandClasses.put("diff", new CommandDiff());
        commandClasses.put("get-collection", new CommandGetCollection());
        commandClasses.put("parse-revision", new CommandParseRevision());
    }

    /**
     * Main function.
     *
     * This is responsible for parsing the command line and determining which
     * Command subclass to run.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) throws IOException, URISyntaxException {
        /*
         * Set up the path to the TFS SDK native libraries based on the current
         * jar filename.
         */
        final Path currentJar = Paths.get(Application.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final Path nativeDir = currentJar.resolve("../lib/native").toRealPath();
        System.setProperty(NativeLoader.NATIVE_LIBRARY_BASE_DIRECTORY_PROPERTY, nativeDir.toString());

        /*
         * Argument parsing happens in two passes. For the first pass, we add
         * global options (like --debug) and all other items get collected into
         * the args list. The first item of that list is then used as the
         * command name, which is used to look up which command to run. That
         * command then can add its own options, and we do another parse with
         * the new set of options.
         */
        final Options options = new Options();
        options.addOption(Option.builder()
            .longOpt("debug")
            .desc("Enable debug output.")
            .build());

        final CommandLineParser parser = new DefaultParser();
        String commandName = null;
        Command command = null;

        try {
            CommandLine commandLine = parser.parse(options, args, true);

            String[] capturedArgs = commandLine.getArgs();
            if (capturedArgs.length == 0 ||
                (capturedArgs.length == 1 && capturedArgs[0].equals("help"))) {
                showHelp(null, "[command] [options]", options, true);
                System.exit(0);
            }

            commandName = capturedArgs[0];
            command = commandClasses.get(commandName);
            if (command == null) {
                System.err.println("Unknown command \"" + commandName + "\"");
                showHelp(null, "[command] [options]", options, true);
                System.exit(1);
            }

            for (Option option : command.getOptions()) {
                options.addOption(option);
            }

            args = Arrays.copyOfRange(capturedArgs, 1, capturedArgs.length);
            commandLine = parser.parse(options, args);

            if (commandLine.hasOption("debug")) {
                Logger.getRootLogger().setLevel(Level.INFO);
            }

            command.run(commandLine);
        } catch(final ParseException e) {
            if (e.getMessage() != null) {
                System.err.println(e);
            }

            showHelp(commandName, command != null ? command.getUsage() : null,
                     options, command == null);
            System.exit(1);
        }
    }

    /**
     * Print usage information to the console.
     *
     * @param commandName  The name of the command being run (may be null).
     * @param args         Usage information.
     * @param options      The options list.
     * @param listCommands Whether to show a list of the available commands.
     */
    private static void showHelp(final String commandName, final String args,
                                 final Options options, final boolean listCommands) {
        String usage = "rb-tfs";

        if (commandName != null) {
            usage += " " + commandName;
        }

        if (args != null) {
            usage += " " + args;
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(usage, options);

        if (listCommands) {
            System.out.println();
            System.out.println("Available commands:");

            for (String command : commandClasses.keySet()) {
                System.out.println("    " + command);
            }
        }
    }

    /**
     * Returns a boolean environment variable.
     *
     * This mirrors the logic used in the Team Explorer Everywhere command-line
     * client.
     *
     * @param  envVar       The name of the variable.
     * @param  defaultValue The default value if the variable is unset.
     * @return              Whether the environment variable is set to
     *                      something truthy.
     */
    public static final boolean getBooleanEnvVar(final String envVar, final boolean defaultValue) {
        final String value = PlatformMiscUtils.getInstance().getEnvironmentVariable(envVar);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }

        return (!value.equalsIgnoreCase("FALSE") &&
                !value.equalsIgnoreCase("NO") &&
                !value.equalsIgnoreCase("N"));
    }
}
