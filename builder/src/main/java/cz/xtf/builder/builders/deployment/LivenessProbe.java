package cz.xtf.builder.builders.deployment;

import io.fabric8.kubernetes.api.model.ProbeBuilder;

public class LivenessProbe extends AbstractProbe {

    private int initialDelay = -1;
    private int timeoutSeconds = -1;
    private int successThreshold = -1;
    private int failureThreshold = -1;
    private int periodSeconds = -1;
    private Boolean isEnabled = null;

    public LivenessProbe setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
        return this;
    }

    public LivenessProbe setIsEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
        return this;
    }

    public LivenessProbe setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public LivenessProbe setSuccessThreshold(int successThreshold) {
        this.successThreshold = successThreshold;
        return this;
    }

    public LivenessProbe setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
        return this;
    }

    public LivenessProbe createHttpProbe(String path, String port) {
        return (LivenessProbe) super.createHttpProbe(path, port);
    }

    protected void build(ProbeBuilder builder) {
        if (isEnabled != null) {
            // TODO this property is likely gone
        }
        if (initialDelay > 0) {
            builder.withInitialDelaySeconds(initialDelay);
        }
        if (timeoutSeconds > 0) {
            builder.withTimeoutSeconds(timeoutSeconds);
        }
        if (successThreshold > 0) {
            builder.withSuccessThreshold(successThreshold);
        }
        if (failureThreshold > 0) {
            builder.withFailureThreshold(failureThreshold);
        }
        if (periodSeconds > 0) {
            builder.withPeriodSeconds(periodSeconds);
        }
    }

}
