package cz.xtf.core.waiting.failfast;

import java.util.List;

/**
 * Handler for multiple {@link FailFastCheck} instances.
 * Checks every instance and if one of them fails ({@link FailFastCheck#hasFailed()} returns true) returns {@code true}
 * and provide reason of that check.
 */
public class MultipleFailFastChecksHandler implements FailFastCheck {
    private final List<FailFastCheck> checks;
    private String reason;

    MultipleFailFastChecksHandler(List<FailFastCheck> checks) {
        this.checks = checks;
    }

    @Override
    public boolean hasFailed() {
        for (FailFastCheck check : checks) {
            if (check.hasFailed()) {
                reason = check.reason();
                return true;
            }
        }
        return false;
    }

    @Override
    public String reason() {
        return reason;
    }
}
