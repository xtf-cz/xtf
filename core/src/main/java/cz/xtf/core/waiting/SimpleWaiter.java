package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import cz.xtf.core.config.WaitingConfig;

public class SimpleWaiter implements Waiter {
	private BooleanSupplier successCondition;
	private BooleanSupplier failureCondition;

	private long timeout;
	private long interval;

	private String reason;
	private LogPoint logPoint;

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
		this.failureCondition = null;
		this.timeout = timeoutUnit.toMillis(timeout);
		this.interval = DEFAULT_INTERVAL;
		this.reason = reason;
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

	@Override
	public boolean waitFor() {
		long startTime = System.currentTimeMillis();
		long endTime = startTime + timeout;

		logPoint.logStart(reason, timeout);
		while (System.currentTimeMillis() < endTime) {
			if (failureCondition != null && failureCondition.getAsBoolean()) {
				logPoint.logEnd(reason + " (Failure)", System.currentTimeMillis() - startTime);
				return false;
			}
			if (successCondition.getAsBoolean()) {
				logPoint.logEnd(reason + " (Success)", System.currentTimeMillis() - startTime);
				return true;
			}

			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				throw new WaiterException("Thread has been interrupted!");
			}
		}
		logPoint.logEnd(reason + " (Time out)", System.currentTimeMillis() - startTime);
		throw new WaiterException(reason);
	}
}
