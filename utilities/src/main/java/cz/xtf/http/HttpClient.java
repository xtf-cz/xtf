package cz.xtf.http;

import static cz.xtf.keystore.KeystoreType.JKS;
import static cz.xtf.tuple.Tuple.pair;
import static cz.xtf.tuple.Tuple.triple;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.keystore.KeystoreType;
import cz.xtf.tuple.Tuple;
import cz.xtf.wait.WaitingException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class HttpClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);
	private static final long HTTP_WAIT_TIMEOUT = 1L;
	private static final long HTTP_POLL_INTERVAL = 1L;

	private final URL url;
	private HttpUriRequest request;
	private SSLConnectionSocketFactory sslsf = null;
	private boolean preemptiveAuth;
	private String username;
	private String password;
	private CloseableHttpClient httpClient;
	private boolean disableRedirect = false;
	private final List<BasicClientCookie> cookies = new LinkedList<>();
	private KeystoreType keystoreType = JKS;

	@FunctionalInterface
	public static interface IOFunction<T, R> {
		public R apply(final T parameter) throws IOException;
	}

	public static HttpClient get(String urlString) throws MalformedURLException {
		return new HttpClient(urlString, new HttpGet(urlString));
	}

	public static HttpClient post(String urlString) throws MalformedURLException {
		return new HttpClient(urlString, new HttpPost(urlString));
	}

	public static HttpClient put(String urlString) throws MalformedURLException {
		return new HttpClient(urlString, new HttpPut(urlString));
	}

	public static HttpClient delete(String urlString) throws MalformedURLException {
		return new HttpClient(urlString, new HttpDelete(urlString));
	}

	public static HttpClient reuseable(final String urlString,
			final String username, final String password) throws IOException {
		HttpClient hc = new HttpClient(urlString, null).basicAuth(username, password);
		hc.httpClient = hc.build();
		return hc;
	}

	public static HttpClient reuseable(final String urlString) throws IOException {
		HttpClient hc = new HttpClient(urlString, null);
		hc.httpClient = hc.build();
		return hc;
	}

	public static HttpClient reuseableNoRetry(final String urlString) throws IOException {
		HttpClient hc = new HttpClient(urlString, null);
		hc.httpClient = hc.build(HttpClientBuilder::disableAutomaticRetries);
		return hc;
	}

	private HttpClient(String url, HttpUriRequest request) throws MalformedURLException {
		this.url = new URL(url);
		this.request = request;
	}

	public HttpClient basicAuth(String username, String password) {
		this.username = username;
		this.password = password;
		return this;
	}

	public HttpClient bearerAuth(String token) {
		request.setHeader("Authorization", String.format("Bearer %s", token));
		return this;
	}

	public HttpClient preemptiveAuth() {
		preemptiveAuth = true;
		return this;
	}

	public HttpClient disableRedirect() {
		this.disableRedirect = true;
		return this;
	}

	public HttpClient trustStore(Path trustStorePath, String trustStorePassword) {
		return trustStore(trustStorePath, trustStorePassword.toCharArray(), false);
	}

	public HttpClient trustStore(Path trustStorePath, char[] trustStorePassword) {
		return trustStore(trustStorePath, trustStorePassword, false);
	}

	public HttpClient trustStore(Path trustStorePath, String trustStorePassword, boolean strictHostnameChecking) {
		return trustStore(trustStorePath, trustStorePassword.toCharArray(), strictHostnameChecking);
	}

	public HttpClient trustStore(Path trustStorePath, char[] trustStorePassword, boolean strictHostnameChecking) {
		sslsf = createSSLConnectionSocketFactory(trustStorePath, trustStorePassword, strictHostnameChecking, keystoreType);
		return this;
	}

	public HttpClient trustStoreType(KeystoreType type) {
		keystoreType = type;
		return this;
	}

	public HttpClient header(String name, String value) {
		request.setHeader(name, value);
		return this;
	}

	public HttpClient addHeader(String name, String value) {
		request.addHeader(name, value);
		return this;
	}

	public HttpClient cookie(final String name, final String value) {
		BasicClientCookie cookie = new BasicClientCookie(name, value);
		cookies.add(cookie);
		return this;
	}
	
	public HttpClient cookie(final String domain, final String name, final String value) {
		BasicClientCookie cookie = new BasicClientCookie(name, value);
		cookie.setDomain(domain);
		cookies.add(cookie);
		return this;
	}

	public HttpClient data(String data, ContentType contentType) {
		if (!(request instanceof HttpEntityEnclosingRequest)) {
			throw new IllegalStateException("Can't add data to " + request.getClass().getSimpleName());
		}

		HttpEntity entity;
		if (data != null) {
			entity = new StringEntity(data, contentType);
		} else {
			BasicHttpEntity cEntity = new BasicHttpEntity();
			cEntity.setContent(new ByteArrayInputStream(new byte[0]));
			if (contentType != null) {
				cEntity.setContentType(contentType.toString());
			}
			entity = cEntity;
		}
		((HttpEntityEnclosingRequest) request).setEntity(entity);

		return this;
	}

	public int code() throws IOException {
		try (CloseableHttpClient hc = build()) {
			try (CloseableHttpResponse response = hc.execute(request)) {
				int code = response.getStatusLine().getStatusCode();
				EntityUtils.consume(response.getEntity());

				LOGGER.debug("Result code: {}", code);

				return code;
			}
		}
	}

	public Tuple.Pair<String, Integer> responseAndCode(final CloseableHttpClient hc) throws IOException {
		return response(hc, (response) -> pair(response.getEntity() == null ? null : EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode()));
	}

	public Tuple.Pair<String, Integer> responseAndCode() throws IOException {
		try (CloseableHttpClient hc = build()) {
			return responseAndCode(hc);
		}
	}

	public Tuple.Pair<String, Integer> responseAndCodeNoClose() throws IOException {
		return responseAndCode(httpClient);
	}

	public String response() throws IOException {
		try (CloseableHttpClient hc = build()) {
			return response(hc);
		}
	}

	public List<Header> responseHeaders() throws IOException {
		try (CloseableHttpResponse response = build().execute(request)) {
			return Arrays.asList(response.getAllHeaders());
		}
	}

	public byte[] responseBytes() throws IOException {
		return response(build(), response -> EntityUtils.toByteArray(response.getEntity()));
	}

	public String responseNoClose() throws IOException {
		return response(httpClient);
	}

	private <T> T response (final CloseableHttpClient hc, final HttpResponseTransform<T> transform) throws IOException {
		try (CloseableHttpResponse response = hc.execute(request)) {
			T ret = transform.transform(response);
			EntityUtils.consume(response.getEntity());
			LOGGER.debug("Response: {}", ret);
			return ret;
		}
	}

	private String response(final CloseableHttpClient hc) throws IOException {
		return response(hc, response -> response.getEntity() == null ? null : EntityUtils.toString(response.getEntity()));
	}

	public Tuple.Pair<String, List<Header>> responseAndResponseHeaders() throws IOException {
		return response(build(), response -> pair(
				response.getEntity() == null ? null : EntityUtils.toString(response.getEntity()),
				Arrays.asList(response.getAllHeaders())));
	}
	
	public Tuple.Triple<String, Integer, List<Header>> responseAndCodeAndResponseHeaders() throws IOException {
		return response(build(), response -> triple(
				response.getEntity() == null ? null : EntityUtils.toString(response.getEntity()),
				response.getStatusLine().getStatusCode(),
				Arrays.asList(response.getAllHeaders())));
	}

	public void waitForOk() {
		waitForCode(200, HTTP_WAIT_TIMEOUT, TimeUnit.MINUTES);
	}

	public void waitForOk(long timeout, TimeUnit unit) {
		waitForCode(200, timeout, unit);
	}

	public void waitForCode(int code) {
		waitForCode(code, HTTP_WAIT_TIMEOUT, TimeUnit.MINUTES);
	}

	public void waitForCode(int code, long timeout, TimeUnit unit) {
		long startTime = System.currentTimeMillis();
		int lastCode = -1;
		do {
			try {
				TimeUnit.SECONDS.sleep(HTTP_POLL_INTERVAL);
				lastCode = code();
			} catch (IOException | InterruptedException ex) {
				LOGGER.debug("Error retrieving HTTP code", ex);
			}
		} while (lastCode != code && System.currentTimeMillis() - startTime < TimeUnit.MILLISECONDS.convert(timeout, unit));

		if (lastCode != code) {
			throw new WaitingException(String.format("Cannot %s %s %s after %s %s. Last HTTP status: %s",
					request.getMethod(), url, code, timeout, unit.name(), lastCode));
		}
	}

	private CloseableHttpClient build() throws IOException {
		return build(null);
	}

	private CloseableHttpClient build(final Consumer<HttpClientBuilder> customizeBuilder) throws IOException {
		LOGGER.debug("HTTP {} {}", request, url);
		if (httpClient != null) {
			LOGGER.debug("Existing HttpClient re-used");
			return httpClient;
		}
		if (sslsf == null) {
			sslsf = createSSLConnectionSocketFactory(null, null, false);
		}

		HttpClientBuilder builder = HttpClients.custom()
				.setRetryHandler(new StandardHttpRequestRetryHandler(3, true) {
					@Override
					public boolean retryRequest(IOException exception,
							int executionCount, HttpContext context) {
						try {
							TimeUnit.SECONDS.sleep(3);
						} catch (InterruptedException e) {
						}
						return super.retryRequest(exception, executionCount,
								context);
					}
				}).setSSLSocketFactory(sslsf);
		if (username != null) {
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(
					new AuthScope(url.getHost(), url.getPort() == -1 ? url.getDefaultPort() : url.getPort()),
					new UsernamePasswordCredentials(username, password));

			builder.setDefaultCredentialsProvider(credsProvider);
		}
		if (disableRedirect) {
			builder.disableRedirectHandling();
		}

		addPreemptiveAuthorizationHeaders();

		if (customizeBuilder != null) {
			customizeBuilder.accept(builder);
		}
		if (!cookies.isEmpty()) {
			final CookieStore cookieStore = new BasicCookieStore();
			cookies.forEach(cookieStore::addCookie);
			builder.setDefaultCookieStore(cookieStore);
		}
		return builder.build();
	}

	public HttpClient addPreemptiveAuthorizationHeaders() {
		if (preemptiveAuth && username != null && password != null && request != null) {
			// https://issues.apache.org/jira/browse/CODEC-89 Using this instead of encodeBase64String, as some versions of apache-commons have chunking enabled by default
			String credentials = StringUtils.newStringUtf8(Base64.encodeBase64((username + ":" + password).getBytes(StandardCharsets.UTF_8), false));
			request.setHeader("Authorization", String.format("Basic %s", credentials));
		}
		return this;
	}

	private static SSLConnectionSocketFactory createSSLConnectionSocketFactory(Path path, char[] password, boolean strict) {
		return createSSLConnectionSocketFactory(path, password, strict, KeystoreType.JKS);
	}

	private static SSLConnectionSocketFactory createSSLConnectionSocketFactory(Path path, char[] password, boolean strict, KeystoreType keystoreType) {
		try {
			SSLContextBuilder builder = SSLContexts.custom();
			if (path != null) {
				KeyStore trustStore = KeyStore.getInstance(keystoreType.name());
				try (InputStream is = Files.newInputStream(path)) {
					trustStore.load(is, password);
				}

				builder.loadTrustMaterial(trustStore);
			} else {
				builder.loadTrustMaterial(null, new TrustEverythingStrategy());
			}

			X509HostnameVerifier verifier;
			if (strict) {
				verifier = SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER;
			} else {
				verifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
			}

			return new SSLConnectionSocketFactory(builder.build(), new String[] {"TLSv1", "TLSv1.2"}, null, verifier);
		} catch (IOException | GeneralSecurityException ex) {
			throw new RuntimeException("Can't create SSL connection factory", ex);
		}
	}

	public HttpClient request(HttpUriRequest request) {
		this.request = request;
		addPreemptiveAuthorizationHeaders();
		return this;
	}

	public Tuple.Pair<String, Integer> guaranteedRequest(final IOFunction<HttpClient, Tuple.Pair<String, Integer>> request) {
		for (int i = 0; i < TestConfiguration.maxHttpTries(); i++) {
			Tuple.Pair<String, Integer> reply;
			try {
				reply = request.apply(this);
				if (reply.getSecond() < 500 || reply.getSecond() >= 600) {
					return reply;
				}
				LOGGER.info("Request returned {}", reply.getSecond());
			} catch (IOException e1) {
				LOGGER.info("IO Exception", e1);
			}
			try {
				TimeUnit.SECONDS.sleep(HTTP_POLL_INTERVAL);
			} catch (InterruptedException e) {
				LOGGER.error("Interruted", e);
				throw new IllegalStateException("Interrupted", e);
			}
		}
		throw new RuntimeException("Http call failed");
	}

	@FunctionalInterface
	private interface HttpResponseTransform<T> {
		T transform(HttpResponse response) throws IOException;
	}
}
