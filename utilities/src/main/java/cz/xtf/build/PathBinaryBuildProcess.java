package cz.xtf.build;

import cz.xtf.git.PomModifier;
import cz.xtf.io.IOUtils;
import cz.xtf.openshift.OpenShiftBinaryClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents an OpenShift "binary" build (oc start-build --from-dir=src/ )
 */
public class PathBinaryBuildProcess extends PathBuildProcess {
	private Path appTmpDir;
	private Path appTmpSourceDir;

	public PathBinaryBuildProcess(PathBuildDefinition definition) {
		super(definition);
	}

	@Override
	public void deployBuild() {
		createDirectoriesStructure();
		runPomModifier();
		createResources();
		startBuildFromDirectory();
	}
	
	@Override
	public void deleteBuild() {
		deleteOpenshiftResources();
	}
	
	@Override
	public BuildStatus getBuildStatus() {
		return getCommonStatus(getBuildConfig());
	}
	
	@Override
	public void updateBuild() {
		throw new UnsupportedOperationException("Yet not implemented");
	}

	private void createDirectoriesStructure() {
		IOUtils.TMP_DIRECTORY.toFile().mkdirs();
		try {
			appTmpDir = Files.createTempDirectory(IOUtils.TMP_DIRECTORY, buildName);
			appTmpSourceDir = appTmpDir.resolve("source");

			appTmpSourceDir.toFile().mkdir();

			IOUtils.copy(pathToProject, appTmpSourceDir);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create temporary directories structure", e);
		}
	}

	private void runPomModifier() {
		if (appTmpSourceDir.resolve("pom.xml").toFile().exists()) {
			new PomModifier(pathToProject.toAbsolutePath(), appTmpSourceDir).modify();
		}
	}

	private void createResources() {
		deployResources(getPreconfiguredBuildConfig().withBinaryBuild().build());
	}

	private void startBuildFromDirectory() {
		OpenShiftBinaryClient.getInstance().executeCommandNoWait("error starting build with oc", "start-build", buildName, "-n", buildNamespace, "--from-dir=" + appTmpSourceDir);
	}
}
