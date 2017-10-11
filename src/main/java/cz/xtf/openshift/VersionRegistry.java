package cz.xtf.openshift;

import cz.xtf.TestConfiguration;

public class VersionRegistry {
	private static final String DEFAULT_VERSION_EAP = "6";
	private static final String DEFAULT_VERSION_EWS = "8";
	private static final String DEFAULT_VERSION_JDG = "6";
	private static final String DEFAULT_VERSION_JDV = "6";
	private static final String DEFAULT_VERSION_KIE = "6";
	private static final String DEFAULT_VERSION_JDK = "1.8";
	private static final String DEFAULT_VERSION_SSO = "7";
	private static final String DEFAULT_VERSION_AMQ = "6";
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

	public ProductVersion jdv() {
		return new ProductVersion(DEFAULT_VERSION_JDV , TestConfiguration.get().readValue(TestConfiguration.VERSION_JDV));
	}

	public ProductVersion kie() {
		return new ProductVersion(DEFAULT_VERSION_KIE , TestConfiguration.get().readValue(TestConfiguration.VERSION_KIE));
	}

	public ProductVersion jdk() {
		return new ProductVersion(DEFAULT_VERSION_JDK , TestConfiguration.get().readValue(TestConfiguration.VERSION_JDK));
	}

	public ProductVersion sso() {
		return new ProductVersion(DEFAULT_VERSION_SSO , TestConfiguration.get().readValue(TestConfiguration.VERSION_SSO));
	}

	public ProductVersion amq() {
		return new ProductVersion(DEFAULT_VERSION_AMQ , TestConfiguration.get().readValue(TestConfiguration.VERSION_AMQ));
	}

}
