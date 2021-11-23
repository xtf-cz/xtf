package cz.xtf.core.openshift;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
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
class ClusterVersionInfo {
    // version must be in format major.minor.micro (4.8.13) or major.minor (4.8)
    private static final Pattern versionPattern = Pattern.compile("^(\\d+\\.\\d+)(\\.\\d+)?$");
    private final String openshiftVersion;
    private final Matcher versionMatcher;

    ClusterVersionInfo() {
        if (StringUtils.isNotEmpty(OpenShiftConfig.version())) {
            // manually configured version in config
            openshiftVersion = OpenShiftConfig.version();
        } else {
            // try to detect version from cluster
            openshiftVersion = detectClusterVersionFromCluster();
        }

        versionMatcher = openshiftVersion != null ? validateConfiguredVersion(openshiftVersion) : null;
    }

    /**
     * @return full version of OpenShift cluster as detected or configured or null
     */
    String getOpenshiftVersion() {
        return openshiftVersion;
    }

    /**
     * @return major.minor only version of OpenShift cluster as detected or configured or null
     */
    String getMajorMinorOpenshiftVersion() {
        if (openshiftVersion != null) {
            return versionMatcher.group(1);
        }
        return null;
    }

    /**
     * @return true if version is in major.minor format only, false if not valid url
     */
    boolean isMajorMinorOnly() {
        if (openshiftVersion != null) {
            return versionMatcher.group(2) == null;
        }
        return false;
    }

    /**
     * @return true if version is in major.minor.micro format, false if not valid url
     */
    boolean isMajorMinorMicro() {
        if (openshiftVersion != null) {
            return versionMatcher.group(2) != null;
        }
        return false;
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

    private Matcher validateConfiguredVersion(final String version) {
        Objects.requireNonNull(version);

        Matcher matcher = versionPattern.matcher(version);
        if (!matcher.matches()) {
            log.warn("Version {} configured in xtf.openshift.version isn't in expected format 'major.minor[.micro]'.", version);
        }
        return matcher;
    }
}
