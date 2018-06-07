package cz.xtf.openshift.builder.secret;

/**
 * @see https://github.com/kubernetes/kubernetes/blob/master/docs/design/secrets.md#secret-api-resource
 */
public enum SecretType {

	OPAQUE("Opaque"),
	SERVICE_ACCOUNT("kubernetes.io/service-account-token"),
	DOCKERCFG("kubernetes.io/dockercfg");

	private final String text;

	SecretType(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return text;
	}

}
