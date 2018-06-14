package cz.xtf.core.config;

public class WaitingConfig {
	public static final String WAITING_TIMEOUT = "xtf.waiting.timeout";

	private static final String WAITING_TIMEOUT_DEFAULT = "180000";

	public static long timeout() {
		return Long.parseLong(XTFConfig.get(WAITING_TIMEOUT, WAITING_TIMEOUT_DEFAULT));
	}
}
