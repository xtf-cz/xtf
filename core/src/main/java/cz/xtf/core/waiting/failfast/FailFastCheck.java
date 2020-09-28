package cz.xtf.core.waiting.failfast;

/**
 * Interface for failfast class that provide reason of failure.
 */
public interface FailFastCheck {
    /**
     * @return true if there is some error and waiter can fail immediately
     */
    public boolean hasFailed();

    /**
     * @return reason why {@link FailFastCheck#hasFailed()} have returned {@code true}
     */
    default public String reason() {
        return "reason not specified";
    }
}
