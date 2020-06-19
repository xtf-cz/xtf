package cz.xtf.core.waiting.failfast;

/**
 * Interface for failfast class that provide reason of failure.
 */
public interface FailFastCheck {
	/**
	 * @return true if there is some error and waiter can fail immediately
	 */
	public boolean shouldFail();

	/**
	 * @return reason why {@link FailFastCheck#shouldFail()} have returned {@code true}
	 */
	default public String resaon() {
		return "reason not specified";
	}
}
