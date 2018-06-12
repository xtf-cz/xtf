package cz.xtf.openshift.builder.pod;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class ConfigMapVolume extends Volume {
	private final String configMapName;
	
	public ConfigMapVolume(final String name, final String configMapName) {
		super(name);
		this.configMapName = configMapName;
	}

	@Override
	protected void addVolumeParameters(VolumeBuilder builder){
		builder.withNewConfigMap().withName(configMapName).endConfigMap();
	}
}
