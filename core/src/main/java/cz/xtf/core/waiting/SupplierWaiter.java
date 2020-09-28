package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.event.Level;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.failfast.ExponentialTimeBackoff;
import cz.xtf.core.waiting.failfast.FailFastCheck;

public class SupplierWaiter<X> implements Waiter {
    private Supplier<X> supplier;
    private Function<X, Boolean> successCondition;
    private Function<X, Boolean> failureCondition;

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

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition) {
        this(supplier, successCondition, x -> false);
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition) {
        this(supplier, successCondition, failureCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout());
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, String reason) {
        this(supplier, successCondition, x -> false, reason);
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition,
            String reason) {
        this(supplier, successCondition, failureCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), reason);
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, TimeUnit timeoutUnit, long timeout) {
        this(supplier, successCondition, x -> false, timeoutUnit, timeout);
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, TimeUnit timeoutUnit, long timeout,
            String reason) {
        this(supplier, successCondition, x -> false, timeoutUnit, timeout, reason);
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition,
            TimeUnit timeoutUnit, long timeout) {
        this(supplier, successCondition, failureCondition, timeoutUnit, timeout, null);
    }

    public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition,
            TimeUnit timeoutUnit, long timeout, String reason) {
        this.supplier = supplier;
        this.successCondition = successCondition;
        this.failureCondition = failureCondition;

        this.onIteration = () -> {
        };
        this.onSuccess = () -> {
        };
        this.onFailure = () -> {
        };
        this.onTimeout = () -> {
        };

        this.failFast = () -> false;

        this.interval = DEFAULT_INTERVAL;
        this.timeout = timeoutUnit.toMillis(timeout);
        this.reason = reason;
        this.level = WaitingConfig.level();
        this.logPoint = reason == null ? LogPoint.NONE : LogPoint.START;
    }

    public SupplierWaiter timeout(long millis) {
        this.timeout = millis;
        return this;
    }

    public SupplierWaiter timeout(TimeUnit timeUnit, long t) {
        this.timeout = timeUnit.toMillis(t);
        return this;
    }

    public SupplierWaiter interval(long millis) {
        this.interval = millis;
        return this;
    }

    public SupplierWaiter interval(TimeUnit timeUnit, long t) {
        this.interval = timeUnit.toMillis(t);
        return this;
    }

    public SupplierWaiter reason(String reason) {
        this.reason = reason;
        this.logPoint = LogPoint.START;
        return this;
    }

    public SupplierWaiter logPoint(LogPoint logPoint) {
        this.logPoint = logPoint;
        return this;
    }

    public SupplierWaiter level(Level level) {
        this.level = level;
        return this;
    }

    public SupplierWaiter onIteration(Runnable runnable) {
        onIteration = runnable;
        return this;
    }

    public SupplierWaiter onSuccess(Runnable runnable) {
        onSuccess = runnable;
        return this;
    }

    public SupplierWaiter onFailure(Runnable runnable) {
        onFailure = runnable;
        return this;
    }

    public SupplierWaiter onTimeout(Runnable runnable) {
        onTimeout = runnable;
        return this;
    }

    public SupplierWaiter failFast(FailFastCheck failFast) {
        this.failFast = failFast;
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

            X x = supplier.get();

            if (failureCondition.apply(x)) {
                logPoint.logEnd(reason + " (Failure)", System.currentTimeMillis() - startTime, level);
                onFailure.run();
                return false;
            }
            if (successCondition.apply(x)) {
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
        logPoint.logEnd(reason + "(Timeout)", timeout, level);
        onTimeout.run();
        throw new WaiterException(reason);
    }
}
