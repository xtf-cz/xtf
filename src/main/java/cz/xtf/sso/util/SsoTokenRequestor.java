package cz.xtf.sso.util;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import cz.xtf.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.jboss.dmr.ModelNode;

/**
 * Utility for requesting JWT/OAuth2 token from RH-SSO instance
 *
 * @author jcechace
 */
@Slf4j
public class SsoTokenRequestor {

	public static class TokenHolder {
		private final ModelNode tokenResponse;

		private TokenHolder(ModelNode tokenResponse) {
			this.tokenResponse = tokenResponse;
		}

		/**
		 * @return full JSON response to the token request
		 */
		public ModelNode getJson() {
			return tokenResponse;
		}

		/**
		 * @return access token string
		 */
		public String getAccessToken() {
			return tokenResponse.get("access_token").asString();
		}

	}

	/**
	 * List of availabl
	 */
	public enum GrantType {
		PASSWORD;

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	private final Map<String, String> params;
	private final String ssoAuthUrl;
	private final String realm;

	public SsoTokenRequestor(String authUrl, String realm, Map<String, String> params) {
		this.ssoAuthUrl = authUrl;
		this.realm = realm;
		this.params = new HashMap<>(params);
	}

	public SsoTokenRequestor(String ssoAuthUrl, String realm) {
		this(ssoAuthUrl, realm, Collections.emptyMap());
	}

	/**
	 * Factory method
	 * @param ssoAuthUrl url of SSO instance (/auth should be included)
	 * @return instance of {@link SsoTokenRequestor} class
	 */
	public static SsoTokenRequestor fromMasterRealmOf(String ssoAuthUrl) {
		return new SsoTokenRequestor(ssoAuthUrl, "master");
	}

	/**
	 * @param username username request parameter
	 * @return this instance
	 */
	public SsoTokenRequestor username(String username) {
		params.put("username", username);
		return this;
	}

	/**
	 * @param password password request parameter
	 * @return this instance
	 */
	public SsoTokenRequestor password(String password) {
		params.put("password", password);
		return this;
	}

	/**
	 * @param grantType grant_type request parameter
	 * @return this instance
	 */
	public SsoTokenRequestor grantType(GrantType grantType) {
		params.put("grant_type", grantType.toString());
		return this;
	}

	/**
	 * @param clientId client_id request parameter
	 * @return this instance
	 */
	public SsoTokenRequestor clientId(String clientId) {
		params.put("client_id", clientId);
		return this;
	}

	/**
	 * @param clientSecret client_secret request parameter
	 * @return this instance
	 */
	public SsoTokenRequestor clientSecret(String clientSecret) {
		params.put("client_secret", clientSecret);
		return this;
	}

	private String buildParams() {
		return params.entrySet().stream()
			.map(e -> e.getKey() + "=" + e.getValue())
			.collect(joining("&"));
	}

	/**
	 * Request new token from sso instance
	 *
	 * @return this instance
	 * @throws IOException in case of request failure
	 */
	public TokenHolder requestNewToken() throws IOException {
		final String params = buildParams();
		final String url = ssoAuthUrl + "/realms/" + realm + "/protocol/openid-connect/token";

		log.info("Token request: {}", url);
		log.info("Request params: {} ", params);
		final String response = HttpClient.post(url)
			.data(params, ContentType.APPLICATION_FORM_URLENCODED)
			.response();

		ModelNode tokenResponse = ModelNode.fromJSONString(response);
		log.debug("Token response: {}", tokenResponse);

		return new TokenHolder(tokenResponse);
	}

}
