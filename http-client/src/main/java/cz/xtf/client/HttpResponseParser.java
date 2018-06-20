package cz.xtf.client;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HttpResponseParser {
	private final HttpResponse response;

	HttpResponseParser(HttpResponse response) {
		this.response = response;
	}

	public int code() {
		return response.getStatusLine().getStatusCode();
	}

	public String response() throws IOException {
		return EntityUtils.toString(response.getEntity());
	}

	public List<Header> headers() {
		return Arrays.asList(response.getAllHeaders());
	}
}
