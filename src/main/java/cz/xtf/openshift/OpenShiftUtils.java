package cz.xtf.openshift;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;

import cz.xtf.TestConfiguration;
import cz.xtf.http.HttpClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;

@Slf4j
public class OpenShiftUtils {
	private static OpenShiftUtil adminUtil;
	private static OpenShiftUtil masterUtil;

	public static OpenShiftUtil admin() {
		if(adminUtil == null) {
			String masterUrl = TestConfiguration.masterUrl();
			String namespace = TestConfiguration.masterNamespace();
			String username = TestConfiguration.adminUsername();
			String password = TestConfiguration.adminPassword();

			adminUtil = getUtil(masterUrl, namespace, username, password);
		}
		return adminUtil;
	}

	public static OpenShiftUtil admin(String namespace) {
		String masterUrl = TestConfiguration.masterUrl();
		String username = TestConfiguration.adminUsername();
		String password = TestConfiguration.adminPassword();

		return getUtil(masterUrl, namespace, username, password);
	}

	public static OpenShiftUtil master() {
		if(masterUtil == null) {
		    if(TestConfiguration.getMasterToken() == null) {
		        String masterUrl = TestConfiguration.masterUrl();
		        String namespace = TestConfiguration.masterNamespace();
		        String username = TestConfiguration.masterUsername();
		        String password = TestConfiguration.masterPassword();

		        masterUtil = getUtil(masterUrl, namespace, username, password);
		    } else {
		        String namespace = TestConfiguration.masterNamespace();
		        String masterUrl = TestConfiguration.masterUrl();
		        masterUtil = getUtil(masterUrl, namespace, TestConfiguration.getMasterToken());
		    }
		}
		return masterUtil;
	}

	public static OpenShiftUtil master(String namespace) {
		String masterUrl = TestConfiguration.masterUrl();
		if(TestConfiguration.getMasterToken() == null) {
			String username = TestConfiguration.masterUsername();
			String password = TestConfiguration.masterPassword();
			return getUtil(masterUrl, namespace, username, password);
		} else {
			return getUtil(masterUrl, namespace, TestConfiguration.getMasterToken());
		}
	}

	public static OpenShiftUtil getUtil(String masterUrl, String namespace, String username, String password) {
		try {
			return new OpenShiftUtil(masterUrl, namespace, username, password);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}

	public static OpenShiftUtil getUtil(String masterUrl, String namespace, String token) {
		try {
			return new OpenShiftUtil(masterUrl, namespace, token);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("OpenShift Master URL is malformed!");
		}
	}

	public static String getMasterToken() {
		if(TestConfiguration.getMasterToken() != null) {
			return TestConfiguration.getMasterToken();
		} else {
			String masterUrl = TestConfiguration.masterUrl();
			String username = TestConfiguration.masterUsername();
			String password = TestConfiguration.masterPassword();

			try {
				String url = masterUrl + "/oauth/authorize?response_type=token&client_id=openshift-challenging-client";
				List<Header> headers = HttpClient.get(url)
						.basicAuth(username, password)
						.preemptiveAuth()
						.disableRedirect()
						.responseHeaders();
				Optional<Header> location = headers.stream().filter(h -> "Location".equals(h.getName())).findFirst();
				if (location.isPresent()) {
					log.debug("Location: {}", location.get().getValue());
					String token = StringUtils.substringBetween(location.get().getValue(), "#access_token=", "&");
					log.debug("Oauth token: {}", token);
					return token;
				}
				log.debug("Location header with token not found");
				return null;
			} catch (IOException e) {
				log.error("Error getting token from master", e);
				return null;
			}
		}
	}
}
