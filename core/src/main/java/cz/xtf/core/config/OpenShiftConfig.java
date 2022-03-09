package cz.xtf.core.config;

import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class OpenShiftConfig {
    public static final String OPENSHIFT_URL = "xtf.openshift.url";
    public static final String OPENSHIFT_TOKEN = "xtf.openshift.token";
    public static final String OPENSHIFT_VERSION = "xtf.openshift.version";
    public static final String OPENSHIFT_NAMESPACE = "xtf.openshift.namespace";
    public static final String OPENSHIFT_BINARY_PATH = "xtf.openshift.binary.path";
    public static final String OPENSHIFT_BINARY_URL_CHANNEL = "xtf.openshift.binary.url.channel";
    public static final String OPENSHIFT_BINARY_CACHE_ENABLED = "xtf.openshift.binary.cache.enabled";
    public static final String OPENSHIFT_BINARY_CACHE_PATH = "xtf.openshift.binary.cache.path";
    public static final String OPENSHIFT_BINARY_CACHE_DEFAULT_FOLDER = "xtf-oc-cache";
    public static final String OPENSHIFT_ADMIN_USERNAME = "xtf.openshift.admin.username";
    public static final String OPENSHIFT_ADMIN_PASSWORD = "xtf.openshift.admin.password";
    public static final String OPENSHIFT_ADMIN_KUBECONFIG = "xtf.openshift.admin.kubeconfig";
    public static final String OPENSHIFT_ADMIN_TOKEN = "xtf.openshift.admin.token";
    public static final String OPENSHIFT_MASTER_USERNAME = "xtf.openshift.master.username";
    public static final String OPENSHIFT_MASTER_PASSWORD = "xtf.openshift.master.password";
    public static final String OPENSHIFT_MASTER_KUBECONFIG = "xtf.openshift.master.kubeconfig";
    public static final String OPENSHIFT_MASTER_TOKEN = "xtf.openshift.master.token";
    public static final String OPENSHIFT_ROUTE_DOMAIN = "xtf.openshift.route_domain";
    public static final String OPENSHIFT_PULL_SECRET = "xtf.openshift.pullsecret";
    public static final String OPENSHIFT_NAMESPACE_PER_TESTCASE = "xtf.openshift.namespace.per.testcase";
    /**
     * Used only if xtf.openshift.namespace.per.testcase=true - this property can configure its maximum length. This is useful
     * in case
     * where namespace is used in first part of URL of route which must have <64 chars length.
     */
    public static final String OPENSHIFT_UNIQUE_NAMESPACE_NAME_LENGTH_LIMIT = "xtf.openshift.namespace.per.testcase.length.limit";

    /**
     * Used only if xtf.openshift.namespace.per.testcase=true - this property configures default maximum length of namespace
     * name.
     */
    private static final String OPENSHIFT_UNIQUE_NAMESPACE_NAME_LENGTH_LIMIT_DEFAULT = "25";

    public static String url() {
        return XTFConfig.get(OPENSHIFT_URL);
    }

    /**
     * @return returns token
     * @deprecated Use masterToken {@link #masterToken()}
     */
    @Deprecated
    public static String token() {
        String token = XTFConfig.get(OPENSHIFT_TOKEN);
        if (token == null) {
            return XTFConfig.get(OPENSHIFT_MASTER_TOKEN);
        }
        return token;
    }

    public static String adminToken() {
        return XTFConfig.get(OPENSHIFT_ADMIN_TOKEN);
    }

    public static String version() {
        return XTFConfig.get(OPENSHIFT_VERSION);
    }

    /**
     * @return Returns default namespace as defined in xtf.openshift.namespace property or namespace for currently running test
     *         case when:
     *         -Dxtf.openshift.namespace.per.testcase=true.
     *         In case when current thread does not have associated test case (for example when initializing Openshift instance
     *         in static variable or static block) then exception is thrown.
     */
    public static String namespace() {
        if (useNamespacePerTestcase()) {
            String namespace = TestCaseContext.getRunningTestcase().get(TestCaseContext.getTestCaseForCurrentThread());
            if (StringUtils.isEmpty(namespace)) {
                throw new RuntimeException(
                        "There is no namespace associated with current thread or test case. This can happen in case that OpenShift instance is created in static variable. In this case avoid using static. Or in thread which is not associated with any test case.");
            }
            return namespace;
        } else {
            return XTFConfig.get(OPENSHIFT_NAMESPACE);
        }
    }

    public static boolean useNamespacePerTestcase() {
        if (StringUtils.isEmpty(XTFConfig.get(OpenShiftConfig.OPENSHIFT_NAMESPACE_PER_TESTCASE))
                || (StringUtils.isNotEmpty(XTFConfig.get(OpenShiftConfig.OPENSHIFT_NAMESPACE_PER_TESTCASE))
                        && !Boolean.parseBoolean(XTFConfig.get(OpenShiftConfig.OPENSHIFT_NAMESPACE_PER_TESTCASE)))) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * @return limit on namespace if it's set by -Dxtf.openshift.unique.namespace.name.length.limit property otherwise returns
     *         -1 which means not set
     */
    public static int getNamespaceLengthLimitForUniqueNamespacePerTest() {
        if (useNamespacePerTestcase()) {
            return Integer.parseInt(XTFConfig.get(OPENSHIFT_UNIQUE_NAMESPACE_NAME_LENGTH_LIMIT,
                    OPENSHIFT_UNIQUE_NAMESPACE_NAME_LENGTH_LIMIT_DEFAULT));
        } else {
            return -1;
        }
    }

    public static String binaryPath() {
        return XTFConfig.get(OPENSHIFT_BINARY_PATH);
    }

    /**
     * Channel configuration for download of OpenShift client from
     * https://mirror.openshift.com/pub/openshift-v4/x86_64/clients/ocp/
     * Channels are: stable, latest, fast, candidate
     *
     * @return channel as configured in xtf.openshift.binary.url.channel property, or default 'stable'
     */
    public static String binaryUrlChannelPath() {
        return XTFConfig.get(OPENSHIFT_BINARY_URL_CHANNEL, "stable");
    }

    public static boolean isBinaryCacheEnabled() {
        return Boolean.parseBoolean(XTFConfig.get(OPENSHIFT_BINARY_CACHE_ENABLED, "true"));
    }

    public static String binaryCachePath() {
        return XTFConfig.get(OPENSHIFT_BINARY_CACHE_PATH, Paths.get(System.getProperty("java.io.tmpdir"),
                OPENSHIFT_BINARY_CACHE_DEFAULT_FOLDER).toAbsolutePath().normalize().toString());
    }

    public static String adminUsername() {
        return XTFConfig.get(OPENSHIFT_ADMIN_USERNAME);
    }

    public static String adminPassword() {
        return XTFConfig.get(OPENSHIFT_ADMIN_PASSWORD);
    }

    public static String adminKubeconfig() {
        return XTFConfig.get(OPENSHIFT_ADMIN_KUBECONFIG);
    }

    public static String masterUsername() {
        return XTFConfig.get(OPENSHIFT_MASTER_USERNAME);
    }

    public static String masterPassword() {
        return XTFConfig.get(OPENSHIFT_MASTER_PASSWORD);
    }

    public static String masterKubeconfig() {
        return XTFConfig.get(OPENSHIFT_MASTER_KUBECONFIG);
    }

    public static String pullSecret() {
        return XTFConfig.get(OPENSHIFT_PULL_SECRET);
    }

    /**
     * @return For backwards-compatibility reasons, also returns the value of xtf.openshift.token if xtf.openshift.master.token
     *         not specified
     */
    public static String masterToken() {
        String masterToken = XTFConfig.get(OPENSHIFT_MASTER_TOKEN);
        if (masterToken == null) {
            return XTFConfig.get(OPENSHIFT_TOKEN);
        }
        return masterToken;
    }

    public static String routeDomain() {
        return XTFConfig.get(OPENSHIFT_ROUTE_DOMAIN);
    }
}
