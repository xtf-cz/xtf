package cz.xtf.sso.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum Provider {
	OIDC_JBOSS_XML_SUBSYSTEM("keycloak-oidc-jboss-subsystem", "Keycloak OIDC JBoss Subsystem XML"),
	SAML_JBOSS_XML_SUBSYSTEM("keycloak-saml-subsystem", "Keycloak SAML Wildfly/JBoss Subsystem"),
	OIDC_KEYCLOAK_JSON("keycloak-oidc-keycloak-json", "Keycloak OIDC JSON");

	@Getter
	private final String providerId;

	@Getter
	private final String webUiLabel;
}
