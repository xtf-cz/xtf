package cz.xtf;

import cz.xtf.openshift.VersionRegistry;

public class TestProjectProfileResolver {

	private static TestProjectProfileResolver instance;

	private TestProjectProfileResolver() {

	}

	public static TestProjectProfileResolver get() {
		if (instance == null) {
			instance = new TestProjectProfileResolver();
		}
		return instance;
	}

	public String getProfileName(final String parentModuleName) {

		final String prefix = getProfileNamePrefix(parentModuleName);
		final String suffix = getProfileNameSuffix(parentModuleName);

		if (prefix == null || suffix == null) {
			return null;
		}

		return prefix + "-" + suffix;
	}

	public String getProfileNamePrefix(final String parentModuleName) {
		switch (parentModuleName) {
		case "parent-eap":
			return "eap";
		case "parent-fis":
			return null;
		case "parent-fis-sb":
			return null;
		case "parent-fis-karaf":
			return null;
		case "parent-msa-sb":
		case "parent-msa-vertx":
		case "parent-msa-wildfly-swarm":
			return "msa";
		default:
			throw new IllegalArgumentException("Unknown parent module name");
		}
	}

	public String getProfileNameSuffix(final String parentModuleName) {
		switch (parentModuleName) {
		case "parent-eap":
			return VersionRegistry.get().eap().getMajorVersion();
		case "parent-fis":
			return null;
		case "parent-fis-sb":
			return null;
		case "parent-fis-karaf":
			return null;
		case "parent-msa-sb":
		case "parent-msa-vertx":
		case "parent-msa-wildfly-swarm":
			return TestConfiguration.getMsaVersion();
		default:
			throw new IllegalArgumentException("Unknown parent module name");
		}
	}
}
