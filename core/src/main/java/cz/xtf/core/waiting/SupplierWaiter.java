package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import cz.xtf.core.config.WaitingConfig;

public class SupplierWaiter<X> implements Waiter {
	private Supplier<X> supplier;
	private Function<X, Boolean> successCondition;
	private Function<X, Boolean> failureCondition;

	private long timeout;
	private long interval;

	private String reason;
	private LogPoint logPoint;

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition) {
		this(supplier, successCondition, x -> false);
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition) {
		this(supplier, successCondition, failureCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout());
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, String reason) {
		this(supplier, successCondition, x -> false, reason);
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition, String reason) {
		this(supplier, successCondition, failureCondition, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), reason);
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, TimeUnit timeoutUnit, long timeout) {
		this(supplier, successCondition, x -> false, timeoutUnit, timeout);
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, TimeUnit timeoutUnit, long timeout, String reason) {
		this(supplier, successCondition, x -> false, timeoutUnit, timeout, reason);
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition, TimeUnit timeoutUnit, long timeout) {
		this(supplier, successCondition, failureCondition, timeoutUnit, timeout, null);
	}

	public SupplierWaiter(Supplier<X> supplier, Function<X, Boolean> successCondition, Function<X, Boolean> failureCondition, TimeUnit timeoutUnit, long timeout, String reason) {
		this.supplier = supplier;
		this.successCondition = successCondition;
		this.failureCondition = failureCondition;
		this.interval = DEFAULT_INTERVAL;
		this.timeout = timeoutUnit.toMillis(timeout);
		this.reason = reason;
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

	@Override
	public boolean waitFor() {
		long startTime = System.currentTimeMillis();
		long endTime = startTime + timeout;

		logPoint.logStart(reason, timeout);
		while (System.currentTimeMillis() < endTime) {
			X x = supplier.get();

			if (failureCondition.apply(x)) {
				logPoint.logEnd(reason + " (Failure)", System.currentTimeMillis() - startTime);
				return false;
			}
			if (successCondition.apply(x)) {
				logPoint.logEnd(reason + " (Success)", System.currentTimeMillis() - startTime);
				return true;
			}

			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				throw new WaiterException("Thread has been interrupted!");
			}
		}
		logPoint.logEnd(reason + "(Timeout)", timeout);
		throw new WaiterException(reason);
	}
}
