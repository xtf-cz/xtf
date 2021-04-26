package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.event.Level;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.failfast.FailFastCheck;
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
     * Set the level of severity for a log message.
     *
     * @param level what level of severity should be used to log the message.
     * @return this
     * @see Level
     */
    default Waiter level(Level level) {
        throw new UnsupportedOperationException("Method level hasn't been implemented.");
    }

    /**
     * Sets waiters execution for each iteration.
     *
     * @param runnable code to be executed upon successful waiting.
     * @return this
     */
    default Waiter onIteration(Runnable runnable) {
        throw new UnsupportedOperationException("Method onIteration hasn't been implemented.");
    }

    /**
     * Sets waiters successful awaiting execution.
     *
     * @param runnable code to be executed upon successful waiting.
     * @return this
     */
    default Waiter onSuccess(Runnable runnable) {
        throw new UnsupportedOperationException("Method onSuccess hasn't been implemented.");
    }

    /**
     * Sets waiters failed awaiting execution.
     *
     * @param runnable code to be executed upon failed waiting.
     * @return this
     */
    default Waiter onFailure(Runnable runnable) {
        throw new UnsupportedOperationException("Method onFailure hasn't been implemented.");
    }

    /**
     * Sets waiters timed out awaiting execution.
     *
     * @param runnable code to be executed upon timed out waiting.
     * @return this
     */
    default Waiter onTimeout(Runnable runnable) {
        throw new UnsupportedOperationException("Method onTimeout hasn't been implemented.");
    }

    /**
     * Sets waiters fail fast function that indicates (returns true) if there is an error state and waiting should not
     * proceed.
     *
     * @param failFast returns true if waiting has failed.
     * @return this
     */
    default Waiter failFast(FailFastCheck failFast) {
        throw new UnsupportedOperationException("Method failFast hasn't been implemented.");
    }

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
        NONE,
        START,
        END,
        BOTH;

        /**
         * Logs start of waiting. Its time and reason in case of {@link LogPoint#START} or {@link LogPoint#BOTH}
         *
         * @param reason reason of waiting
         * @param millis waiting timeout on condition
         */
        public void logStart(String reason, long millis) {
            logStart(reason, millis, WaitingConfig.level());
        }

        /**
         * Logs start of waiting using the selected logging <code>{@param level}</code>. Its time and reason in case
         * of {@link LogPoint#START} or {@link LogPoint#BOTH}.
         *
         * @param reason reason of waiting
         * @param millis waiting timeout on condition
         * @param level logging severity of <code>{@param reason}</code> log
         */
        public void logStart(String reason, long millis, Level level) {
            if (this.equals(START) || this.equals(BOTH))
                logMessage(level, String.format("Waiting up to %s. Reason: %s",
                        DurationFormatUtils.formatDurationWords(millis, true, true), reason));
        }

        /**
         * Logs end of waiting using the selected logging <code>{@param level}</code>. Its time and reason in case
         * of {@link LogPoint#END} or {@link LogPoint#BOTH}
         *
         * @param reason reason of waiting
         * @param millis waiting timeout on condition
         */
        public void logEnd(String reason, long millis) {
            logEnd(reason, millis, WaitingConfig.level());
        }

        /**
         * Logs end of waiting. Its time and reason in case of {@link LogPoint#END} or {@link LogPoint#BOTH}
         *
         * @param reason reason of waiting
         * @param millis waiting timeout on condition
         * @param level logging severity of {@param reason} log
         */
        public void logEnd(String reason, long millis, Level level) {
            if (this.equals(END) || this.equals(BOTH))
                logMessage(level, String.format("Finished waiting after %s. Reason: %s",
                        DurationFormatUtils.formatDurationWords(millis, true, true), reason));
        }

        private void logMessage(Level level, String message) {
            switch (level) {
                case TRACE:
                    log.trace(message);
                    break;
                case DEBUG:
                    log.debug(message);
                    break;
                case INFO:
                    log.info(message);
                    break;
                case WARN:
                    log.warn(message);
                    break;
                case ERROR:
                    log.error(message);
                    break;
            }
        }
    }
}
