package org.reviewboard.tfs;

import com.microsoft.tfs.core.clients.versioncontrol.VersionControlClient;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.Changeset;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.RecursionType;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.Shelveset;
import com.microsoft.tfs.core.clients.versioncontrol.specs.version.LatestVersionSpec;
import com.microsoft.tfs.core.clients.versioncontrol.specs.version.VersionSpec;
import com.microsoft.tfs.core.clients.versioncontrol.specs.version.WorkspaceVersionSpec;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.reviewboard.tfs.Command;
import org.reviewboard.tfs.Revision;
import org.reviewboard.tfs.TFSCollection;


public class CommandParseRevision extends Command {
    private static Log log = LogFactory.getLog(CommandParseRevision.class);

    /**
     * Returns a string to use when printing usage information.
     *
     * @return Usage information.
     */
    public String getUsage() {
        return "[revision or shelveset ...]";
    }

    /**
     * Returns the command-line arguments that this command accepts.
     *
     * @return Options for the command-line parsing.
     */
    public Option[] getOptions() {
        return ArrayUtils.addAll(super.getOptions(), new Option[]{
            Option.builder()
                .longOpt("shelveset-owner")
                .desc("Look up the shelveset created by the given owner.")
                .hasArg()
                .argName("owner")
                .build()
        });
    }

    /**
     * Run the command.
     *
     * @param  commandLine    Command-line arguments.
     * @throws ParseException An error parsing the command line.
     */
    public void run(CommandLine commandLine) throws ParseException {
        final String[] revision = commandLine.getArgs();

        try(final TFSCollection collection = getCollection(commandLine)) {
            /*
             * If there are no revisions specified, we want the working
             * directory.
             */
            if (revision.length == 0) {
                final VersionSpec version = new WorkspaceVersionSpec(collection.workspace.getWorkspace(collection));
                final Changeset change = getChanges(collection, null, version, 1)[0];

                System.out.println(change.getChangesetID());
                System.out.println(Revision.WORKING_COPY);

                return;
            }

            /*
             * If there's a single revision specified, first check to see if
             * it's a shelveset.
             */
            if (revision.length == 1) {
                final String ownerName = commandLine.hasOption("owner")
                                         ? commandLine.getOptionValue("owner")
                                         : collection.workspace.getOwnerName();
                final VersionControlClient versionControl = collection.getVersionControlClient();
                final Shelveset[] shelvesets = versionControl.queryShelvesets(revision[0], ownerName, null);

                if (shelvesets.length == 1) {
                    System.out.println(Revision.SHELVESET_BASE);
                    System.out.println(Revision.SHELVESET_PREFIX + revision[0]);

                    return;
                }
            }

            VersionSpec fromVersion = null;
            VersionSpec toVersion = null;

            if (revision.length == 1) {
                final VersionSpec[] versions = VersionSpec.parseMultipleVersionsFromSpec(revision[0], null, true);

                if (versions.length == 1) {
                    toVersion = versions[0];
                } else if (versions.length == 2) {
                    fromVersion = versions[0];
                    toVersion = versions[1];
                } else {
                    assert false : versions;
                }
            } else if (revision.length == 2) {
                fromVersion = VersionSpec.parseSingleVersionFromSpec(revision[0], null);
                toVersion = VersionSpec.parseSingleVersionFromSpec(revision[1], null);
            } else {
                throw new ParseException("parse-revision takes between zero and two revisions");
            }

            Changeset[] changes = null;
            if (fromVersion == null) {
                changes = getChanges(collection, fromVersion, toVersion, 2);

                ArrayUtils.reverse(changes);
            } else {
                changes = new Changeset[]{
                    getChanges(collection, null, fromVersion, 1)[0],
                    getChanges(collection, null, toVersion, 1)[0],
                };
            }

            for (Changeset change : changes) {
                System.out.println(change.getChangesetID());
            }
        }
    }

    /**
     * Query for changeset history.
     *
     * @param  collection  The TFS collection.
     * @param  fromVersion The oldest version to query.
     * @param  toVersion   The newest version to query.
     * @param  maxChanges  The total number of results to return.
     * @return             An array of changesets based on the given query
     *                     parameters.
     */
    private Changeset[] getChanges(TFSCollection collection,
                                   VersionSpec fromVersion,
                                   VersionSpec toVersion,
                                   int maxChanges) {
        return collection.getVersionControlClient().queryHistory(
            collection.workdir,
            LatestVersionSpec.INSTANCE,
            0,
            RecursionType.FULL,
            null,
            fromVersion,
            toVersion,
            maxChanges,
            false,
            false,
            false,
            false);
    }
}
