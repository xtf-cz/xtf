package cz.xtf.core.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;

import cz.xtf.core.waiting.SupplierWaiter;
import cz.xtf.core.waiting.Waiter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Https {

    public static Waiter doesUrlReturnOK(String url) {
        return doesUrlReturnCode(url, 200);
    }

    public static Waiter doesUrlReturnCode(String url, int expectedCode) {
        return doesUrlReturnCode(url, expectedCode, -1);
    }

    public static Waiter doesUrlReturnCode(String url, int expectedCode, int failCode) {
        Supplier<Integer> getCode = () -> {
            try {
                return Https.getCode(url);
            } catch (HttpsException e) {
                log.warn("Attempt to retrieve http code from {} has failed. ({})", url, e.getMessage());
                return -2;
            }
        };

        return new SupplierWaiter<>(getCode, x -> x == expectedCode, x -> x == failCode);
    }

    public static Waiter doesUrlResponseEqual(String url, String string) {
        return doesUrlResponseMatch(url, response -> response.equals(string));
    }

    public static Waiter doesUrlResponseStartWith(String url, String string) {
        return doesUrlResponseMatch(url, response -> response.startsWith(string));
    }

    public static Waiter doesUrlResponseContain(String url, String... strings) {
        return doesUrlResponseMatch(url, response -> Stream.of(strings).allMatch(response::contains));
    }

    public static Waiter doesUrlResponseMatch(String url, Predicate<String>... predicates) {
        Supplier<String> getContent = () -> {
            try {
                return Https.getContent(url);
            } catch (HttpsException e) {
                log.warn("Attempt to retrieve http content from {} has failed. ({})", url, e.getMessage());
                return "";
            }
        };

        return new SupplierWaiter<>(getContent, content -> Stream.of(predicates).allMatch(p -> p.test(content)));
    }

    public static int getCode(String url) {
        if (url.startsWith("https")) {
            return Https.httpsGetCode(url);
        } else {
            return Https.httpGetCode(url);
        }
    }

    public static String getContent(String url) {
        if (url.startsWith("https")) {
            return Https.httpsGetContent(url);
        } else {
            return Https.httpGetContent(url);
        }
    }

    public static int httpGetCode(String url) {
        return httpGetCode(Https.urlFromString(url));
    }

    public static int httpGetCode(URL url) {
        HttpURLConnection connection = Https.getHttpConnection(url);
        return Https.getConnectionCode(connection);
    }

    public static String httpGetContent(String url) {
        return httpGetContent(Https.urlFromString(url));
    }

    public static String httpGetContent(URL url) {
        HttpURLConnection connection = Https.getHttpConnection(url);
        return Https.getConnectionContent(connection);
    }

    public static HttpURLConnection getHttpConnection(URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            return connection;
        } catch (ProtocolException e) {
            throw new HttpsException("Seems that time and IT has changed. Please contact creators for feature update!", e);
        } catch (IOException e) {
            throw new HttpsException(e);
        }
    }

    public static int httpsGetCode(String url) {
        return httpsGetCode(Https.urlFromString(url));
    }

    public static int httpsGetCode(URL url) {
        HttpsURLConnection connection = Https.getHttpsConnection(url);
        return Https.getConnectionCode(connection);
    }

    public static String httpsGetContent(String url) {
        return httpsGetContent(Https.urlFromString(url));
    }

    public static String httpsGetContent(URL url) {
        HttpsURLConnection connection = Https.getHttpsConnection(url);
        return Https.getConnectionContent(connection);
    }

    public static void copyHttpsURLToFile(String source, File destination, int connectionTimeout, int readTimeout)
            throws IOException {
        copyHttpsURLToFile(Https.urlFromString(source), destination, connectionTimeout, readTimeout);
    }

    public static void copyHttpsURLToFile(URL source, File destination, int connectionTimeout, int readTimeout)
            throws IOException {
        URLConnection connection = getHttpsConnection(source);
        connection.setConnectTimeout(connectionTimeout);
        connection.setReadTimeout(readTimeout);
        FileUtils.copyInputStreamToFile(connection.getInputStream(), destination);
    }

    public static HttpsURLConnection getHttpsConnection(URL url) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { new TrustAllManager() }, new SecureRandom());

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier((s, session) -> true);
            connection.setRequestMethod("GET");

            return connection;
        } catch (NoSuchAlgorithmException | KeyManagementException | ProtocolException e) {
            throw new HttpsException("Seems that time and IT has changed. Please contact creators for feature update!", e);
        } catch (IOException e) {
            throw new HttpsException(e);
        }
    }

    private static int getConnectionCode(HttpURLConnection connection) {
        try {
            connection.connect();

            int code = connection.getResponseCode();

            connection.disconnect();
            return code;
        } catch (IOException e) {
            throw new HttpsException(e);
        }
    }

    private static String getConnectionContent(HttpURLConnection connection) {
        try {
            connection.connect();

            String content = Https.readContent(connection.getInputStream());

            connection.disconnect();
            return content;
        } catch (IOException e) {
            throw new HttpsException(e);
        }
    }

    private static String readContent(InputStream inputStream) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine).append("\n");
        }
        in.close();
        return content.toString().trim();
    }

    private static URL urlFromString(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new HttpsException("Ivalid url: " + url);
        }
    }

    public static class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
