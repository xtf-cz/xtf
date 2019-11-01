package cz.xtf.core.config;

import org.slf4j.event.Level;

public class WaitingConfig {
	public static final String WAITING_TIMEOUT = "xtf.waiting.timeout";
	public static final String WAITING_LOG_LEVEL = "xtf.waiting.log.level";

	private static final String WAITING_TIMEOUT_DEFAULT = "180000";
	private static final String WAITING_LOG_LEVEL_DEFAULT = "INFO";

	public static long timeout() {
		return Long.parseLong(XTFConfig.get(WAITING_TIMEOUT, WAITING_TIMEOUT_DEFAULT));
	}

	public static Level level() {
		return Level.valueOf(XTFConfig.get(WAITING_LOG_LEVEL, WAITING_LOG_LEVEL_DEFAULT).toUpperCase());
	}
}
