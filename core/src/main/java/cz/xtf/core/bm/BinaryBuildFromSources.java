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
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
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
            for (File f : filesToArchive) {
                String tarPath = getPath().relativize(f.toPath()).toString();
                log.trace("adding file to tar: {}", tarPath);
                ArchiveEntry entry = o.createArchiveEntry(f, tarPath);

                // we force the modTime in the tar, so that the resulting tars are binary equal if their contents are
                TarArchiveEntry tarArchiveEntry = (TarArchiveEntry) entry;
                tarArchiveEntry.setModTime(Date.from(Instant.EPOCH));

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
}
