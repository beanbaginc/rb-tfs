package org.reviewboard.tfs;

import com.microsoft.tfs.core.TFSTeamProjectCollection;
import com.microsoft.tfs.core.clients.versioncontrol.workspacecache.WorkspaceInfo;
import com.microsoft.tfs.core.httpclient.Credentials;


/**
 * This adaptor allows the TFSTeamProjectCollection to be used with java's
 * relatively new try-with-resources construct to automatically close the
 * connection, even when there are exceptions.
 */
public class TFSCollection extends TFSTeamProjectCollection implements AutoCloseable {
    public String workdir;
    public WorkspaceInfo workspace;

    public TFSCollection(java.net.URI serverURI, Credentials credentials, String workdir, WorkspaceInfo workspace) {
        super(serverURI, credentials);

        this.workdir = workdir;
        this.workspace = workspace;
    }

    public void close() {
        super.close();
    }
};
