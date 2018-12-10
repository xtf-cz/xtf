package cz.xtf.gitlab.config;

import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.config.XTFConfig;

public class GitLabConfig {
	private static final String GITLAB_URL = "xtf.gitlab.url";
	private static final String GITLAB_TOKEN = "xtf.gitlab.token";
	private static final String GITLAB_USERNAME = "xtf.gitlab.username";
	private static final String GITLAB_PASSWORD = "xtf.gitlab.password";
	private static final String GITLAB_GROUP = "xtf.gitlab.group";
	private static final String GITLAB_PROTOCOL = "xtf.gitlab.protocol";
	private static final String GITLAB_SSH_PRIVATE_KEY_PATH = "xtf.gitlab.ssh.identity_file";

	private static String trimOrNull(final String x) {
		if (StringUtils.isBlank(x)) {
			return null;
		}

		return x.trim();
	}

	public static String token() {
		return trimOrNull(XTFConfig.get(GITLAB_TOKEN));
	}

	public static String username() {
		return trimOrNull(XTFConfig.get(GITLAB_USERNAME));
	}

	public static String password() {
		return trimOrNull(XTFConfig.get(GITLAB_PASSWORD));
	}

	public static String url() throws MalformedURLException {
		return trimOrNull(XTFConfig.get(GITLAB_URL));
	}

	public static String group() {
		final String group = trimOrNull(XTFConfig.get(GITLAB_GROUP));
		return group == null ? OpenShiftConfig.namespace() : group;
	}

	public static Protocol protocol() {
		if ("ssh".equalsIgnoreCase(trimOrNull(XTFConfig.get(GITLAB_PROTOCOL)))) {
			return Protocol.SSH;
		}

		return Protocol.HTTP;
	}

	public static String sshIdentityFile() {
		return trimOrNull(XTFConfig.get(GITLAB_SSH_PRIVATE_KEY_PATH));
	}

	public enum Protocol {
		HTTP,
		SSH
	}
}
