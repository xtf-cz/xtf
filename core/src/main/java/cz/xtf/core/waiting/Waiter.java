package cz.xtf.core.waiting;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * An object that waits on condition to be met.
 */
public interface Waiter {
	long DEFAULT_INTERVAL = 1_000L;

	/**
	 * Sets waiter timeout after which waiters stops waiting. Timeout is logged
	 * in case of setting {@link Waiter#logPoint(LogPoint)} to anything else then
	 * {@link LogPoint#NONE}
	 *
	 * @param millis timeout in milliseconds
	 * @return this
	 */
	Waiter timeout(long millis);

	/**
	 * Sets waiter timeout after which waiters stops waiting. Timeout is logged
	 * in case of setting {@link Waiter#logPoint(LogPoint)} to anything else then
	 * {@link LogPoint#NONE}
	 *
	 * @param timeUnit timeUnit that is converts time {@code t} to milliseconds
	 * @param t timeout in {@code timeUnit}
	 * @return this
	 */
	Waiter timeout(TimeUnit timeUnit, long t);

	/**
	 * Sets waiter conditions check interval.
	 *
	 * @param millis interval in milliseconds
	 * @return this
	 */
	Waiter interval(long millis);

	/**
	 * Sets waiter conditions check interval.
	 *
	 * @param timeUnit timeUnit that is converts time {@code t} to milliseconds
	 * @param t interval in milliseconds
	 * @return this
	 */
	Waiter interval(TimeUnit timeUnit, long t);

	/**
	 * Sets waiting reason.
	 *
	 * @param reason what the waiters what upon.
	 * @return this
	 */
	Waiter reason(String reason);

	/**
	 * Sets waiters logPoints.
	 *
	 * @param logPoint what points of waiting should be logged.
	 * @return this
	 * @see LogPoint
	 */
	Waiter logPoint(LogPoint logPoint);

	/**
	 * Waits till condition is met.
	 *
	 * @return true if wanted condition was met, false if unwanted state condition was met
	 * @throws WaiterException in case of timeout
	 */
	boolean waitFor();

	/**
	 * Object for configurable conditional logging of waiting.
	 */
	@Slf4j
	enum LogPoint {
		NONE, START, END, BOTH;

		/**
		 * Logs start of waiting. Its time and reason in case of {@link LogPoint#START} or {@link LogPoint#BOTH}
		 *
		 * @param reason reason of waiting
		 * @param millis waiting timeout on condition
		 */
		public void logStart(String reason, long millis) {
			if (this.equals(START) || this.equals(BOTH)) log.info("Waiting up to {}. Reason: {}", DurationFormatUtils.formatDurationWords(millis, true, true), reason);
		}

		/**
		 * Logs end of waiting. Its time and reason in case of {@link LogPoint#END} or {@link LogPoint#BOTH}
		 *
		 * @param reason reason of waiting
		 * @param millis waiting timeout on condition
		 */
		public void logEnd(String reason, long millis) {
			if (this.equals(END) || this.equals(BOTH)) log.info("Finished waiting after {}. Reason: {}", DurationFormatUtils.formatDurationWords(millis, true, true), reason);
		}
	}
}
