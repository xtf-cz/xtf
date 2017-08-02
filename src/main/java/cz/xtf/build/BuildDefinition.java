package cz.xtf.build;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Plain data object holding data necessary for single build.
 * <p>
 * {@code getName()} returns name of a created build resources in OSE based on application name and image version in format: "appName-productCode" (eg. database-eap64)
 */
@Getter
@EqualsAndHashCode(of = "name")
public abstract class BuildDefinition {
	private String name;
	private String appName;
	private String builderImage;
	private Map<String, String> envProperties;

	@Setter
	private boolean forcePull = false;

	protected BuildDefinition(String appName, String builderImage) {
		this(appName, builderImage, new HashMap<String, String>());
	}

	protected BuildDefinition(String appName, String builderImage, Map<String, String> envProperties) {
		this.appName = appName;
		this.builderImage = builderImage;
		this.envProperties = envProperties;

		String[] splitted = getBuilderImage().split("/");
		this.name = appName + "-" + splitted[splitted.length - 1].split(":")[0].replace("-openshift", "");
	}
}
