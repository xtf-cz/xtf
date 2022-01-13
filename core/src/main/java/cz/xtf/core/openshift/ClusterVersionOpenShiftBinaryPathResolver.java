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
import org.apache.commons.lang3.SystemUtils;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.http.Https;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Route;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ClusterVersionOpenShiftBinaryPathResolver implements OpenShiftBinaryPathResolver {
    private static final String OCP3_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v3/clients";
    private static final String OCP4_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v4";

    @Override
    public String resolve() {
        final ClusterVersionInfo clusterVersionInfo = ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo();
        final boolean cacheEnabled = OpenShiftConfig.isBinaryCacheEnabled();

        final String clientUrl = determineClientUrl(clusterVersionInfo);
        final Path archivePath = getCachedOrDownloadClientArchive(clientUrl,
                clusterVersionInfo.getOpenshiftVersion() != null ? getVersionOrChannel(clusterVersionInfo) : "unknown",
                cacheEnabled);
        return unpackOpenShiftClientArchive(archivePath, !cacheEnabled);
    }

    private String determineClientUrl(final ClusterVersionInfo versionInfo) {
        log.debug("Trying to determine OpenShift client url for cluster version {}.", versionInfo.getOpenshiftVersion());
        if (versionInfo.getOpenshiftVersion() != null) {
            return getClientUrlBasedOnOcpVersion(versionInfo);
        }
        return loadOrGuessClientUrlOnCluster();
    }

    private String getClientUrlBasedOnOcpVersion(final ClusterVersionInfo versionInfo) {
        Objects.requireNonNull(versionInfo);

        final String openshiftVersion = versionInfo.getOpenshiftVersion();
        if (openshiftVersion.startsWith("3")) {
            // OpenShift 3
            final String systemTypeForOCP3 = getSystemTypeForOCP3();
            String downloadUrl = String.format("%s/%s/%s/oc.tar.gz", OCP3_CLIENTS_URL, openshiftVersion,
                    systemTypeForOCP3);
            // if the generated download URL is not working (404 or 403 response code) try to concatenate -1 to the version
            final int code = Https.httpsGetCode(downloadUrl);
            if (code >= 400 && code < 500) {
                downloadUrl = String.format("%s/%s-1/%s/oc.tar.gz", OCP3_CLIENTS_URL, openshiftVersion,
                        systemTypeForOCP3);
            }
            return downloadUrl;
        } else {
            // OpenShift 4

            // https://mirror.openshift.com/pub/openshift-v4/clients/oc/$__DEPRECATED_LOCATION__PLEASE_READ__.txt
            // Please direct x86_64 users and automation to the new oc client locations under: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/
            //
            // This directory contains subdirectories for:
            // - The clients for released version of OpenShift v4; e.g.
            //   - The clients for OpenShift release 4.6.4: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/4.6.4/
            // - The latest client for a given OpenShift update channel; e.g.
            //   - The latest in the 4.6 candidate channel: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/candidate-4.6/
            //   - The latest in the 4.5 stable channel: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/stable-4.5/
            // - The latest client for the channels of the latest GA OpenShift release.
            //   - The latest client for the most recent GA release's candidate channel: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/candidate/
            //   - The latest client for the most recent GA release's stable channel: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/stable/
            //
            // If you are looking for the latest stable release's client, please use: https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/stable/

            String ocFileName = SystemUtils.IS_OS_MAC ? "openshift-client-mac.tar.gz" : "openshift-client-linux.tar.gz";

            return String.format("%s/%s/clients/ocp/%s/%s", OCP4_CLIENTS_URL, getSystemTypeForOCP4(),
                    getVersionOrChannel(versionInfo),
                    ocFileName);
        }
    }

    /**
     * Generates the URL reading from 'downloads' route in 'openshift-console' namespace
     * adding OS architecture and OS system to create the full OC client download URL
     *
     * @return client url on cluster
     */
    private String loadOrGuessClientUrlOnCluster() {
        final String locationTemplate = "https://%s/%s/%s/oc.tar";
        final String systemType = getSystemTypeForOCP4();
        final String operatingSystem = SystemUtils.IS_OS_MAC ? "mac" : "linux";

        try {
            final Optional<Route> downloadsRouteOptional = Optional
                    .ofNullable(OpenShifts.admin("openshift-console").getRoute("downloads"));
            final Route downloads = downloadsRouteOptional
                    .orElseThrow(() -> new IllegalStateException("We are not able to find download link for OC binary."));
            return String.format(locationTemplate,
                    downloads.getSpec().getHost(),
                    getSystemTypeForOCP4(),
                    operatingSystem);
        } catch (KubernetesClientException kce) {
            log.warn(
                    "It isn't possible to read 'downloads' route in 'openshift-console' namespace to get binary location. Attempting to guess it.",
                    kce);
            // try to guess URL in case of insufficient permission to read route 'downloads' in 'openshift-console' namespace
            return String.format(locationTemplate,
                    OpenShifts.admin("openshift-console").generateHostname("downloads"),
                    systemType,
                    operatingSystem);
        }
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

    private String getSystemTypeForOCP3() {
        String systemType = "linux";
        if (SystemUtils.IS_OS_MAC) {
            systemType = "macosx";
        } else if (isS390x()) {
            systemType += "-s390x";
        } else if (isPpc64le()) {
            systemType += "-ppc64le";
        }
        return systemType;
    }

    private String getSystemTypeForOCP4() {
        String systemType = "amd64";
        if (isS390x()) {
            systemType = "s390x";
        } else if (isPpc64le()) {
            systemType = "ppc64le";
        }
        return systemType;
    }

    private static boolean isS390x() {
        return SystemUtils.IS_OS_ZOS || "s390x".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("s390x");
    }

    private static boolean isPpc64le() {
        return "ppc64le".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("ppc64le");
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

    private String getVersionOrChannel(ClusterVersionInfo versionInfo) {
        Objects.requireNonNull(versionInfo);

        if (versionInfo.isMajorMinorMicro()) {
            return versionInfo.getOpenshiftVersion();
        } else {
            return getConfiguredChannel() + "-" + versionInfo.getMajorMinorOpenshiftVersion();
        }
    }

    private String getConfiguredChannel() {
        final String channel = OpenShiftConfig.binaryUrlChannelPath();
        // validate
        if (!Stream.of("stable", "fast", "latest", "candidate").collect(Collectors.toList()).contains(channel)) {
            throw new IllegalStateException(
                    "Channel (" + channel + ") configured in 'xtf.openshift.binary.url.channel' property is invalid.");
        }
        return channel;
    }
}
