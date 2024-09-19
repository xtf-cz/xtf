package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.slf4j.event.Level;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.failfast.ExponentialTimeBackoff;
import cz.xtf.core.waiting.failfast.FailFastCheck;

/**
 * A simple waiter enabling user to wait until a success or a fail condition will be satisfied
 */
public class SimpleWaiter implements Waiter {
    private final BooleanSupplier successCondition;
    private BooleanSupplier failureCondition;

    private Runnable onIteration;
    private Runnable onSuccess;
    private Runnable onFailure;
    private Runnable onTimeout;

    private long timeout;
    private long interval;

    private String reason;
    private LogPoint logPoint;
    private Level level;
    private FailFastCheck failFast;

    /**
     * Construct new SimpleWaiter with a defined success condition. To perform the wait, you need to call
     * {@link SimpleWaiter#waitFor}.
     *
     * @param successCondition whenever this supplier will provide {@code true}, waiting stops.
     */
    public SimpleWaiter(BooleanSupplier successCondition) {
        this(successCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), null);
    }

    /**
     * Construct new SimpleWaiter with a defined success condition and a reason. The reason will be used in log
     * messages associated with the wait. To perform the wait, you need to call {@link SimpleWaiter#waitFor}.
     *
     * @param successCondition whenever the provided condition will provide {@code true}, waiting stops.
     * @param reason description of the reason why this wait is being performed
     */
    public SimpleWaiter(BooleanSupplier successCondition, String reason) {
        this(successCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), reason);
    }

    /**
     * Construct new SimpleWaiter with a defined success condition and a timeout. The timeout denotes the maximum time
     * window in which periodical condition evaluation will be done. To perform the wait, you need to call
     * {@link SimpleWaiter#waitFor}.
     *
     * @param successCondition whenever this supplier will provide {@code true}, waiting stops.
     * @param timeoutUnit unit of the timeout
     * @param timeout maximum time window in which periodical condition evaluation will be done
     */
    public SimpleWaiter(BooleanSupplier successCondition, TimeUnit timeoutUnit, long timeout) {
        this(successCondition, timeoutUnit, timeout, null);
    }

    /**
     * Construct new SimpleWaiter with a defined success condition, reason, and a timeout. The timeout denotes the
     * maximum time window in which periodical condition evaluation will be done. To perform the wait, you need to call
     * {@link SimpleWaiter#waitFor}.
     *
     * @param successCondition whenever this supplier will provide {@code true}, waiting stops.
     * @param timeoutUnit unit of the timeout
     * @param timeout maximum time window in which periodical condition evaluation will be done
     * @param reason description of the reason why this wait is being performed. Will be used in associated log
     *        messages.
     */
    public SimpleWaiter(BooleanSupplier successCondition, TimeUnit timeoutUnit, long timeout, String reason) {
        this.successCondition = successCondition;
        this.failureCondition = () -> false;

        this.failFast = () -> false;

        this.onIteration = () -> {
        };
        this.onSuccess = () -> {
        };
        this.onFailure = () -> {
        };
        this.onTimeout = () -> {
        };

        this.timeout = timeoutUnit.toMillis(timeout);
        this.interval = DEFAULT_INTERVAL;
        this.reason = reason;
        this.level = WaitingConfig.level();
        this.logPoint = reason == null ? LogPoint.NONE : LogPoint.START;
    }

    /**
     * Set a failure condition. Note that this condition will be evaluated first when the evaluation routine.
     *
     * @param failureCondition whenever this supplier provides true, the wait will stop.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter failureCondition(BooleanSupplier failureCondition) {
        this.failureCondition = failureCondition;
        return this;
    }

    /**
     * Set the maximum time windows in which the periodical evaluation of failure and success conditions will be done.
     *
     * @param millis timeout in milliseconds
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter timeout(long millis) {
        this.timeout = millis;
        return this;
    }

    /**
     * Set the maximum time windows in which the periodical evaluation of failure and success conditions will be done.
     *
     * @param timeUnit timeUnit that is converts time {@code t} to milliseconds
     * @param t timeout in {@code timeUnit}
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter timeout(TimeUnit timeUnit, long t) {
        this.timeout = timeUnit.toMillis(t);
        return this;
    }

    /**
     * Set the amount of time the wait will rest after checking all the conditions in the evaluation routine
     *
     * @param millis interval in milliseconds
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter interval(long millis) {
        this.interval = millis;
        return this;
    }

    /**
     * Set the amount of time the wait will rest after checking all the conditions in the evaluation routine.
     *
     * @param timeUnit timeUnit that is converts time {@code t} to milliseconds
     * @param t interval in milliseconds
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter interval(TimeUnit timeUnit, long t) {
        this.interval = timeUnit.toMillis(t);
        return this;
    }

    /**
     * Set the description of the reason why this wait is being performed. Will be used in associated log messages.
     *
     * @param reason what the waiters what upon.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter reason(String reason) {
        this.reason = reason;
        this.logPoint = LogPoint.START;
        return this;
    }

    /**
     * Set XTF Waiter specific log configuration. It can be set to any enum value from
     * {@link cz.xtf.core.waiting.Waiter.LogPoint} allowing to specify whether the wait start, end, both events or none
     * of them should be logged.
     *
     * @param logPoint what points of waiting should be logged.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter logPoint(LogPoint logPoint) {
        this.logPoint = logPoint;
        return this;
    }

    /**
     * Set the log level of messages produced during this wait.
     *
     * @param level what level of severity should be used to log the message.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter level(Level level) {
        this.level = level;
        return this;
    }

    /**
     * Set the {@link Runnable} to be executed upon each iteration of the evaluation cycle. This will be run after all
     * conditions were evaluated.
     *
     * @param runnable code to be executed upon successful waiting.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter onIteration(Runnable runnable) {
        onIteration = runnable;
        return this;
    }

    /**
     * Set the {@link Runnable} to be executed after the {@link SimpleWaiter#successCondition} has been met.
     *
     * @param runnable code to be executed upon successful waiting.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter onSuccess(Runnable runnable) {
        onSuccess = runnable;
        return this;
    }

    /**
     * Set the {@link Runnable} to be executed after the {@link SimpleWaiter#failureCondition} has been met.
     *
     * @param runnable code to be executed upon successful waiting.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter onFailure(Runnable runnable) {
        onFailure = runnable;
        return this;
    }

    /**
     * Set the condition which will be evaluated before other conditions are checked. If evaluated to true, the wait
     * will stop with a {@link WaiterException}. Note that non-blocking wait with exponential time backoff is used in
     * the evaluation routine for this check meaning that this condition will not be evaluated during each routine
     * cycle because the backoff time gradually increases while the interval is a constant.
     *
     * @param failFast returns true if waiting has failed.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter failFast(FailFastCheck failFast) {
        this.failFast = failFast;
        return this;
    }

    /**
     * Set a {@link Runnable} to be executed upon timeout. This will be executed as a last thing outside the evaluation
     * routine before the {@link WaiterException} is thrown.
     *
     * @param runnable code to be executed upon timed out waiting.
     * @return instance of this {@link SimpleWaiter}
     */
    public SimpleWaiter onTimeout(Runnable runnable) {
        onTimeout = runnable;
        return this;
    }

    /**
     * Waits for the success or a failure condition to be met or the timeout to occur.
     *
     * @return true if the success condition is met, false if the failure condition is met
     * @throws WaiterException if the timeout occurs, the blocking wait thread is interrupted or a fast fail check is
     *         evaluated to true.
     */
    @Override
    public boolean waitFor() {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout;

        logPoint.logStart(reason, timeout, level);

        //backoff counter for fast fail condition
        ExponentialTimeBackoff backoff = ExponentialTimeBackoff.builder()
                .blocking(false)
                .maxBackoff(32000)
                .build();

        while (System.currentTimeMillis() < endTime) {
            if (backoff.next() && failFast.hasFailed()) {
                logPoint.logEnd(reason + " (fail fast method failure)", System.currentTimeMillis() - startTime, level);
                throw new WaiterException(failFast.reason());
            }
            if (failureCondition.getAsBoolean()) {
                logPoint.logEnd(reason + " (Failure)", System.currentTimeMillis() - startTime, level);
                onFailure.run();
                return false;
            }
            if (successCondition.getAsBoolean()) {
                logPoint.logEnd(reason + " (Success)", System.currentTimeMillis() - startTime, level);
                onSuccess.run();
                return true;
            }
            onIteration.run();

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                throw new WaiterException("Thread has been interrupted!");
            }
        }
        logPoint.logEnd(reason + " (Time out)", System.currentTimeMillis() - startTime, level);
        onTimeout.run();
        throw new WaiterException(reason);
    }
}
