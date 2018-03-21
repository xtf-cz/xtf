package cz.xtf.openshift;

import cz.xtf.TestConfiguration;

import java.net.MalformedURLException;

public class OpenShiftUtils {
	private static OpenShiftUtil adminUtil;
	private static OpenShiftUtil masterUtil;

	public static OpenShiftUtil admin() {
		if(adminUtil == null) {
			String masterUrl = TestConfiguration.masterUrl();
			String namespace = TestConfiguration.masterNamespace();
			String username = TestConfiguration.adminUsername();
			String password = TestConfiguration.adminPassword();

			adminUtil = getUtil(masterUrl, namespace, username, password);
		}
		return adminUtil;
	}

	public static OpenShiftUtil admin(String namespace) {
		String masterUrl = TestConfiguration.masterUrl();
		String username = TestConfiguration.adminUsername();
		String password = TestConfiguration.adminPassword();

		return getUtil(masterUrl, namespace, username, password);
	}

	public static OpenShiftUtil master() {
		if(masterUtil == null) {
		    if(!TestConfiguration.openshiftOnline()) {
		        String masterUrl = TestConfiguration.masterUrl();
		        String namespace = TestConfiguration.masterNamespace();
		        String username = TestConfiguration.masterUsername();
		        String password = TestConfiguration.masterPassword();

		        masterUtil = getUtil(masterUrl, namespace, username, password);
		    } else {
		        String namespace = TestConfiguration.masterNamespace();
		        String masterUrl = TestConfiguration.masterUrl();
		        masterUtil = getUtil(masterUrl, namespace, TestConfiguration.getMasterToken());
		    }
		}
		return masterUtil;
	}

	public static OpenShiftUtil master(String namespace) {
		String masterUrl = TestConfiguration.masterUrl();
		if(!TestConfiguration.openshiftOnline()) {
			String username = TestConfiguration.masterUsername();
			String password = TestConfiguration.masterPassword();
			return getUtil(masterUrl, namespace, username, password);
		} else {
			return getUtil(masterUrl, namespace, TestConfiguration.getMasterToken());
		}
	}

	public static OpenShiftUtil getUtil(String masterUrl, String namespace, String username, String password) {
		try {
			return new OpenShiftUtil(masterUrl, namespace, username, password);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}

	public static OpenShiftUtil getUtil(String masterUrl, String namespace, String token) {
		try {
			return new OpenShiftUtil(masterUrl, namespace, token);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}
}
