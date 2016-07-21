package org.reviewboard.tfs;

import java.io.File;

import com.microsoft.tfs.core.clients.versioncontrol.Workstation;
import com.microsoft.tfs.core.clients.versioncontrol.path.LocalPath;
import com.microsoft.tfs.core.clients.versioncontrol.workspacecache.WorkspaceInfo;
import com.microsoft.tfs.core.config.persistence.DefaultPersistenceStoreProvider;
import com.microsoft.tfs.core.credentials.CachedCredentials;
import com.microsoft.tfs.core.credentials.CredentialsManager;
import com.microsoft.tfs.core.credentials.CredentialsManagerFactory;
import com.microsoft.tfs.core.httpclient.Credentials;
import com.microsoft.tfs.core.httpclient.DefaultNTCredentials;
import com.microsoft.tfs.core.httpclient.UsernamePasswordCredentials;
import com.microsoft.tfs.core.util.ServerURIUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.reviewboard.tfs.TFSCollection;


/**
 * An abstract command.
 *
 * Each individual command should subclass this and implement the getUsage,
 * getOptions, and run methods.
 */
public abstract class Command {
    private static Log log = LogFactory.getLog(Command.class);

    public abstract String getUsage();
    public abstract void run(CommandLine commandLine) throws ParseException;

    /**
     * Returns the command-line arguments that this command accepts.
     *
     * @return Options for the command-line parsing.
     */
    public Option[] getOptions() {
        return new Option[]{
            Option.builder()
                .longOpt("workdir")
                .desc("Local workdir.")
                .hasArg()
                .argName("workdir")
                .build(),
            Option.builder()
                .longOpt("login")
                .desc("TFS login information.")
                .hasArg()
                .argName("username@domain,password")
                .build()
        };
    }

    /**
     * Returns a connection to TFS.
     *
     * @param  commandLine The parsed command line.
     * @return             The connection to TFS.
     */
    protected final TFSCollection getCollection(final CommandLine commandLine) {
        final String workdir = new File(
            commandLine.hasOption("workdir")
            ? commandLine.getOptionValue("workdir")
            : LocalPath.getCurrentWorkingDirectory()).getAbsolutePath();
        log.info("Using working directory " + workdir);

        final WorkspaceInfo workspace = getLocalWorkspace(workdir);

        final java.net.URI serverURI = workspace.getServerURI();
        log.info("Using TFS server " + serverURI);

        final Credentials credentials = findCredentials(serverURI, commandLine.getOptionValue("login"));

        return new TFSCollection(serverURI, credentials, workdir, workspace);
    }

    /**
     * Returns credentials for the given server.
     *
     * @param  serverURI The TFS server to connect to.
     * @param  login     Login credentials provided on the command-line, if
     *                   any.
     * @return           A credentials object.
     */
    protected static Credentials findCredentials(java.net.URI serverURI, String login) {
        final CredentialsManager credentialsManager = CredentialsManagerFactory.getCredentialsManager(
            DefaultPersistenceStoreProvider.INSTANCE,
            !Application.getBooleanEnvVar("TF_USE_KEYCHAIN", true));

        final CachedCredentials cachedCredentials = credentialsManager.getCredentials(serverURI);

        if (login != null) {
            final String[] parts = login.split(",", 2);
            final String username = parts[0];
            final String password = parts.length == 2 ? parts[1] : null;

            return new UsernamePasswordCredentials(username, password);
        } else if (cachedCredentials != null &&
                   cachedCredentials.getUsername() != null &&
                   cachedCredentials.getUsername().length() > 0) {
            return new UsernamePasswordCredentials(cachedCredentials.getUsername(),
                                                   cachedCredentials.getPassword());
        } else if (!ServerURIUtils.isHosted(serverURI)) {
            return new DefaultNTCredentials();
        } else {
            return null;
        }
    }

    /**
     * Returns the workspace info for the given local working directory.
     *
     * @param  workdir The working directory to look up.
     * @return         TFS workspace information.
     */
    protected static WorkspaceInfo getLocalWorkspace(final String workdir) {
        final Workstation workstation = Workstation.getCurrent(DefaultPersistenceStoreProvider.INSTANCE);

        if (workdir != null && workstation.isMapped(workdir)) {
            return workstation.getLocalWorkspaceInfo(workdir);
        } else {
            return null;
        }
    }
}
