package cz.xtf.core.helm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.SystemUtils;

import cz.xtf.core.config.HelmConfig;
import cz.xtf.core.http.Https;
import cz.xtf.core.openshift.ClusterVersionInfo;
import cz.xtf.core.openshift.ClusterVersionInfoFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ConfiguredVersionHelmBinaryPathResolver implements HelmBinaryPathResolver {

    private static final String OCP_4_HELM_BINARY_DOWNLOAD_URL = "https://mirror.openshift.com/pub/openshift-v4/clients/helm";

    @Override
    public String resolve() {
        final ClusterVersionInfo clusterVersionInfo = ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo();
        if (!clusterVersionInfo.getOpenshiftVersion().startsWith("4")) {
            log.warn(
                    "Unsupported Openshift version for Helm client, OCP cluster is of version {}, while currently only OCP 4 is supported",
                    clusterVersionInfo.getOpenshiftVersion());
            return null;
        }
        final boolean cacheEnabled = HelmConfig.isHelmBinaryCacheEnabled();
        final String helmClientVersion = HelmConfig.helmClientVersion();
        final String clientUrl = getHelmClientUrlBasedOnConfiguredHelmVersion(helmClientVersion);
        final Path archivePath = getCachedOrDownloadClientArchive(clientUrl, helmClientVersion, cacheEnabled);
        return unpackHelmClientArchive(archivePath, !cacheEnabled);
    }

    private String unpackHelmClientArchive(final Path archivePath, final boolean deleteArchiveWhenDone) {
        Objects.requireNonNull(archivePath);

        try {
            List<String> args = Stream
                    .of("tar", "-xf", archivePath.toAbsolutePath().toString(), "-C",
                            getProjectHelmDir().toAbsolutePath().toString())
                    .collect(Collectors.toList());
            ProcessBuilder pb = new ProcessBuilder(args);

            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            int result = pb.start().waitFor();

            if (result != 0) {
                throw new IOException("Failed to execute: " + args);
            }

        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to extract helm binary " + archivePath.toAbsolutePath(), e);
        }

        try {
            if (deleteArchiveWhenDone) {
                Files.delete(archivePath);
            }
        } catch (IOException ioe) {
            log.warn("It wasn't possible to delete Helm client archive {}", archivePath.toAbsolutePath(), ioe);
        }
        try (Stream<Path> helmDirContents = Files.list(getProjectHelmDir())) {
            Path helmBinaryFile = helmDirContents.collect(Collectors.toList()).get(0);
            if (!helmBinaryFile.getFileName().toString().equals("helm")) {
                Files.move(helmBinaryFile, helmBinaryFile.resolveSibling("helm"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error when extracting Helm client binary", e);
        }
        return getProjectHelmDir().resolve("helm").toAbsolutePath().toString();
    }

    private Path getCachedOrDownloadClientArchive(final String url, final String version, final boolean cacheEnabled) {
        Objects.requireNonNull(url);

        log.debug("Trying to load Helm client archive from cache (enabled: {}) or download it from {}.", cacheEnabled,
                url);
        Path archivePath;
        if (cacheEnabled) {
            Path cachePath = Paths.get(HelmConfig.binaryCachePath(), version, DigestUtils.md5Hex(url));
            archivePath = cachePath.resolve("helm.tar.gz");
            if (Files.exists(archivePath)) {
                // it is cached, removed immediately
                log.debug("Helm client archive is already in cache: {}.", archivePath.toAbsolutePath());
                return archivePath;
            }
            log.debug("Helm client archive not found in cache, downloading it.");
        } else {
            archivePath = getProjectHelmDir().resolve("helm.tar.gz");
            log.debug("Cache is disabled, downloading Helm client archive to {}.", archivePath.toAbsolutePath());
        }

        try {
            Https.copyHttpsURLToFile(url, archivePath.toFile(), 20_000, 300_000);
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to download and extract helm binary from " + url, ioe);
        }
        return archivePath;
    }

    private String getHelmClientUrlBasedOnConfiguredHelmVersion(final String helmClientVersion) {
        String systemType = "linux";
        if (SystemUtils.IS_OS_MAC) {
            systemType = "darwin";
        }
        return String.format("%s/%s/helm-%s-amd64.tar.gz", OCP_4_HELM_BINARY_DOWNLOAD_URL, helmClientVersion,
                systemType);
    }

    private Path getProjectHelmDir() {
        Path dir = Paths.get("tmp/helm/");

        try {
            Files.createDirectories(dir);
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to create directory " + dir.toAbsolutePath(), ioe);
        }

        return dir;
    }
}
