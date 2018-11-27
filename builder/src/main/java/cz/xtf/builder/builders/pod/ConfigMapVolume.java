package cz.xtf.builder.builders.pod;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class ConfigMapVolume extends Volume {
	private final String configMapName;
	
	public ConfigMapVolume(String name, String configMapName) {
		super(name);
		this.configMapName = configMapName;
	}

	@Override
	protected void addVolumeParameters(VolumeBuilder builder){
		builder.withNewConfigMap().withName(configMapName).endConfigMap();
	}
}
