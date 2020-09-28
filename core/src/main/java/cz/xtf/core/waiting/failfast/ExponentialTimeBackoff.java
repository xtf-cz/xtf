package cz.xtf.core.waiting.failfast;

import java.util.Random;

/**
 * Class provide support for exponential time backoff. This is convenient if you need to check some event periodically.
 * The class provides {@code next} method that implements wait (blocking or non-blocking - returns true/false whether code
 * can proceed).
 * <p>
 * Class wait time grow exponentially up to {@code maxBackoffMillis} time: 1sec, 2 sec, 4 sec, 8
 * sec,...,{@code maxBackoffMillis}.
 */
public class ExponentialTimeBackoff {
    private long lastNonBlockingTime;
    private boolean blocking;
    private long maxBackoffMillis;

    private long step;
    private Random random;
    private long nonBlockingWaitMillis;

    private ExponentialTimeBackoff(Builder builder) {
        blocking = builder.blocking;
        maxBackoffMillis = builder.maxBackoffMillis;
        step = 1;
        random = new Random(System.currentTimeMillis());
        lastNonBlockingTime = System.currentTimeMillis();
        nonBlockingWaitMillis = waitMillisForCurrentStep();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Exponential backoff wait.
     * If wait is blocking, {@link Thread#sleep} is called and true is always returned.
     * If wait is non-blocking, the method returns true if logic can proceed (enough time has elapsed)
     *
     * @return true if logic can proceed
     */
    public boolean next() {
        return blocking ? nextBlocking() : nextNonBlocking();
    }

    private boolean nextBlocking() {
        try {
            long wait = waitMillisForCurrentStep();
            Thread.sleep(wait);
            incrementStep();
            return true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean nextNonBlocking() {
        long now = System.currentTimeMillis();
        if (lastNonBlockingTime + nonBlockingWaitMillis <= now) {
            incrementStep();
            nonBlockingWaitMillis = waitMillisForCurrentStep();
            lastNonBlockingTime = now;
            return true;
        } else {
            return false;
        }
    }

    private long waitMillisForCurrentStep() {
        if (step * 1000 >= maxBackoffMillis) {
            return maxBackoffMillis + random.nextInt(1000);
        } else {
            return step * 1000 + random.nextInt(1000);
        }
    }

    private void incrementStep() {
        if (step * 1000 < maxBackoffMillis) {
            step *= 2;
        }
    }

    public static class Builder {
        static boolean DEFAULT_BLOCKING = false;
        static long DEFAULT_MAX_BACKOFF = 1_000L;

        private boolean blocking;
        private long maxBackoffMillis;

        private Builder() {
            blocking = DEFAULT_BLOCKING;
            maxBackoffMillis = DEFAULT_MAX_BACKOFF;
        }

        /**
         * Flag whether waiting is blocking or not
         */
        public Builder blocking(boolean blocking) {
            this.blocking = blocking;
            return this;
        }

        /**
         * Maximum wait time is {@code maxBackoffMillis} + rand(1000)
         */
        public Builder maxBackoff(long maxBackoffMillis) {
            this.maxBackoffMillis = maxBackoffMillis;
            return this;
        }

        public ExponentialTimeBackoff build() {
            return new ExponentialTimeBackoff(this);
        }
    }
}
