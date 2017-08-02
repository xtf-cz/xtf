package cz.xtf.sso.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum ProtocolType {
	OPENID_CONNECT("openid-connect"),
	SAML("saml");

	@Getter
	private String label;
}
