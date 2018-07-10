package cz.xtf.client;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HttpResponseParser {
	private final int code;
	private final String response;
	private final List<Header> headers;

	HttpResponseParser(HttpResponse httpResponse) throws IOException {
		this.code = httpResponse.getStatusLine().getStatusCode();
		this.headers = Arrays.asList(httpResponse.getAllHeaders());
		this.response = httpResponse.getEntity() == null ? null : EntityUtils.toString(httpResponse.getEntity());
	}

	public int code() {
		return code;
	}

	public String response() throws IOException {
		return response;
	}

	public List<Header> headers() {
		return headers;
	}
}
