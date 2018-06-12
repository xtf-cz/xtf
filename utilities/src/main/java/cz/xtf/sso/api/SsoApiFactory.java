package cz.xtf.sso.api;

public class SsoApiFactory {
	public static final SsoApiType DEFAULT_API_TYPE = SsoApiType.REST;

	public static SsoApi get(String authUrl, String realm) {
		return get(authUrl, realm, DEFAULT_API_TYPE);
	}

	public static SsoApi get(String authUrl, String realm, SsoApiType type) {
		switch (type) {
			case REST:
				return getRestApi(authUrl, realm);
			case WEBUI:
				return getWebUIApi(authUrl, realm);
			default:
				return getRestApi(authUrl, realm);
		}
	}

	public static SsoApi getRestApi(String authUrl, String realm) {
		return SsoRestApi.get(authUrl, realm);
	}

	public static SsoApi getWebUIApi(String authUrl, String realm) {
		return SsoWebUIApi.get(authUrl, realm);
	}

	public enum SsoApiType {
		REST, WEBUI
	}
}
