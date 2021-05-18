package cz.xtf.builder.db;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class ProbeSettings {
    private int livenessInitialDelaySeconds;
    private String livenessTcpProbe;
    private int readinessInitialDelaySeconds;
    private String readinessProbeCommand;
}
