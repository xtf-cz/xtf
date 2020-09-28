package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.slf4j.event.Level;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.failfast.ExponentialTimeBackoff;
import cz.xtf.core.waiting.failfast.FailFastCheck;

public class SimpleWaiter implements Waiter {
    private BooleanSupplier successCondition;
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

    public SimpleWaiter(BooleanSupplier successCondition) {
        this(successCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), null);
    }

    public SimpleWaiter(BooleanSupplier successCondition, String reason) {
        this(successCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), reason);
    }

    public SimpleWaiter(BooleanSupplier successCondition, TimeUnit timeoutUnit, long timeout) {
        this(successCondition, timeoutUnit, timeout, null);
    }

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

    public SimpleWaiter failureCondition(BooleanSupplier failureCondition) {
        this.failureCondition = failureCondition;
        return this;
    }

    public SimpleWaiter timeout(long millis) {
        this.timeout = millis;
        return this;
    }

    public SimpleWaiter timeout(TimeUnit timeUnit, long t) {
        this.timeout = timeUnit.toMillis(t);
        return this;
    }

    public SimpleWaiter interval(long millis) {
        this.interval = millis;
        return this;
    }

    public SimpleWaiter interval(TimeUnit timeUnit, long t) {
        this.interval = timeUnit.toMillis(t);
        return this;
    }

    public SimpleWaiter reason(String reason) {
        this.reason = reason;
        this.logPoint = LogPoint.START;
        return this;
    }

    public SimpleWaiter logPoint(LogPoint logPoint) {
        this.logPoint = logPoint;
        return this;
    }

    public SimpleWaiter level(Level level) {
        this.level = level;
        return this;
    }

    public SimpleWaiter onIteration(Runnable runnable) {
        onIteration = runnable;
        return this;
    }

    public SimpleWaiter onSuccess(Runnable runnable) {
        onSuccess = runnable;
        return this;
    }

    public SimpleWaiter onFailure(Runnable runnable) {
        onFailure = runnable;
        return this;
    }

    public SimpleWaiter failFast(FailFastCheck failFast) {
        this.failFast = failFast;
        return this;
    }

    public SimpleWaiter onTimeout(Runnable runnable) {
        onTimeout = runnable;
        return this;
    }

    @Override
    public boolean waitFor() {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout;

        logPoint.logStart(reason, timeout, level);

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
