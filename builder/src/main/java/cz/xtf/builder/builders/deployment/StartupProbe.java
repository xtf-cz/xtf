package cz.xtf.builder.builders.deployment;

import io.fabric8.kubernetes.api.model.ProbeBuilder;

public class StartupProbe extends AbstractProbe {

    private int failureThreshold = -1;
    private int periodSeconds = -1;

    private int initialDelay = -1;

    public StartupProbe setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
        return this;
    }

    public StartupProbe setPeriodSeconds(int periodSeconds) {
        this.periodSeconds = periodSeconds;
        return this;
    }

    public StartupProbe setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
        return this;
    }

    public StartupProbe createHttpProbe(String path, String port) {
        return (StartupProbe) super.createHttpProbe(path, port);
    }

    @Override
    protected void build(ProbeBuilder builder) {
        if (failureThreshold > 0) {
            builder.withFailureThreshold(failureThreshold);
        }
        if (periodSeconds > 0) {
            builder.withPeriodSeconds(periodSeconds);
        }
        if (initialDelay > 0) {
            builder.withInitialDelaySeconds(initialDelay);
        }

    }
}
