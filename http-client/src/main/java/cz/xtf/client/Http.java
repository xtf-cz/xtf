package cz.xtf.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.LinkedList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.http.HttpEntityEnclosingRequest;
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
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

public class Http {

    public static Http get(String url) throws MalformedURLException {
        return new Http(url, new HttpGet(url));
    }

    public static Http post(String url) throws MalformedURLException {
        return new Http(url, new HttpPost(url));
    }

    public static Http put(String url) throws MalformedURLException {
        return new Http(url, new HttpPut(url));
    }

    public static Http delete(String url) throws MalformedURLException {
        return new Http(url, new HttpDelete(url));
    }

    private final URL url;
    private final HttpUriRequest request;
    private final Collection<ClientCookie> cookies;
    private final HttpWaiters waiters;

    private SSLConnectionSocketFactory sslsf;
    private String username;
    private String password;
    private boolean preemptiveAuth;
    private boolean disableRedirect;

    private Http(String url, HttpUriRequest request) throws MalformedURLException {
        this.url = new URL(url);
        this.request = request;
        this.cookies = new LinkedList<>();

        this.preemptiveAuth = false;
        this.disableRedirect = false;
        this.waiters = new HttpWaiters(this);
    }

    public Http basicAuth(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public Http bearerAuth(String token) {
        request.setHeader("Authorization", String.format("Bearer %s", token));
        return this;
    }

    public Http preemptiveAuth() {
        preemptiveAuth = true;
        return this;
    }

    public Http disableRedirect() {
        this.disableRedirect = true;
        return this;
    }

    public Http trustAll() {
        try {
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustAllStrategy()).build();
            sslsf = new SSLConnectionSocketFactory(sslContext, new String[] { "TLSv1", "TLSv1.2" }, null,
                    new NoopHostnameVerifier());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Can't create SSL connection factory trusting everything", e);
        }
        return this;
    }

    public Http trustStore(Path trustStorePath, String trustStorePassword) {
        return trustStore(trustStorePath, trustStorePassword, new DefaultHostnameVerifier());
    }

    public Http trustStore(Path trustStorePath, String trustStorePassword, HostnameVerifier verifier) {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(trustStorePath.toFile(), trustStorePassword.toCharArray()).build();
            sslsf = new SSLConnectionSocketFactory(sslContext, new String[] { "TLSv1", "TLSv1.2" }, null, verifier);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Can't create SSL connection factory", e);
        }
        return this;
    }

    public Http header(String name, String value) {
        request.addHeader(name, value);
        return this;
    }

    public Http cookie(String name, String value) {
        cookies.add(new BasicClientCookie(name, value));
        return this;
    }

    public Http cookie(String domain, String name, String value) {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(domain);
        cookies.add(cookie);
        return this;
    }

    public Http data(String data, ContentType contentType) {
        if (!(request instanceof HttpEntityEnclosingRequest))
            throw new IllegalStateException("Can't add data to " + request.getClass().getSimpleName());

        ((HttpEntityEnclosingRequest) request).setEntity(new StringEntity(data, contentType));
        return this;
    }

    public HttpResponseParser execute() throws IOException {
        try (CloseableHttpClient hc = build()) {
            try (CloseableHttpResponse response = hc.execute(request)) {
                HttpResponseParser parser = new HttpResponseParser(response);
                EntityUtils.consume(response.getEntity());
                return parser;
            }
        }
    }

    private CloseableHttpClient build() {
        HttpClientBuilder builder = HttpClients.custom();

        if (sslsf != null) {
            builder.setSSLSocketFactory(sslsf);
        }

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

        if (preemptiveAuth && username != null && password != null) {
            // https://issues.apache.org/jira/browse/CODEC-89 Using this instead of encodeBase64String, as some versions of apache-commons have chunking enabled by default
            String credentials = StringUtils
                    .newStringUtf8(Base64.encodeBase64((username + ":" + password).getBytes(StandardCharsets.UTF_8), false));
            request.setHeader("Authorization", String.format("Basic %s", credentials));
        }

        if (!cookies.isEmpty()) {
            CookieStore cookieStore = new BasicCookieStore();
            cookies.forEach(cookieStore::addCookie);
            builder.setDefaultCookieStore(cookieStore);
        }

        return builder.build();
    }

    public HttpWaiters waiters() {
        return waiters;
    }

    URL getUrl() {
        return url;
    }
}
