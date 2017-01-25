package org.reviewboard.tfs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import com.microsoft.tfs.core.clients.versioncontrol.VersionControlClient;
import com.microsoft.tfs.core.clients.versioncontrol.VersionControlConstants;
import com.microsoft.tfs.util.temp.TempStorageService;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.ChangeType;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.ItemType;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.PendingChange;
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.PendingSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;


/**
 * A utility class to create diffs for TFS.
 */
public class TFSDiffer {
    private static TFSDiffer instance = null;
    private static Log log = LogFactory.getLog(TFSDiffer.class);
    private final Charset utf8 = Charset.forName("UTF-8");

    public static class DiffResult {
        public byte[] diff = null;
        public String err = null;
        public boolean warnAboutDirty = false;
        public boolean success = true;
    }

    @SuppressWarnings("serial")
    protected class DiffException extends Exception {
        public DiffException(String message) {
            super(message);
        }
    }

    protected TFSDiffer() {}

    /**
     * Returns the TFSDiffer instance.
     *
     * @return The differ instance.
     */
    public static TFSDiffer getInstance() {
        if (instance == null) {
            instance = new TFSDiffer();
        }

        return instance;
    }

    /**
     * Perform a diff across a range of PendingSets.
     *
     * @param  sets           An array of PendingSets, which each contain
     *                        an array of PendingChanges.
     * @param  versionControl The version control client.
     * @return                A unified diff suitable for uploading to Review
     *                        Board.
     */
    public DiffResult diffPendingSets(final PendingSet[] sets,
                                      final VersionControlClient versionControl) {
        final DiffResult result = new DiffResult();

        try(final ByteArrayOutputStream diffStream = new ByteArrayOutputStream()) {
            boolean foundCandidateChanges = false;

            for (PendingSet set : sets) {
                for (PendingChange change : set.getPendingChanges()) {
                    diffPendingChange(change, versionControl, diffStream);
                }

                PendingChange[] candidateChanges = set.getCandidatePendingChanges();
                if (candidateChanges != null && candidateChanges.length > 0) {
                    result.warnAboutDirty = true;
                }
            }

            result.diff = diffStream.toByteArray();
        } catch (final DiffException|IOException e) {
            result.err = e.getMessage();
            result.success = false;
        }

        return result;
    }

    /**
     * Perform a diff of a PendingChange.
     *
     * Each PendingChange represents a change to an individual file.
     *
     * @param  change         The pending change to diff.
     * @param  versionControl The version control client.
     * @param  diff           The stream to write the diff to.
     */
    private final void diffPendingChange(final PendingChange change,
                                         final VersionControlClient versionControl,
                                         final ByteArrayOutputStream diff)
                                         throws DiffException, IOException {
        final String serverItem = change.getServerItem();
        final ChangeType changeType = change.getChangeType();
        final ItemType itemType = change.getItemType();
        final ChangeType availableTypes = ChangeType.combine(new ChangeType[]{
            ChangeType.ADD,
            ChangeType.BRANCH,
            ChangeType.DELETE,
            ChangeType.EDIT,
            ChangeType.RENAME,
            ChangeType.UNDELETE
        });

        if (itemType != ItemType.FILE || !changeType.containsAny(availableTypes)) {
            log.info("Skipping " + changeType.toUIString(false) + " of " + serverItem + " (" + itemType.toUIString() + ")");
            return;
        }

        final TempStorageService tempStorage = TempStorageService.getInstance();
        final boolean isBinary = change.getEncoding() == VersionControlConstants.ENCODING_BINARY;
        File oldFile;
        String oldVersion = new Integer(change.getVersion()).toString();
        String oldFilename = change.getServerItem();
        File newFile;
        String newVersion = "(pending)";
        String newFilename = oldFilename;

        if (change.isRename() || change.isBranch()) {
            oldFilename = change.getSourceServerItem();
            oldVersion = new Integer(change.getSourceVersionFrom()).toString();
        }

        if (change.isAdd() || change.isUndelete()) {
            log.info("Creating empty file to represent old version of " + serverItem);
            oldFile = tempStorage.createTempFile();
            oldFilename = "/dev/null";
        } else if (isBinary) {
            log.info("Creating empty file to represent old version of " + serverItem);
            oldFile = tempStorage.createTempFile();
        } else {
            log.info("Downloading old version of " + serverItem);
            oldFile = change.downloadBaseFileToTempLocation(versionControl, serverItem + ".old");
            log.info("Downloaded old version of " + serverItem + " to " + oldFile);
        }

        if (change.isDelete()) {
            log.info("Creating empty file to represent new version of " + serverItem);
            newFile = tempStorage.createTempFile();
            newVersion = "(deleted)";
        } else if (change.isInShelveset()) {
            log.info("Downloading new version of " + serverItem);
            newFile = change.downloadShelvedFileToTempLocation(versionControl, serverItem + ".new");
            log.info("Finished downloading new version of " + serverItem + " to "  + newFile);
        } else {
            final String localItem = change.getLocalItem();
            log.info("Using local item " + localItem + " as new version of " + serverItem);
            newFile = new File(localItem);
        }

        final String oldLabel = oldFilename + "\t" + oldVersion;
        final String newLabel = newFilename + "\t" + newVersion;

        log.info("Processing pending change " + changeType.toUIString(false) + " of " + serverItem);

        if (change.isBranch()) {
            IOUtils.write("Copied from: " + oldFilename + "\n", diff, utf8);
        }

        if (isBinary) {
            // Binary files
            IOUtils.write("--- " + oldLabel + "\n", diff, utf8);
            IOUtils.write("+++ " + newLabel + "\n", diff, utf8);
            IOUtils.write("Binary files " + oldFilename + " and " + newFilename + " differ\n", diff, utf8);
        } else if (!oldFilename.equals(newFilename) && FileUtils.contentEquals(oldFile, newFile)) {
            // Renamed file with no changes
            IOUtils.write("--- " + oldLabel + "\n", diff, utf8);
            IOUtils.write("+++ " + newLabel + "\n", diff, utf8);
        } else {
            final Process p = Runtime.getRuntime().exec(new String[]{
                "diff", "-u",
                "--label", oldLabel,
                "--label", newLabel,
                oldFile.getAbsolutePath(), newFile.getAbsolutePath()
            });

            try(final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                IOUtils.copy(p.getInputStream(), output);
                final String  error = IOUtils.toString(p.getErrorStream(), utf8);
                final int retcode = p.waitFor();

                if (retcode == 0 || retcode == 1) {
                    IOUtils.write(output.toByteArray(), diff);
                } else {
                    final String diffError = IOUtils.toString(p.getErrorStream(), utf8);
                    throw new DiffException("diff command failed with output:\n" + error);
                }
            } catch (InterruptedException|IOException e) {
                throw new DiffException("diff command failed: " + e.getMessage());
            }
        }
    }
}
