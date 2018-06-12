package cz.xtf.wait;

import cz.xtf.http.HttpClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public final class Waiters {

	public static void sleep(TimeUnit timeUnit, long t) {
		sleep(timeUnit.toMillis(t));
	}

	public static void sleep(TimeUnit timeUnit, long t, String reason) {
		sleep(timeUnit.toMillis(t), reason);
	}

	public static void sleep(TimeUnit timeUnit, long t, String reason, Waiter.LogPoint logPoint) {
		sleep(timeUnit.toMillis(t), reason, logPoint);
	}

	public static void sleep(long millis) {
		sleep(millis, null, Waiter.LogPoint.NONE);
	}

	public static void sleep(long millis, String reason) {
		sleep(millis, reason, Waiter.LogPoint.START);
	}

	public static void sleep(long millis, String reason, Waiter.LogPoint logPoint) {
		try {
			logPoint.logStart(reason, millis);
			Thread.sleep(millis);
			logPoint.logEnd(reason, millis);
		} catch (InterruptedException e) {
			log.warn("Interrupted during waiting. Wait reason: {}", reason, e);
		}
	}

	public static Waiter doesUrlReturnOK(String url) {
		return doesUrlReturnCode(url, 200);
	}

	public static Waiter doesUrlReturnCode(String url, int expectedCode) {
		return doesUrlReturnCode(url, expectedCode, -1);
	}

	public static Waiter doesUrlReturnCode(String url, int expectedCode, int failCode) {
		Supplier<Integer> getCode = () -> {
			try {
				return HttpClient.get(url).code();
			} catch (IOException e) {
				log.warn("Attempt to retrieve http code from {} has failed", url, e);
				return -1;
			}
		};

		return new SupplierWaiter<>(getCode, x -> x == expectedCode, x -> x == failCode || x == -1);
	}

	private Waiters() {

	}
}
