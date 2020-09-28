package cz.xtf.core.waiting;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

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

    private Waiters() {

    }
}
