package cz.xtf.openshift.imagestream;

import cz.xtf.TestConfiguration;
import cz.xtf.openshift.VersionRegistry;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Register of images.
 */
public class ImageRegistry {

	private static final String REGISTRY_HOSTNAME = "registry-docker.";
	private static final String INTERNAL_REGISTRY = "[internal]";
	private static ImageRegistry instance;

	private ImageRegistry() {
		// singleton class, do not initialize directly
	}

	public static ImageRegistry get() {
		if (instance == null) {
			instance = new ImageRegistry();
		}
		return instance;
	}

	public String eap() {
		return normalize(TestConfiguration.get().readValue(
				TestConfiguration.IMAGE_EAP_PREFIX + VersionRegistry.get().eap().getMajorVersion()));
	}

	public String eapPrevious() {
		return normalize(TestConfiguration.get().readValue(
				TestConfiguration.IMAGE_EAP_PREFIX + VersionRegistry.get().eap().getMajorVersion() + TestConfiguration.IMAGE_PREVIOUS_SUFFIX));
	}

	public String tomcat() {
		return normalize(TestConfiguration.get().readValue(
				TestConfiguration.IMAGE_EWS_PREFIX + VersionRegistry.get().ews().getMajorVersion()));
	}

	public String eap6() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_EAP_6));
	}

	public String eap6Previous() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_EAP_6 + TestConfiguration.IMAGE_PREVIOUS_SUFFIX));
	}

	public String eap7() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_EAP_7));
	}

	public String eap7Previous() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_EAP_7 + TestConfiguration.IMAGE_PREVIOUS_SUFFIX));
	}

	public String jdg() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_JDG));
	}

	public String jdgPrevious() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_JDG + TestConfiguration.IMAGE_PREVIOUS_SUFFIX));
	}

	public String jdgClient() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_JDG_CLIENT));
	}

	public String jdv() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_JDV));
	}

	public String jdvClient() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_JDV_CLIENT));
	}

	public String jdvOdbcTest() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_JDV_ODBC_TEST_IMAGE));
	}

	public String tomcat7() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_TOMCAT7));
	}

	public String tomcat8() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_TOMCAT8));
	}

	public String amq() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_AMQ));
	}

	public String postgresql() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_POSTGRES));
	}

	public String derby() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_DERBY));
	}

	public String mysql() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_MYSQL));
	}

	public String mongodb() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_MONGO));
	}

	public String nfs() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_NFS));
	}

	public String fuseJavaMain() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_FUSE_JAVA_MAIN));
	}

	public String fuseKaraf() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_FUSE_KARAF));
	}

	public String fuseEap() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_FUSE_EAP));
	}

	public String brms() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_BRMS));
	}

	public String bpms() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_BPMS));
	}

	public String bpmsPrevious() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_BPMS + TestConfiguration.IMAGE_PREVIOUS_SUFFIX));
	}

	public String bpmsLdapTest() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_BPMS_LDAP_TEST_IMAGE));
	}

	public String sso() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_SSO));
	}

	public String ssoPrevious() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_SSO + TestConfiguration.IMAGE_PREVIOUS_SUFFIX));
	}

	public String phantomJs() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_PHANTOMJS));
	}

	public String h2() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_H2));
	}
	
	public String msa() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_MSA));
	}

	public String zipkin() {
		return normalize(TestConfiguration.get()
				.readValue(TestConfiguration.IMAGE_ZIPKIN));
	}

	public String squid() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_SQUID));
	}

	public String tcpProxy() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_TCP_PROXY));
	}

	public String midlewareManagerService() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_MM_SERVICE));
	}

	public String midlewareManagerStorage() {
		return normalize(TestConfiguration.get().readValue(TestConfiguration.IMAGE_MM_DATASTORE));
	}

	private String normalize(final String image) {
		if (image != null && image.startsWith(INTERNAL_REGISTRY)) {
			return image.replace(INTERNAL_REGISTRY, REGISTRY_HOSTNAME + TestConfiguration.routeDomain() + ":80");
		}
		return image;
	}

	public static Image toImage(final String image) {
		return new Image(image);
	}

	public static boolean isVersionAtLeast(final BigDecimal version, final String image) {
		final String imageTag = new Image(image).getImageTag();

		final String[] split = imageTag.split("-");

		try {
			return new BigDecimal(split[0]).compareTo(version) >= 0;
		} catch (final NumberFormatException x) {
			// no tag, or latest, we default to being always the greatest version
			return true;
		}
	}

	public static Optional<String> imageStreamVersion(final String image) {
		final String imageTag = new Image(image).getImageTag();
		final String[] split = imageTag.split("-");
		if (split.length == 2) {
			return Optional.of(split[0]);
		}
		else if(split.length == 1) {
			if (imageTag.matches("\\d+\\.\\d+")) {
				return Optional.of(imageTag);
			}
		}

		return Optional.empty();
	}

	public static class Image {
		// REGISTRY[:PORT]/USER/REPO[:TAG]
		private String imageRegistry; // including port
		private String imageUser;
		private String imageRepo;
		private String imageTag;
		private String image;

		public Image(final String image) {

			this.image = image;

			final String[] slashTokens = image.split("/");
			final String repoTag;

			switch (slashTokens.length) {
				case 1:imageRegistry = ""; imageUser = ""; repoTag = slashTokens[0];break;
				case 2:imageRegistry = ""; imageUser = slashTokens[0]; repoTag = slashTokens[1];break;
				case 3:imageRegistry = slashTokens[0]; imageUser = slashTokens[1]; repoTag = slashTokens[2]; break;
				default: throw new IllegalArgumentException("image '" + image + "' should have one or two '/' characters");
			}

			final String[] tokens = repoTag.split(":");
			switch (tokens.length) {
				case 1:
					this.imageRepo = tokens[0];
					this.imageTag = "";
					break;
				case 2:
					this.imageRepo = tokens[0];
					this.imageTag = tokens[1];
					break;
				default: throw new IllegalArgumentException("repoTag '" + repoTag + "' should have zero or two ':' characters");
			}
		}

		/** @returns REGISTRY[:PORT]/USER/REPO (everything except tag) */
		@Deprecated
		public String getImageName() {
			if (imageRegistry.isEmpty()) {
				if (imageUser.isEmpty()) {
					return imageRepo;
				} else {
					return imageUser + "/" + imageRepo;
				}
			} else {
				return imageRegistry + "/" + imageUser + "/" + imageRepo;
			}
		}

		public String getImageTag() {
			return this.imageTag;
		}

		public String getImageRegistry() {
			return this.imageRegistry;
		}

		public String getImageUser() {
			return this.imageUser;
		}

		public String getImageRepo() {
			return this.imageRepo;
		}

		@Override
		public String toString() {
			return image;
		}
	}
}
