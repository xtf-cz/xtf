package cz.xtf.core.openshift;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.http.Https;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ClusterVersionOpenShiftBinaryPathResolver implements OpenShiftBinaryPathResolver {

    @Override
    public String resolve() {
        final ClusterVersionInfo clusterVersionInfo = ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo();
        final boolean cacheEnabled = OpenShiftConfig.isBinaryCacheEnabled();
        final String clientUrl = clusterVersionInfo.getClientUrl();
        if (clientUrl == null) {
            log.warn("OpenShift version is not detected, using stable channel.");
        }
        final Path archivePath = getCachedOrDownloadClientArchive(clientUrl,
                Optional.ofNullable(clusterVersionInfo.getOpenshiftVersion()).orElse("stable"),
                cacheEnabled);
        return unpackOpenShiftClientArchive(archivePath, !cacheEnabled);
    }

    private Path getCachedOrDownloadClientArchive(final String url, final String version, final boolean cacheEnabled) {
        Objects.requireNonNull(url);

        log.debug("Trying to load OpenShift client archive from cache (enabled: {}) or download it from {}.", cacheEnabled,
                url);
        Path archivePath;
        if (cacheEnabled) {
            Path cachePath = Paths.get(OpenShiftConfig.binaryCachePath(), version, DigestUtils.md5Hex(url));
            archivePath = cachePath.resolve("oc.tar.gz");
            if (Files.exists(archivePath)) {
                // it is cached, removed immediately
                log.debug("OpenShift client archive is already in cache: {}.", archivePath.toAbsolutePath());
                return archivePath;
            }
            log.debug("OpenShift client archive not found in cache, downloading it.");
        } else {
            archivePath = getProjectOcDir().resolve("oc.tar.gz");
            log.debug("Cache is disabled, downloading OpenShift client archive to {}.", archivePath.toAbsolutePath());
        }

        try {
            Https.copyHttpsURLToFile(url, archivePath.toFile(), 20_000, 300_000);
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to download and extract oc binary from " + url, ioe);
        }
        return archivePath;
    }

    private String unpackOpenShiftClientArchive(final Path archivePath, final boolean deleteArchiveWhenDone) {
        Objects.requireNonNull(archivePath);

        try {
            List<String> args = Stream
                    .of("tar", "-xf", archivePath.toAbsolutePath().toString(), "-C",
                            getProjectOcDir().toAbsolutePath().toString())
                    .collect(Collectors.toList());
            ProcessBuilder pb = new ProcessBuilder(args);

            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            int result = pb.start().waitFor();

            if (result != 0) {
                throw new IOException("Failed to execute: " + args);
            }

        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to extract oc binary " + archivePath.toAbsolutePath(), e);
        }

        try {
            if (deleteArchiveWhenDone) {
                Files.delete(archivePath);
            }
        } catch (IOException ioe) {
            log.warn("It wasn't possible to delete OpenShift client archive {}", archivePath.toAbsolutePath(), ioe);
        }

        return getProjectOcDir().resolve("oc").toAbsolutePath().toString();
    }

    private Path getProjectOcDir() {
        Path dir = Paths.get("tmp/oc/");

        try {
            Files.createDirectories(dir);
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to create directory " + dir.toAbsolutePath(), ioe);
        }

        return dir;
    }
}
