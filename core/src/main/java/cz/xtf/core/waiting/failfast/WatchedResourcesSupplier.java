package cz.xtf.core.waiting.failfast;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Generic class for {@link FailFastCheck} so that a builder can build it easily.
 */
public class WatchedResourcesSupplier<X> implements FailFastCheck {

    private final Supplier<X> resourceSupplier;
    private final Function<X, Boolean> hasFailedSupplier;
    private final Function<X, String> reasonSupplier;

    private String reason;

    /**
     * @param resourceSupplier supplier that provides an object
     * @param hasFailedFunction condition. The result is the result of {@link FailFastCheck#hasFailed()}
     * @param reasonFunction returns string as a reason why {@code hasFailed} returned {@code true}
     */
    WatchedResourcesSupplier(Supplier<X> resourceSupplier, Function<X, Boolean> hasFailedFunction,
            Function<X, String> reasonFunction) {
        this.resourceSupplier = resourceSupplier;
        this.hasFailedSupplier = hasFailedFunction;
        this.reasonSupplier = reasonFunction;
    }

    @Override
    public boolean hasFailed() {
        X resources = resourceSupplier.get();
        if (hasFailedSupplier.apply(resources)) {
            reason = reasonSupplier.apply(resources);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String reason() {
        return reason;
    }

}
