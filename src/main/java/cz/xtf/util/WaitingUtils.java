package cz.xtf.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Util class for waiting.
 */
@Slf4j
public final class WaitingUtils {

	/**
	 * Default number of milliseconds to wait.
	 */
	public static final long DEFAULT_MILLIS_TO_WAIT = 1000;

	/**
	 * Default logged waiting reason.
	 */
	public static final String DEFAULT_REASON = "unspecified";

	private WaitingUtils() {
		// util class, do not initialize
	}

	/**
	 * Waits for the {@link #DEFAULT_MILLIS_TO_WAIT}, logs {@link #DEFAULT_REASON} as the waiting reason.
	 *
	 * @see #waitSilently(long, String)
	 */
	public static void waitSilently() {
		waitSilently(DEFAULT_MILLIS_TO_WAIT, DEFAULT_REASON);
	}

	/**
	 * Waits for the specified {@code millis}, logs {@link #DEFAULT_REASON} as the waiting reason.
	 *
	 * @see #waitSilently(long, String)
	 *
	 * @param millis the number of milliseconds to wait
	 */
	public static void waitSilently(long millis) {
		waitSilently(millis, DEFAULT_REASON);
	}

	/**
	 * Waits for the {@link #DEFAULT_MILLIS_TO_WAIT}, logs the specified {@code reason} as the waiting reason.
	 *
	 * @see #waitSilently(long, String)
	 *
	 * @param reason the waiting reason, will be logged
	 */
	public static void waitSilently(String reason) {
		waitSilently(DEFAULT_MILLIS_TO_WAIT, reason);
	}

	/**
	 * Waits for the specified {@code millis} using {@link Thread#sleep(long)} before the program will continue, logs
	 * the specified {@code reason} as the waiting reason.
	 *
	 * <p>
	 * In case of {@link InterruptedException} the error is logged but no exception is thrown.
	 * </p>
	 *
	 * @param millis the number of milliseconds to wait
	 * @param reason the waiting reason, will be logged
	 */
	public static void waitSilently(long millis, String reason) {
		try {
			log.info("action=wait status=START reason={} millis={}", reason, millis);
			Thread.sleep(millis);
			log.info("action=wait status=FINISH reason={} millis={}", reason, millis);
		} catch (InterruptedException ex) {
			log.warn("action=wait status=ERROR reason={} millis={}", reason, millis, ex);
		}
	}
}
