package org.reviewboard.tfs;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.reviewboard.tfs.Command;


/**
 * A command which prints the current collection URL.
 */
public class CommandGetCollection extends Command {
    private static Log log = LogFactory.getLog(CommandGetCollection.class);

    /**
     * Returns a string to use when printing usage information.
     *
     * @return Usage information.
     */
    public String getUsage() {
        return "";
    }

    /**
     * Run the command.
     *
     * @param  commandLine    Command-line arguments.
     * @throws ParseException An error parsing the command line.
     */
    public void run(CommandLine commandLine) throws ParseException {
        try(final TFSCollection collection = getCollection(commandLine)) {
            System.out.println(collection.getBaseURI());
        }
    }
}
