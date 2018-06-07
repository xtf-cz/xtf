package cz.xtf.git;

import org.apache.commons.io.FileUtils;

import org.assertj.core.api.Fail;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabGroup;
import org.gitlab.api.models.GitlabProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.io.IOUtils;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.util.RandomUtil;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class GitLabUtil {

	private static final String SEPARATOR = "------";

	private static final Logger LOGGER = LoggerFactory.getLogger(GitLabUtil.class);

	private final GitlabAPI api;

	private String gitRepoSshUrl = null;
	private String gitRepoHttpUrl = null;
	private Path gitRepoLocalPath = null;

	private static final int WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS = 5;
	private static final int WAIT_FOR_GITLAB_CREATE_PROJECT_TIME = 10000;
	private static final String WAIT_FOR_GITLAB_CREATE_PROJECT_MESSAGE = "Please try again later";

	public GitLabUtil() {
		try {
			if (TestConfiguration.dynamicGitLab()) {
				api = GitlabAPI.connect(
						TestConfiguration.gitLabURL(),
						GitlabAPI.connect(TestConfiguration.gitLabURL(),
								TestConfiguration.gitLabUsername(),
								TestConfiguration.gitLabPassword())
								.getPrivateToken());
			} else {
				api = GitlabAPI.connect(TestConfiguration.gitLabURL(),
						TestConfiguration.gitLabToken());
			}
		} catch (IOException e) {
			LOGGER.error("Error while connecting to GitLab", e);
			throw new IllegalStateException(e);
		}
	}

	public String getProjectUrl(String projectName) {
		Optional<GitlabProject> project;

		if (TestConfiguration.gitLabGroupEnabled()) {
			// try the projects from assigned group (*-automated)
			project = getProjects(getGroupId())
					.filter(p -> p.getName().equals(projectName) | p.getName().startsWith(projectName))
					.findFirst();
			if (!project.isPresent() && getGroupId() != getGroupId(TestConfiguration.gitLabGroup())) {
				LOGGER.debug("Couldn't find project {} in assigned group", projectName);

				// try the default group
				project = getProjects(getGroupId(TestConfiguration.gitLabGroup()))
						.filter(p -> p.getName().equals(projectName))
						.findFirst();
			}
		} else {
			project = getProjects()
					.filter(p -> p.getName().equals(projectName) | p.getName().startsWith(projectName))
					.findFirst();
		}

		if (!project.isPresent()) {
			throw new IllegalArgumentException("Project '" + projectName + "' was not found");
		}

		return project.get().getHttpUrl();
	}

	public GitProject createProject() {
		return createProject(null);
	}

	public GitProject createProject(String name) {
		initGitLabProject(name, null);

		return createAndInitGitLabProject();
	}

	public GitProject createAndInitGitLabProject() {
		GitProject project;
		if (TestConfiguration.dynamicGitLab()) {
			project = GitUtil.initRepository(gitRepoLocalPath, gitRepoHttpUrl,
					TestConfiguration.gitLabUsername(),
					TestConfiguration.gitLabPassword());
		} else {
			project = GitUtil.initRepository(gitRepoLocalPath, gitRepoSshUrl);
		}
		project.setHttpUrl(gitRepoHttpUrl);
		return project;
	}

	public GitProject createProjectFromPath(String path) {
		return createProjectFromPath(null, path);
	}

	public GitProject createProjectFromPath(String name, String path) {
		return createProjectFromPath(name, Paths.get(path));
	}

	public GitProject createProjectFromPath(String name, Path path) {
		return createProjectFromPathWithMavenProfile(name, path, null);
	}

	public GitProject createProjectFromPathWithMavenProfile(String name, Path path, String mavenProfile) {
		return createProject(name, path, mavenProfile, null);
	}

	public GitProject createProject(String name, Path path, String mavenProfile, Consumer<GitProject> injectFiles) {
		return createProject(name, path, mavenProfile, injectFiles, true);
	}
	
	public GitProject createProject(String name, Path path,	String mavenProfile, Consumer<GitProject> injectFiles, boolean modifyPom) {
		initGitLabProject(name, null);

		Path sourcePath = path.toAbsolutePath();

		try {
			IOUtils.copy(sourcePath, gitRepoLocalPath);
		} catch (IOException e) {
			LOGGER.error("Error when copying files to the local repository", e);
			return null;
		}

		if(modifyPom) {
			final PomModifier pom = new PomModifier(sourcePath, gitRepoLocalPath);
			pom.modify();
		}

		GitProject project = createAndInitGitLabProject();

		if (mavenProfile != null) {
			project.addProfileToSTIBuild(mavenProfile);
		}

		if (injectFiles != null) {
			injectFiles.accept(project);
		}
		
		project.addAll();
		project.commit("initial commit");
		project.push();

		LOGGER.debug("Project \"" + name + "\" was created");
		return project;
	}

	private void initGitLabProject(String name, String url) {
		if (name == null || name.length() == 0) {
			name = UUID.randomUUID().toString();
		} else {
			name = RandomUtil.generateUniqueId(name, SEPARATOR);
		}

		try {
			GitlabProject project = null;
			int attempt = 0;
			IOException lastExcepion = null;
			while(project == null && attempt < WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS) {
				++attempt;
				try {
					Integer groupId = TestConfiguration.gitLabGroupEnabled()? getGroupId() : null;
					project = api.createProject(name, groupId, null, null, null, null, null, null, true,
							null, url);
				} catch (IOException x) {
					lastExcepion = x;
					if (!x.getMessage().contains(
							WAIT_FOR_GITLAB_CREATE_PROJECT_MESSAGE)
							|| attempt >= WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS) {
						throw x;
					} else {
						LOGGER.debug(
								"Error when creating project in GitLab. Attempt "
										+ attempt
										+ " of "
										+ WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS,
								x);
					}

					try {
						Thread.sleep(WAIT_FOR_GITLAB_CREATE_PROJECT_TIME);
					} catch (InterruptedException e) {
						throw x;
					}
				}
			}

			if (project == null) {
				Fail.fail("Failed to create git repository", lastExcepion);
			} else {
				gitRepoHttpUrl = project.getHttpUrl();
				gitRepoSshUrl = project.getSshUrl();
				gitRepoLocalPath = pathToProject(name);

				if (Files.notExists(gitRepoLocalPath)) {
					Files.createDirectories(gitRepoLocalPath);
				} else {
					FileUtils.deleteDirectory(gitRepoLocalPath.toFile());
					Files.createDirectories(gitRepoLocalPath);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Error when creating project in GitLab", e);
		}
	}

	public boolean deleteAllProjects() {
		LOGGER.debug("Deleting all projects in this namespace.");
		Stream<GitlabProject> projects = TestConfiguration.gitLabGroupEnabled()? getProjects(getGroupId()) : getProjects();
		return projects.map(p -> deleteProject(p.getName())).reduce(true, (b1, b2) -> b1 && b2);
	}

	public boolean deleteProject(String name) {
		Stream<GitlabProject> projects = TestConfiguration.gitLabGroupEnabled()? getProjects(getGroupId()) : getProjects();
		return projects.filter(p -> p.getName().equals(name) || p.getName().startsWith(name + SEPARATOR))
				.map(project -> {
					try {
						performDeleteProject(project.getId());
						IOUtils.deleteDirectory(pathToProject(project.getName()));
						LOGGER.debug("Project \"" + name + "\" was deleted");
					} catch (IOException e) {
						if (e.getMessage()
								.startsWith(
										"Can not instantiate value of type [simple type, class java.lang.Void]")) {
							LOGGER.debug("Project \"" + name + "\" was deleted");

							// known exception
							LOGGER.trace(
									"Catch known exception when deleting project in GitLab",
									e);
						} else {
							LOGGER.error(
									"Error when deleting project in GitLab", e);
							return false;
						}
					}
					return true;
				}).anyMatch(x -> !x);
	}

	private void performDeleteProject(Serializable projectId) throws IOException {
		api.deleteProject(projectId);
	}

	private Integer getGroupId() {
		String groupName;
		String namespace = OpenshiftUtil.getInstance().getContext().getNamespace();
		if (namespace != null && namespace.endsWith("-automated")) {
			groupName = namespace;
		} else {
			groupName = TestConfiguration.gitLabGroup();
		}

		return getGroupId(groupName);
	}

	private Integer getGroupId(String groupName) {
		try {
			List<GitlabGroup> groups = api.getGroups();

			for (GitlabGroup group : groups) {
				if (group.getName().equals(groupName)) {
					return group.getId();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	private Stream<GitlabProject> getProjects(Integer inGroup) {
		return getProjects().filter(p -> p.getNamespace().getId().equals(inGroup));
	}

	private Stream<GitlabProject> getProjects() {
		try {
			return api.getProjects().stream();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to list projects", e);
		}
	}

	private Path pathToProject(String projectName) {
		return GitUtil.REPOSITORIES.resolve(projectName);
	}
}
