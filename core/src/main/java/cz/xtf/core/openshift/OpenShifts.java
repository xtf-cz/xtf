package cz.xtf.core.openshift;

import cz.xtf.core.config.OpenShiftConfig;

import java.net.MalformedURLException;

public class OpenShifts {
	private static OpenShift adminUtil;
	private static OpenShift masterUtil;

	public static OpenShift admin() {
		if(adminUtil == null) {
			adminUtil = OpenShifts.admin(OpenShiftConfig.namespace());
		}
		return adminUtil;
	}

	public static OpenShift admin(String namespace) {
		return OpenShifts.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword());
	}

	public static OpenShift master() {
		if(masterUtil == null) {
			masterUtil = OpenShifts.master(OpenShiftConfig.namespace());
		}
		return masterUtil;
	}

	public static OpenShift master(String namespace) {
		if(OpenShiftConfig.token() == null) {
			return OpenShifts.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword());
		} else {
			return OpenShifts.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.token());
		}
	}

	public static OpenShift get(String masterUrl, String namespace, String username, String password) {
		try {
			return new OpenShift(masterUrl, namespace, username, password);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}

	public static OpenShift get(String masterUrl, String namespace, String token) {
		try {
			return new OpenShift(masterUrl, namespace, token);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}
}
