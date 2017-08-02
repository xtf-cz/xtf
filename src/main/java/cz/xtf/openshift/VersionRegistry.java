package cz.xtf.openshift;

import cz.xtf.TestConfiguration;

public class VersionRegistry {
	private static final String DEFAULT_VERSION_EAP = "6";
	private static final String DEFAULT_VERSION_EWS = "8";
	private static final String DEFAULT_VERSION_JDG = "6";
	private static VersionRegistry instance;

	private VersionRegistry() {

	}

	public static VersionRegistry get() {
		if (instance == null) {
			instance = new VersionRegistry();
		}
		return instance;
	}

	public ProductVersion eap() {
		final String major = TestConfiguration.get().readValue(
				TestConfiguration.VERSION_EAP, DEFAULT_VERSION_EAP);
		return new ProductVersion(major, TestConfiguration.get().readValue(
				String.format("%s.%s.string", TestConfiguration.VERSION_EAP,
						major)));
	}

	public boolean isEap6() {
		return eap().getMajorVersion().startsWith("6");
	}

	public boolean isEap7() {
		return eap().getMajorVersion().startsWith("7");
	}

	public ProductVersion ews() {
		final String major = TestConfiguration.get().readValue(
				TestConfiguration.VERSION_EWS, DEFAULT_VERSION_EWS);
		return new ProductVersion(major, TestConfiguration.get().readValue(
				String.format("%s.%s.string", TestConfiguration.VERSION_EWS,
						major)));
	}

	public ProductVersion jdg() {
		final String major = DEFAULT_VERSION_JDG;
		return new ProductVersion(major, TestConfiguration.get().readValue(
				TestConfiguration.VERSION_JDG));
	}
}
