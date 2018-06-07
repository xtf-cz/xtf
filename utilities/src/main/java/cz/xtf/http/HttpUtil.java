package cz.xtf.http;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;

public class HttpUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtil.class);

	public static void waitForHttp(String urlString, int code) throws Exception {
		waitForHttp(urlString, TestConfiguration.maxHttpTries(), code);
	}

	public static void waitForHttp(String urlString, int timeout, int code) throws Exception {
		LOGGER.info("Waiting for {} HTTP {}, timeout {} s", urlString, code, timeout);
		HttpClient.get(urlString).waitForCode(code, timeout, TimeUnit.SECONDS);
	}

	public static void waitForAuthHttp(final String urlString, final String username, final String password, final int timeout, final int code) throws Exception {
		LOGGER.info("Waiting for {} HTTP {}, timeout {} s", urlString, code, timeout);
		HttpClient.get(urlString).basicAuth(username, password).preemptiveAuth().waitForCode(code, timeout, TimeUnit.SECONDS);
	}

	public static void waitForHttps(String urlString, int code, Path trustStorePath, char[] password) throws Exception {
		LOGGER.info("Waiting for {} HTTPS {}", urlString, code);
		HttpClient.get(urlString).trustStore(trustStorePath, password).waitForCode(code, TestConfiguration.maxHttpTries(), TimeUnit.SECONDS);
	}

	public static void waitForHttpOk(String urlString) throws Exception {
		waitForHttp(urlString, 200);
	}

	public static void waitForHttpsOk(String urlString, Path trustStorePath, char[] password) throws Exception {
		waitForHttps(urlString, 200, trustStorePath, password);
	}

	public static String httpGet(String urlString) throws Exception {
		LOGGER.debug("HTTP GET {}", urlString);
		return HttpClient.get(urlString).response();
	}

	public static String httpPost(String urlString, String postData) throws Exception {
		LOGGER.debug("HTTP POST {}, data: {}", urlString, postData);
		return HttpClient.post(urlString).data(postData, null).response();
	}

	public static String httpPost(String urlString, String postData, ContentType contentType) throws Exception {
		LOGGER.debug("HTTP POST {}, data: {}", urlString, postData);
		return HttpClient.post(urlString).data(postData, contentType).response();
	}

	public static String httpGetBasicAuth(String urlString, String username, String password) throws Exception {
		LOGGER.debug("HTTP GET with basic auth {}:{} {}", username, password, urlString);
		return HttpClient.get(urlString).basicAuth(username, password).response();
	}

	public static String httpDeleteBasicAuth(String urlString, String username, String password) throws Exception {
		LOGGER.debug("HTTP DELETE with basic auth {}:{} {}", username, password, urlString);
		return HttpClient.delete(urlString).basicAuth(username, password).response();
	}

	public static String httpPutBasicAuth(String urlString, String username, String password, String putData, ContentType contentType)
			throws Exception {
		LOGGER.debug("HTTP PUT with basic auth {}:{} {}, data: {}", username, password, urlString, putData);
		return HttpClient.put(urlString).basicAuth(username, password).data(putData, contentType).response();
	}

	public static String httpsGet(String urlString, Path trustStorePath, char[] password) throws Exception {
		LOGGER.debug("HTTP GET {} truststore {} {}", urlString, trustStorePath, password);
		return HttpClient.get(urlString).trustStore(trustStorePath, password).response();
	}

	public static String httpsGetBearerAuth(String urlString, String token, Path trustStorePath, char[] password) throws Exception {
		LOGGER.debug("HTTPS GET {} truststore {} {} token {}", urlString, trustStorePath, password, token);
		return HttpClient.get(urlString).bearerAuth(token).trustStore(trustStorePath, password).response();
	}

	public static int codeFromHttpGet(String urlString) throws Exception {
		LOGGER.debug("Code from HTTP GET {}", urlString);
		try {
			return HttpClient.get(urlString).code();
		}
		catch (ClientProtocolException e) {
			return 503;
		}
	}

}
