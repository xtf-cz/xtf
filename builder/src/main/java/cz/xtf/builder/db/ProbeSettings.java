package cz.xtf.builder.db;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
// builder implicitly makes allArgConstructor public, so we hide it
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProbeSettings {
    // Fields with default values
    @Builder.Default
    private int livenessInitialDelaySeconds = 0;
    /**
     * Port number
     */
    private String livenessTcpProbe;
    @Builder.Default
    private int readinessInitialDelaySeconds = 0;
    private String readinessProbeCommand;
    @Builder.Default
    private int readinessTimeoutSeconds = 1;
    @Builder.Default
    private int readinessPeriodSeconds = 10;
    @Builder.Default
    private int readinessFailureThreshold = 3;
    @Builder.Default
    private int startupInitialDelaySeconds = 0;
    private String startupProbeCommand;
    @Builder.Default
    private int startupFailureThreshold = 3;
    @Builder.Default
    private int startupPeriodSeconds = 10;

    @Deprecated
    public ProbeSettings(
            int livenessInitialDelaySeconds,
            String livenessTcpProbe,
            int readinessInitialDelaySeconds,
            String readinessProbeCommand,
            int startupInitialDelaySeconds,
            String startupProbeCommand,
            int startupFailureThreshold,
            int startupPeriodSeconds) {
        this.livenessInitialDelaySeconds = livenessInitialDelaySeconds;
        this.livenessTcpProbe = livenessTcpProbe;
        this.readinessInitialDelaySeconds = readinessInitialDelaySeconds;
        this.readinessProbeCommand = readinessProbeCommand;
        this.readinessTimeoutSeconds = 1; // default value
        this.readinessPeriodSeconds = 10; // default value
        this.readinessFailureThreshold = 3; // default value
        this.startupInitialDelaySeconds = startupInitialDelaySeconds;
        this.startupProbeCommand = startupProbeCommand;
        this.startupFailureThreshold = startupFailureThreshold;
        this.startupPeriodSeconds = startupPeriodSeconds;
    }
}