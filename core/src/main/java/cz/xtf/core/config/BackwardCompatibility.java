package cz.xtf.core.config;

// Helper class to ensure core's backward compatibility with original utilities
class BackwardCompatibility {

    static void updateProperties() {
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_URL, ocpUrl());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_TOKEN, token());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_NAMESPACE, namespace());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_MASTER_USERNAME, masterUsername());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_MASTER_PASSWORD, masterPassword());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_ADMIN_USERNAME, adminUsername());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_ADMIN_PASSWORD, adminPassword());
        BackwardCompatibility.setIfAbsentAndNotNull(OpenShiftConfig.OPENSHIFT_ROUTE_DOMAIN, routeDomain());
    }

    private static void setIfAbsentAndNotNull(String property, String value) {
        if (XTFConfig.get(property) == null && value != null) {
            XTFConfig.setProperty(property, value);
        }
    }

    private static String ocpUrl() {
        String url1 = XTFConfig.get("xtf.config.master.url");
        String url2 = XTFConfig.get("xtf.config.domain");
        String url3 = System.getenv().get("MASTER_URL");
        String url4 = System.getenv().get("DOMAIN");

        url2 = url2 == null ? null : "https://api." + url2 + ":8443";
        url4 = url4 == null ? null : "https://api." + url4 + ":8443";

        return url1 != null ? url1 : (url2 != null ? url2 : (url3 != null ? url3 : url4));
    }

    private static String token() {
        String token1 = XTFConfig.get("xtf.config.master.token");
        String token2 = System.getenv("MASTER_TOKEN");

        return token1 != null ? token1 : token2;
    }

    private static String namespace() {
        String namespace1 = XTFConfig.get("xtf.config.master.namespace");
        String namespace2 = System.getenv().get("MASTER_NAMESPACE");

        return namespace1 != null ? namespace1 : namespace2;
    }

    private static String masterUsername() {
        String username1 = XTFConfig.get("xtf.config.master.username");
        String username2 = System.getenv().get("MASTER_USERNAME");

        return username1 != null ? username1 : username2;
    }

    private static String masterPassword() {
        String password1 = XTFConfig.get("xtf.config.master.password");
        String password2 = System.getenv().get("MASTER_PASSWORD");

        return password1 != null ? password1 : password2;
    }

    private static String adminUsername() {
        String username1 = XTFConfig.get("xtf.config.master.admin.username");
        String username2 = System.getenv().get("ADMIN_USERNAME");

        return username1 != null ? username1 : username2;
    }

    private static String adminPassword() {
        String password1 = XTFConfig.get("xtf.config.master.admin.password");
        String password2 = System.getenv().get("ADMIN_PASSWORD");

        return password1 != null ? password1 : password2;
    }

    private static String routeDomain() {
        String configDomain = XTFConfig.get("xtf.config.domain");
        String configRouteDomain = XTFConfig.get("xtf.config.route_domain");
        String systemEnvDomain = System.getenv().get("DOMAIN");

        return configDomain != null ? "apps." + configDomain
                : (systemEnvDomain != null ? "apps." + systemEnvDomain : configRouteDomain);

    }
}
