package cz.xtf.openshift;

import cz.xtf.TestConfiguration;

import java.net.MalformedURLException;

public class OpenShiftUtils {
	private static OpenShiftUtil adminUtil;
	private static OpenShiftUtil masterUtil;

	public static OpenShiftUtil adminUtil() {
		if(adminUtil == null) {
			String masterUrl = TestConfiguration.masterUrl();
			String namespace = TestConfiguration.masterNamespace();
			String username = TestConfiguration.adminUsername();
			String password = TestConfiguration.adminPassword();

			adminUtil = getUtil(masterUrl, namespace, username, password);
		}
		return adminUtil;
	}

	public static OpenShiftUtil masterUtil() {
		if(masterUtil == null) {
			String masterUrl = TestConfiguration.masterUrl();
			String namespace = TestConfiguration.masterNamespace();
			String username = TestConfiguration.masterUsername();
			String password = TestConfiguration.masterPassword();

			masterUtil = getUtil(masterUrl, namespace, username, password);
		}
		return masterUtil;
	}

	public static OpenShiftUtil masterUtil(String namespace) {
		String masterUrl = TestConfiguration.masterUrl();
		String username = TestConfiguration.masterUsername();
		String password = TestConfiguration.masterPassword();

		return getUtil(masterUrl, namespace, username, password);
	}

	public static OpenShiftUtil getUtil(String masterUrl, String namespace, String username, String password) {
		try {
			return new OpenShiftUtil(masterUrl, namespace, username, password);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}
}
