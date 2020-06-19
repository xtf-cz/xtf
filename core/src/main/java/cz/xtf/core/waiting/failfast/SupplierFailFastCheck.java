package cz.xtf.core.waiting.failfast;


import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Generic class for {@link FailFastCheck} so that builder can build it easily.
 */
public class SupplierFailFastCheck<X> implements FailFastCheck {

	private final Supplier<X> eventSupplier;
	private final Function<X, Boolean> shouldFail;
	private final Function<X, String> reasonSupplier;

	private String reason;

	/**
	 * @param supplier       supplier that provide object
	 * @param shouldFail     condition. The result is the result of {@link FailFastCheck#shouldFail()}
	 * @param reasonSupplier returns string as a reason why {@code shouldFail} returned {@code true}
	 */
	SupplierFailFastCheck(Supplier<X> supplier, Function<X, Boolean> shouldFail, Function<X, String> reasonSupplier) {
		this.eventSupplier = supplier;
		this.shouldFail = shouldFail;
		this.reasonSupplier = reasonSupplier;
	}

	@Override
	public boolean shouldFail() {
		X resources = eventSupplier.get();
		if (shouldFail.apply(resources)) {
			reason = reasonSupplier.apply(resources);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public String resaon() {
		return reason;
	}

}
