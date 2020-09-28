package cz.xtf.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

public class HttpResponseParser {
    private final int code;
    private final String response;
    private final List<Header> headers;

    HttpResponseParser(HttpResponse httpResponse) throws IOException {
        this.code = httpResponse.getStatusLine().getStatusCode();
        this.headers = Arrays.asList(httpResponse.getAllHeaders());
        this.response = httpResponse.getEntity() == null ? null : EntityUtils.toString(httpResponse.getEntity()).trim();
    }

    public int code() {
        return code;
    }

    public String response() {
        return response;
    }

    public List<Header> headers() {
        return headers;
    }
}
