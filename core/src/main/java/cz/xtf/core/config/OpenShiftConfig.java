package cz.xtf.core.config;

public final class OpenShiftConfig {
    public static final String OPENSHIFT_URL = "xtf.openshift.url";
    public static final String OPENSHIFT_TOKEN = "xtf.openshift.token";
    public static final String OPENSHIFT_VERSION = "xtf.openshift.version";
    public static final String OPENSHIFT_NAMESPACE = "xtf.openshift.namespace";
    public static final String OPENSHIFT_BINARY_PATH = "xtf.openshift.binary.path";
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

    public static String namespace() {
        return XTFConfig.get(OPENSHIFT_NAMESPACE);
    }

    public static String binaryPath() {
        return XTFConfig.get(OPENSHIFT_BINARY_PATH);
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
