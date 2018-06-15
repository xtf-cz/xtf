package cz.xtf.core.openshift;

import cz.xtf.core.config.OpenShiftConfig;

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
		return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword());
	}

	public static OpenShift master() {
		if(masterUtil == null) {
			masterUtil = OpenShifts.master(OpenShiftConfig.namespace());
		}
		return masterUtil;
	}

	public static OpenShift master(String namespace) {
		if(OpenShiftConfig.token() == null) {
			return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword());
		} else {
			return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.token());
		}
	}
}
