package cz.xtf.build;

import org.apache.maven.it.VerificationException;

import cz.xtf.git.PomModifier;
import cz.xtf.io.IOUtils;
import cz.xtf.maven.MavenUtil;
import cz.xtf.openshift.OpenShiftBinaryClient;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents local maven build with resulting archives given to builder image.
 * <p>
 * Local maven build - Will build project localy in temporary folder and copy 
 * all .jar, .ear, .war, .rar files to deployments folder and copy configuration folder.
 * <p>
 * Supports only simple maven projects at this moment with no possibility of configuring maven args.
 * Local builds may fail as most configuration of repositories is in Indy.
 */
public class PathBinaryBuildProcess extends PathBuildProcess {
	private Path appTmpDir;
	private Path appTmpSourceDir;
	private Path appTmpArtifactsDir;
	private Path appTmpDeploymentsDir;
	private Path appTmpConfigurationDir;

	public PathBinaryBuildProcess(PathBuildDefinition definition) {
		super(definition);
	}

	@Override
	public void deployBuild() {
		createDirectoriesStructure();
		runMavenBuild();
		copyArtefacts();
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
			appTmpArtifactsDir = appTmpDir.resolve("artifacts");
			appTmpDeploymentsDir = appTmpArtifactsDir.resolve("deployments");
			appTmpConfigurationDir = appTmpArtifactsDir.resolve("configuration");

			appTmpSourceDir.toFile().mkdir();
			appTmpArtifactsDir.toFile().mkdir();
			appTmpDeploymentsDir.toFile().mkdir();
			appTmpConfigurationDir.toFile().mkdir();

			IOUtils.copy(pathToProject, appTmpSourceDir);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create temporary directories structure", e);
		}
	}

	private void runMavenBuild() {
		if (appTmpSourceDir.resolve("pom.xml").toFile().exists()) {
			new PomModifier(pathToProject.toAbsolutePath(), appTmpSourceDir).modify();
			try {
				MavenUtil.forProject(appTmpSourceDir).forkJvm().executeGoals("clean", "package");
			} catch (VerificationException e) {
				throw new IllegalStateException("Failed to run local maven build on project", e);
			}
		}
	}

	private void copyArtefacts() {
		try {
			if (appTmpSourceDir.resolve("pom.xml").toFile().exists()) {
				if (appTmpSourceDir.resolve("target").toFile().exists()) {
					for (File file : appTmpSourceDir.resolve("target").toFile().listFiles(new ArchivesFileFilter())) {
						IOUtils.copy(Paths.get(file.getPath()), appTmpDeploymentsDir);
					}
				} else {
					throw new IllegalStateException("Didn't found target directory in builded app, multiprojects aren't supported");
				}
			}
			if (appTmpSourceDir.resolve("configuration").toFile().exists()) {
				IOUtils.copy(appTmpSourceDir.resolve("configuration"), appTmpConfigurationDir);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to copy build artifacts", e);
		}
	}

	private void createResources() {
		deployResources(getPreconfiguredBuildConfig().withBinaryBuild().build());
	}

	private void startBuildFromDirectory() {
		OpenShiftBinaryClient.getInstance().executeCommandNoWait("error starting build with oc", "start-build", buildName, "-n", buildNamespace, "--from-dir=" + appTmpArtifactsDir);
	}

	public static class ArchivesFileFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.toString().matches(".*(\\.war|\\.jar|\\.ear|\\.rar)");
		}
	}

	public static class DirectoryFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	}
}
