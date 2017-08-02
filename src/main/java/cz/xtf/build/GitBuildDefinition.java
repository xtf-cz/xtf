package cz.xtf.build;

import java.util.Map;

import lombok.AccessLevel;

import lombok.Setter;
import lombok.Getter;

@Getter
public abstract class GitBuildDefinition extends BuildDefinition {
	private final String gitRepo;
	
	@Setter(AccessLevel.PROTECTED)
	private String branch;
	@Setter(AccessLevel.PROTECTED)
	private String contextDir;

	protected GitBuildDefinition(String appName, String gitRepo, String builderImage) {
		super(appName, builderImage);

		this.gitRepo = gitRepo;
	}
	
	protected GitBuildDefinition(String appName, String gitRepo, String builderImage, Map<String, String> envProperties) {
		super(appName, builderImage, envProperties);

		this.gitRepo = gitRepo;
	}
}
