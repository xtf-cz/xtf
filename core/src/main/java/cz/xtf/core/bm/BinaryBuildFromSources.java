package cz.xtf.core.bm;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import cz.xtf.core.openshift.OpenShift;
import lombok.extern.slf4j.Slf4j;

/**
 * Binary build that expect maven sources on path that shall be uploaded to OpenShift and built there.
 */
@Slf4j
abstract public class BinaryBuildFromSources extends BinaryBuild {

    private static final int OWNER_READ_FILEMODE = 0400;
    private static final int OWNER_WRITE_FILEMODE = 0200;
    private static final int OWNER_EXEC_FILEMODE = 0100;
    private static final int GROUP_READ_FILEMODE = 0040;
    private static final int GROUP_WRITE_FILEMODE = 0020;
    private static final int GROUP_EXEC_FILEMODE = 0010;
    private static final int OTHERS_READ_FILEMODE = 0004;
    private static final int OTHERS_WRITE_FILEMODE = 0002;
    private static final int OTHERS_EXEC_FILEMODE = 0001;

    public BinaryBuildFromSources(String builderImage, Path path, Map<String, String> envProperties, String id) {
        super(builderImage, path, envProperties, id);
    }

    @Override
    public void build(OpenShift openShift) {
        openShift.imageStreams().create(is);
        openShift.buildConfigs().create(bc);

        try {
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos);

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            final Future<?> future = executorService.submit(() -> writeProjectTar(pos));

            openShift.buildConfigs().withName(bc.getMetadata().getName()).instantiateBinary().fromInputStream(pis);
            future.get();
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("Exception building {}", getId(), e);
            throw new RuntimeException(e);
        }
    }

    protected String getContentHash() {
        if (!isCached() || contentHash == null) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                DigestOutputStream dos = new DigestOutputStream(NULL_OUTPUT_STREAM, md);

                writeProjectTar(dos);

                // kubernetes annotation value must not be longer than 63 chars
                contentHash = Hex.encodeHexString(dos.getMessageDigest().digest()).substring(0, 63);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        return contentHash;
    }

    private void writeProjectTar(OutputStream os) {
        Collection<File> filesToArchive = FileUtils.listFiles(getPath().toFile(), TrueFileFilter.INSTANCE,
                TrueFileFilter.INSTANCE);
        try (TarArchiveOutputStream o = (TarArchiveOutputStream) new ArchiveStreamFactory()
                .createArchiveOutputStream(ArchiveStreamFactory.TAR, os)) {
            o.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            o.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            for (File f : filesToArchive) {
                String tarPath = getPath().relativize(f.toPath()).toString();
                log.trace("adding file to tar: {}", tarPath);
                ArchiveEntry entry = o.createArchiveEntry(f, tarPath);

                // we force the time fields in the tar, so that the resulting tars are binary equal if their contents are
                TarArchiveEntry tarArchiveEntry = (TarArchiveEntry) entry;
                tarArchiveEntry.setModTime(Date.from(Instant.EPOCH));
                tarArchiveEntry.setCreationTime(FileTime.from(Instant.EPOCH));
                tarArchiveEntry.setLastAccessTime(FileTime.from(Instant.EPOCH));
                tarArchiveEntry.setLastModifiedTime(FileTime.from(Instant.EPOCH));
                tarArchiveEntry.setStatusChangeTime(FileTime.from(Instant.EPOCH));
                PosixFileAttributes attrs = Files.getFileAttributeView(Paths.get(f.toURI()), PosixFileAttributeView.class)
                        .readAttributes();
                tarArchiveEntry.setMode(toOctalFileMode(attrs.permissions()));

                o.putArchiveEntry(tarArchiveEntry);
                if (f.isFile()) {
                    try (InputStream i = Files.newInputStream(f.toPath())) {
                        IOUtils.copy(i, o);
                    }
                }
                o.closeArchiveEntry();
            }
            o.finish();
        } catch (ArchiveException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts a set of {@link PosixFilePermission} to chmod-style octal file mode.
     */
    public int toOctalFileMode(Set<PosixFilePermission> permissions) {
        int result = 0;
        for (PosixFilePermission permissionBit : permissions) {
            switch (permissionBit) {
                case OWNER_READ:
                    result |= OWNER_READ_FILEMODE;
                    break;
                case OWNER_WRITE:
                    result |= OWNER_WRITE_FILEMODE;
                    break;
                case OWNER_EXECUTE:
                    result |= OWNER_EXEC_FILEMODE;
                    break;
                case GROUP_READ:
                    result |= GROUP_READ_FILEMODE;
                    break;
                case GROUP_WRITE:
                    result |= GROUP_WRITE_FILEMODE;
                    break;
                case GROUP_EXECUTE:
                    result |= GROUP_EXEC_FILEMODE;
                    break;
                case OTHERS_READ:
                    result |= OTHERS_READ_FILEMODE;
                    break;
                case OTHERS_WRITE:
                    result |= OTHERS_WRITE_FILEMODE;
                    break;
                case OTHERS_EXECUTE:
                    result |= OTHERS_EXEC_FILEMODE;
                    break;
            }
        }
        return result;
    }
}
