package cz.xtf.core.openshift;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.dmr.ModelNode;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.http.Https;
import cz.xtf.core.http.HttpsException;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.ClusterVersion;
import io.fabric8.openshift.api.model.ClusterVersionList;
import io.fabric8.openshift.client.OpenShiftHandlers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClusterVersionInfo {
    // version must contain major.minor (4.8)

    private static final String OCP3_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v3/clients";
    private static final String OCP4_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v4";

    private static final Pattern versionPattern = Pattern
            .compile("(^(\\d+\\.\\d+).*)");
    private String openshiftVersion;
    private String openshiftVersionURL;
    private Matcher versionMatcher;

    ClusterVersionInfo() {
        //get version from config
        openshiftVersion = OpenShiftConfig.version();
        versionMatcher = (openshiftVersion != null) ? versionPattern.matcher(openshiftVersion) : null;
        if (versionMatcher != null && versionMatcher.matches()) {
            getClientUrlBasedOnOcpVersion();
        }

        if (getClientUrl() != null && getOpenshiftVersion() != null) {
            log.info("xtf.openshift.version version detected: {}", getOpenshiftVersion());
            return;
        }
        log.warn("xtf.openshift.version not found, proceeding with cluster detection");

        //fallback - get version from cluster
        openshiftVersion = detectClusterVersionFromCluster();
        versionMatcher = (openshiftVersion != null) ? versionPattern.matcher(openshiftVersion) : null;
        if (versionMatcher != null && versionMatcher.matches()) {
            getClientUrlBasedOnOcpVersion();
        }
        if (getClientUrl() != null && getOpenshiftVersion() != null) {
            log.info("xtf.openshift.version version detected: {}", getOpenshiftVersion());
        } else {
            log.warn("xtf.openshift.version cluster detection failed");
        }
    }

    /**
     * @return full version of OpenShift cluster as detected or configured or null
     */
    public String getOpenshiftVersion() {
        return openshiftVersion;
    }

    /**
     * @return url to download client tools for OpenShift cluster as detected or configured or null
     */
    public String getClientUrl() {
        return openshiftVersionURL;
    }

    /**
     * @return major.minor only version of OpenShift cluster as detected or configured or null
     */
    String getMajorMinorOpenshiftVersion() {
        if (openshiftVersion != null) {
            return versionMatcher.group(2);
        }
        return null;
    }

    /**
     * Detects cluster version from cluster
     *
     * @return version of OpenShift cluster or null
     */
    private String detectClusterVersionFromCluster() {
        String openshiftVersion = null;
        try {
            // try to access version info on OpenShift 3.x, this endpoint isn't available on OpenShift 4.x
            // another option might be client.getVersion() but it returns Kubernetes version, we would
            // need to check whether version starts with 1.x == Kubernetes == OpenShift 4.x
            //
            // Response looks like this:
            //  {
            //    "major": "3",
            //    "minor": "11+",
            //    "gitVersion": "v3.11.272",
            //    "gitCommit": "8b0575fb48",
            //    "gitTreeState": "",
            //    "buildDate": "2020-08-18T05:38:34Z",
            //    "goVersion": "",
            //    "compiler": "",
            //    "platform": ""
            //  }
            String versionInfo = Https.httpsGetContent(OpenShiftConfig.url() + "/version/openshift");

            // it is OpenShift 3, parse version from gitVersion and convert it
            // example: v3.11.272 -> 3.11.272
            openshiftVersion = ModelNode.fromJSONString(versionInfo).get("gitVersion").asString()
                    .replaceAll("^v(.*)", "$1");
        } catch (HttpsException he) {
            // it is OpenShift 4+
            // admin is required for operation
            try {
                NonNamespaceOperation<ClusterVersion, ClusterVersionList, Resource<ClusterVersion>> op = OpenShiftHandlers
                        .getOperation(ClusterVersion.class, ClusterVersionList.class, OpenShifts.admin());
                openshiftVersion = op.withName("version").get().getStatus().getDesired().getVersion();
            } catch (KubernetesClientException kce) {
                log.warn("xtf.openshift.version isn't configured and automatic version detection failed.", kce);
            }
        }
        return openshiftVersion;
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

    private static boolean isS390x() {
        return SystemUtils.IS_OS_ZOS || "s390x".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("s390x");
    }

    private static boolean isPpc64le() {
        return "ppc64le".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("ppc64le");
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

    private String getOcp4DownloadUrl(final String versionOrChannel, final String location) {
        final String ocFileName = SystemUtils.IS_OS_MAC ? "openshift-client-mac.tar.gz" : "openshift-client-linux.tar.gz";
        return String.format("%s/%s/clients/%s/%s/%s", OCP4_CLIENTS_URL, getSystemTypeForOCP4(),
                location, versionOrChannel, ocFileName);
    }

    private void getClientUrlBasedOnOcpVersion() {
        final String openshiftVersion = getOpenshiftVersion();
        Objects.requireNonNull(openshiftVersion, "OpenShift version not set");

        if (openshiftVersion.startsWith("3")) {
            // OpenShift 3
            final String systemTypeForOCP3 = getSystemTypeForOCP3();
            String downloadUrl = String.format(
                    "%s/%s/%s/oc.tar.gz", OCP3_CLIENTS_URL, openshiftVersion, systemTypeForOCP3);

            int code = Https.httpsGetCode(downloadUrl);
            if (code >= 200 && code < 300) {
                this.openshiftVersion = openshiftVersion;
                this.openshiftVersionURL = downloadUrl;
                return;
            }

            // if the generated download URL is not working (404 or 403 response code) try to concatenate
            // -1 to the version
            downloadUrl = String.format(
                    "%s/%s-1/%s/oc.tar.gz", OCP3_CLIENTS_URL, openshiftVersion, systemTypeForOCP3);
            code = Https.httpsGetCode(downloadUrl);
            if (code >= 200 && code < 300) {
                this.openshiftVersion = openshiftVersion;
                this.openshiftVersionURL = downloadUrl;
                return;
            }
        } else {
            // OpenShift 4

            // https://mirror.openshift.com/pub/openshift-v4/clients/oc/$__DEPRECATED_LOCATION__PLEASE_READ__.txt
            // Please direct x86_64 users and automation to the new oc client locations under:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/
            //
            // This directory contains subdirectories for:
            // - The clients for released version of OpenShift v4; e.g.
            //   - The clients for OpenShift release 4.6.4:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/4.6.4/
            // - The latest client for a given OpenShift update channel; e.g.
            //   - The latest in the 4.6 candidate channel:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/candidate-4.6/
            //   - The latest in the 4.5 stable channel:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/stable-4.5/
            // - The latest client for the channels of the latest GA OpenShift release.
            //   - The latest client for the most recent GA release's candidate channel:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/candidate/
            //   - The latest client for the most recent GA release's stable channel:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/stable/
            //
            // If you are looking for the latest stable release's client, please use:
            // https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/stable/

            // list of fallbacks - channel defaults to stable if not set
            for (String version : Stream.of(
                    openshiftVersion,
                    getMajorMinorOpenshiftVersion(),
                    getConfiguredChannel() + "-" + getMajorMinorOpenshiftVersion(),
                    getConfiguredChannel())
                    .map(Object::toString)
                    .collect(Collectors.toList())) {

                // list of fallbacks ( ocp, ocp-dev-preview )
                List<Pair<String, String>> channels = Arrays.asList(Pair.of(version, "ocp"),
                        Pair.of(version, "ocp-dev-preview"));

                for (Pair<String, String> channel : channels) {
                    String downloadUrl = getOcp4DownloadUrl(channel.getLeft(), channel.getRight());
                    int code = Https.httpsGetCode(downloadUrl);
                    if (code >= 200 && code < 300) {
                        this.openshiftVersion = openshiftVersion;
                        this.openshiftVersionURL = downloadUrl;
                        return;
                    }
                }
            }

            // list of fallbacks - stable
            List<Pair<String, String>> channels = Arrays.asList(Pair.of("stable", "ocp"), Pair.of("stable", "ocp-dev-preview"));

            for (Pair<String, String> channel : channels) {
                String downloadUrl = getOcp4DownloadUrl(channel.getLeft(), channel.getRight());
                int code = Https.httpsGetCode(downloadUrl);
                if (code >= 200 && code < 300) {
                    this.openshiftVersion = null;
                    this.openshiftVersionURL = downloadUrl;
                    return;
                }
            }
            this.openshiftVersion = null;
            this.openshiftVersionURL = null;
        }
    }

    Boolean isMajorMinorOnly() {
        Objects.requireNonNull(openshiftVersion);
        return openshiftVersion.matches("^\\d+\\.\\d+$");
    }
}
