package cz.xtf.openshift;

import cz.xtf.TestConfiguration;
import cz.xtf.http.HttpClient;
import org.jboss.dmr.ModelNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class KubernetesVersion {
	private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesVersion.class);

	private static KubernetesVersion INSTANCE;

	private final int major;
	private final int minor;

	public static synchronized KubernetesVersion get() {
		if (INSTANCE != null) {
			return INSTANCE;
		}

		try {
			String versionJson = HttpClient.get(TestConfiguration.masterUrl() + "/version").response();
			ModelNode version = ModelNode.fromJSONString(versionJson);
			int major = Integer.parseInt(version.get("major").asString());
			int minor = Integer.parseInt(version.get("minor").asString());
			INSTANCE = new KubernetesVersion(major, minor);
		} catch (Exception e) {
			LOGGER.error("Error getting Kubernetes version from master", e);
			INSTANCE = new KubernetesVersion(0, 0);
		}

		return INSTANCE;
	}

	KubernetesVersion(int major, int minor) {
		this.major = major;
		this.minor = minor;
	}

	public int serviceNameLimit() {
		// see https://github.com/kubernetes/kubernetes/pull/29523
		if (major > 1 || major == 1 && minor >= 4) {
			return 63;
		}
		return 24;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof KubernetesVersion)) return false;
		KubernetesVersion that = (KubernetesVersion) o;
		return major == that.major && minor == that.minor;
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor);
	}

	@Override
	public String toString() {
		return major + "." + minor;
	}
}
