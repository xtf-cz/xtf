package cz.xtf.build;

import cz.xtf.TestParent;

import java.nio.file.Path;
import java.util.Map;

import lombok.Getter;

@Getter
public abstract class PathBuildDefinition extends BuildDefinition {
	private final Path pathToProject;

	protected PathBuildDefinition(String appName, Path pathToProject, String builderImage) {
		super(appName, builderImage);

		this.pathToProject = pathToProject;
	}

	protected PathBuildDefinition(String appName, Path pathToProject, String builderImage, Map<String, String> envProperties) {
		super(appName, builderImage, envProperties);

		this.pathToProject = pathToProject;
	}

	protected static Path getStandardPathToProject(String moduleName, String appName) {
		return TestParent.findApplicationDirectory(moduleName, appName);
	}

	protected static Path getStandardPathToProject(String moduleName, String appName, String subModuleName) {
		return TestParent.findApplicationDirectory(moduleName, appName, subModuleName);
	}
}
