package cz.xtf.junit5.config;

import cz.xtf.core.config.XTFConfig;

public class JUnitConfig {
	private static final String CLEAN_OPENSHIFT = "xtf.junit.clean_openshift";

	public static boolean cleanOpenShift() {
		return Boolean.valueOf(XTFConfig.get(CLEAN_OPENSHIFT, "false"));
	}
}
