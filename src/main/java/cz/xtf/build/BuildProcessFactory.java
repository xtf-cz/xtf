package cz.xtf.build;

import cz.xtf.TestConfiguration;

public class BuildProcessFactory {

	public static BuildProcess getProcess(BuildDefinition definition) {
		if (definition instanceof PathBuildDefinition) {
			return TestConfiguration.binaryBuild() ?
					new PathBinaryBuildProcess((PathBuildDefinition) definition) : new PathGitBuildProcess((PathBuildDefinition) definition);
		} else if (definition instanceof GitBuildDefinition) {
			return new GitBuildProcess((GitBuildDefinition) definition);
		} else {
			throw new IllegalStateException("Unknown type of BuildDefinition");
		}
	}
}
