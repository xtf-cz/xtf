package cz.xtf.core.config;

public final class OpenShiftConfig {
	public static final String OPENSHIFT_URL = "xtf.openshift.url";
	public static final String OPENSHIFT_TOKEN = "xtf.openshift.token";
	public static final String OPENSHIFT_VERSION = "xtf.openshift.version";
	public static final String OPENSHIFT_NAMESPACE = "xtf.openshift.namespace";
	public static final String OPENSHIFT_BINARY_PATH = "xtf.openshift.binary.path";
	public static final String OPENSHIFT_ADMIN_USERNAME = "xtf.openshift.admin.username";
	public static final String OPENSHIFT_ADMIN_PASSWORD = "xtf.openshift.admin.password";
	public static final String OPENSHIFT_MASTER_USERNAME = "xtf.openshift.master.username";
	public static final String OPENSHIFT_MASTER_PASSWORD = "xtf.openshift.master.password";
	public static final String OPENSHIFT_ROUTE_DOMAIN = "xtf.openshift.route_domain";

	public static String url() {
		return XTFConfig.get(OPENSHIFT_URL);
	}

	public static String token() {
		return XTFConfig.get(OPENSHIFT_TOKEN);
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

	public static String masterUsername() {
		return XTFConfig.get(OPENSHIFT_MASTER_USERNAME);
	}

	public static String masterPassword() {
		return XTFConfig.get(OPENSHIFT_MASTER_PASSWORD);
	}

	public static String routeDomain() {
		return XTFConfig.get(OPENSHIFT_ROUTE_DOMAIN);
	}
}
