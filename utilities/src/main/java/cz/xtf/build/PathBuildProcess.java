package cz.xtf.build;

import java.nio.file.Path;

public abstract class PathBuildProcess extends BuildProcess {
	protected final Path pathToProject;

	public PathBuildProcess(PathBuildDefinition definition) {
		super(definition);

		pathToProject = definition.getPathToProject();
	}
}
