package org.reviewboard.tfs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import com.microsoft.tfs.core.clients.versioncontrol.VersionControlClient;
import com.microsoft.tfs.core.clients.versioncontrol.path.ServerPath;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.PendingSet;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.RecursionType;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.Shelveset;
import com.microsoft.tfs.core.clients.versioncontrol.specs.ItemSpec;
import com.microsoft.tfs.core.clients.versioncontrol.workspacecache.WorkspaceInfo;
import com.microsoft.tfs.core.exceptions.TECoreException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.reviewboard.tfs.Command;
import org.reviewboard.tfs.TFSCollection;
import org.reviewboard.tfs.TFSDiffer;


/**
 * A command to perfom a diff.
 */
public class CommandDiff extends Command {
    private static Log log = LogFactory.getLog(CommandDiff.class);
    private CommandLine commandLine;

    /**
     * Returns the command-line arguments that this command accepts.
     *
     * @return Options for the command-line parsing.
     */
    public Option[] getOptions() {
        return ArrayUtils.addAll(super.getOptions(), new Option[]{
            Option.builder("h")
                .longOpt("help")
                .desc("Show help.")
                .build(),
            Option.builder()
                .longOpt("shelveset-owner")
                .desc("Look up the shelveset created by the given owner.")
                .hasArg()
                .argName("owner")
                .build()
        });
    }

    /**
     * Returns a string to use when printing usage information.
     *
     * @return Usage information.
     */
    public String getUsage() {
        return "[options] revision1 revision2";
    }

    /**
     * Run the command.
     *
     * @param  commandLine    Command-line arguments.
     * @throws ParseException An error parsing the command line.
     */
    public void run(CommandLine commandLine) throws ParseException {
        final String[] args = commandLine.getArgs();

        if (args.length != 2) {
            throw new ParseException("diff command requires specifying revisions");
        }

        final String base = args[0];
        final String tip = args[1];

        try(final TFSCollection collection = getCollection(commandLine)) {
            TFSDiffer.DiffResult diffResult = null;

            if (tip.startsWith(Revision.SHELVESET_PREFIX)) {
                final String shelvesetName = tip.substring(Revision.SHELVESET_PREFIX.length());
                final String ownerName =
                    commandLine.hasOption("shelveset-owner")
                        ? commandLine.getOptionValue("shelveset-owner")
                        : collection.workspace.getOwnerName();

                diffResult = getShelvesetDiff(collection, shelvesetName, ownerName);
            } else if (tip.equals(Revision.WORKING_COPY)) {
                diffResult = getWorkingCopyDiff(collection);
            } else {
                diffResult = getCommittedChangesetsDiff(collection, base, tip);
            }

            if (diffResult.diff != null) {
                IOUtils.write(diffResult.diff, System.out);
            }

            if (diffResult.err != null) {
                System.err.println(diffResult.err);
            }

            if (!diffResult.success) {
                System.exit(1);
            } else if (diffResult.warnAboutDirty) {
                System.exit(2);
            }
        } catch (final IOException|TECoreException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        };
    }

    /**
     * Do a diff of a shelveset.
     *
     * @param  collection    The TFS collection.
     * @param  shelvesetName The name of the shelveset.
     * @param  ownerName     The owner of the shelveset.
     * @return               The diff and/or error information.
     */
    private TFSDiffer.DiffResult getShelvesetDiff(final TFSCollection collection,
                                                  final String shelvesetName,
                                                  final String ownerName) {
        final VersionControlClient versionControl = collection.getVersionControlClient();

        log.info("Querying for shelveset '" + shelvesetName + "' (" + ownerName + ")");

        final PendingSet[] pendingSets = versionControl.queryShelvedChanges(
            shelvesetName, ownerName, null, true);

        return TFSDiffer.getInstance().diffPendingSets(pendingSets, versionControl);
    }

    /**
     * Do a diff of the working copy.
     *
     * @param  collection The TFS collection;
     * @return            The diff and/or error information.
     */
    private TFSDiffer.DiffResult getWorkingCopyDiff(final TFSCollection collection) {
        final VersionControlClient versionControl = collection.getVersionControlClient();
        final WorkspaceInfo workspace = collection.workspace;
        final String[] items = new String[]{ collection.workdir };
        final ItemSpec[] specs = ItemSpec.fromStrings(items, RecursionType.FULL);

        log.info("Doing diff of working copy");

        final PendingSet[] pendingSets = versionControl.queryPendingSets(
            specs, true, workspace.getName(), workspace.getOwnerName(), true);

        return TFSDiffer.getInstance().diffPendingSets(pendingSets, versionControl);
    }

    /**
     * Do a diff of the working copy.
     *
     * @param  collection The TFS collection;
     * @return            The diff and/or error information.
     */
    private TFSDiffer.DiffResult getCommittedChangesetsDiff(final TFSCollection collection,
                                                            final String base,
                                                            final String tip) {
        /*
         * TODO: Implement. This will involve querying for all the changesets
         * between base and tip, and then accumulating all changes together.
         */
        TFSDiffer.DiffResult result = new TFSDiffer.DiffResult();
        result.err = "Diffing between committed revisions isn't implemented yet.";
        result.success = false;
        return result;
    }
}
