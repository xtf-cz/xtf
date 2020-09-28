package cz.xtf.builder.builders.deployment;

import io.fabric8.kubernetes.api.model.ProbeBuilder;

public class ReadinessProbe extends AbstractProbe {

    private int timeoutSeconds = 0;
    private int frequencySeconds = 0;
    private int successThreshold = -1;
    private int failureThreshold = -1;
    private int initialDelaySeconds = 0;
    private int periodSeconds = -1;

    public ReadinessProbe setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public ReadinessProbe setFrequencyCheck(int frequencyCheck) {
        this.frequencySeconds = frequencyCheck;
        return this;
    }

    public ReadinessProbe setSuccessThreshold(int successThreshold) {
        this.successThreshold = successThreshold;
        return this;
    }

    public ReadinessProbe setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
        return this;
    }

    public ReadinessProbe setInitialDelaySeconds(int initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
        return this;
    }

    public ReadinessProbe createHttpProbe(String path, String port) {
        return (ReadinessProbe) super.createHttpProbe(path, port);
    }

    protected void build(ProbeBuilder builder) {
        if (timeoutSeconds > 0) {
            builder.withTimeoutSeconds(timeoutSeconds);
        }
        if (frequencySeconds > 0) {
            builder.withPeriodSeconds(frequencySeconds);
        }
        if (initialDelaySeconds > 0) {
            builder.withInitialDelaySeconds(initialDelaySeconds);
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
