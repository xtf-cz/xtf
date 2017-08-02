package cz.xtf.build;

import cz.xtf.TestConfiguration;
import cz.xtf.TestParent;
import cz.xtf.git.GitProject;
import cz.xtf.git.GitUtil;
import cz.xtf.git.PomModifier;
import cz.xtf.io.IOUtils;
import cz.xtf.tuple.Tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import io.fabric8.openshift.api.model.BuildConfig;

/**
 * Represents standard git build process from local project.
 */
@Slf4j
public class PathGitBuildProcess extends PathBuildProcess {
	private boolean initialized = false;
	private GitProject git = null;

	public PathGitBuildProcess(PathBuildDefinition definition) {
		super(definition);
	}

	@Override
	public void deployBuild() {
		String gitRepo = TestParent.gitlab().createProjectFromPath(buildName, pathToProject).getHttpUrl();
		deployBuildFromGit(gitRepo);
	}

	@Override
	public void deleteBuild() {
		if (isBuildConfigPresent() && GitUtil.checkIfRepositoryExist(getGitUri(getBuildConfig()))) {
			TestParent.gitlab().deleteProject(buildName);
		}
		deleteOpenshiftResources();
	}

	@Override
	public void updateBuild() {
		git.addAll();
		git.commit("Autocommit by build manager");
		git.push();

		startBuild(getBuildConfig());
	}

	@Override
	public BuildStatus getBuildStatus() {
		BuildConfig bc = getBuildConfig();
		BuildStatus status = getCommonStatus(bc);

		boolean buildPresent = status == BuildStatus.RUNNING || status == BuildStatus.READY || status == BuildStatus.DEPLOYED || status == BuildStatus.PENDING;
		boolean checkCodeForEquality = initialized ? false : true;
		initialized = true;

		// Get code and compare once only per jvm run
		if (checkCodeForEquality && buildPresent) {
			String gitUri = getGitUri(bc);
			if (GitUtil.checkIfRepositoryExist(gitUri)) {
				Tuple.Pair<Path, Path> projects;
				try { 
					projects = prepareProjects(gitUri);
				} catch (IOException e) {
					log.error("Hit IO Exception", e);
					return BuildStatus.ERROR;
				} 
				BuildStatus sourceStatus = areProjectsDifferent(projects.getFirst(), projects.getSecond());
				if (sourceStatus != BuildStatus.READY) {
					return sourceStatus;
				}
			} else {
				return BuildStatus.GIT_REPO_GONE;
			}
		}
		return status;
	}

	private String getGitUri(BuildConfig bc) {
		return bc.getSpec().getSource().getGit().getUri();
	}

	/**
	 * Prepares local and git project for comparision on equality
	 * @return paths to both projects
	 * @throws IOException 
	 */
	private Tuple.Pair<Path, Path> prepareProjects(String gitUri) throws IOException {
		Path bmTmpBuildDir = IOUtils.TMP_DIRECTORY.resolve("bm").resolve(buildName);
		Path expectedSourcePath = bmTmpBuildDir.resolve("expected-source");
		Path presentSourcePath = bmTmpBuildDir.resolve("present-source");

		expectedSourcePath.toFile().mkdirs();
		presentSourcePath.toFile().mkdirs();

		git = GitUtil.cloneRepository(presentSourcePath, gitUri, TestConfiguration.gitLabUsername(), TestConfiguration.gitLabPassword());

		// Use the ghost of our suite to get same result as should be in git
		final PomModifier pom = new PomModifier(pathToProject.toAbsolutePath(), expectedSourcePath);
		expectedSourcePath.toFile().mkdir();
		IOUtils.copy(pathToProject, expectedSourcePath);

		pom.modify();
		return Tuple.pair(expectedSourcePath, presentSourcePath);
	}

	private BuildStatus areProjectsDifferent(Path expected, Path present) {
		try {
			log.info("Checking diff between source codes: expected -> present");
			SourceCodeUpdater expectedPresentComparator = new SourceCodeUpdater(expected, present, SourceCodeUpdater.OnDiff.COPY);
			Files.walkFileTree(expected, expectedPresentComparator);

			log.info("Checking diff between source codes: present -> expected");
			SourceCodeUpdater presentExpectedComparator = new SourceCodeUpdater(present, expected, SourceCodeUpdater.OnDiff.DELETE);
			Files.walkFileTree(present, presentExpectedComparator);

			if (expectedPresentComparator.isError() || presentExpectedComparator.isError()) {
				return BuildStatus.ERROR;
			} else if (expectedPresentComparator.isDiffPresent() || presentExpectedComparator.isDiffPresent()) {
				return BuildStatus.SOURCE_CHANGE;
			} else {
				return BuildStatus.READY;
			}
		} catch (IOException e) {
			log.error(e.getMessage());
			log.info("IO happend, new build will be created");

			return BuildStatus.ERROR;
		}
	}
}
