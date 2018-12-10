package cz.xtf.gitlab;

import org.apache.commons.io.FileUtils;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabGroup;
import org.gitlab.api.models.GitlabProject;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import cz.xtf.gitlab.config.GitLabConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitLabUtil {

	private final GitlabAPI api;

	private String gitRepoSshUrl = null;
	private String gitRepoHttpUrl = null;
	private Path gitRepoLocalPath = null;

	private static final int WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS = 5;
	private static final int WAIT_FOR_GITLAB_CREATE_PROJECT_TIME = 10000;
	private static final String WAIT_FOR_GITLAB_CREATE_PROJECT_MESSAGE = "Please try again later";

	public GitLabUtil() {
		try {
			if (GitLabConfig.token() == null) {
				api = GitlabAPI.connect(
						GitLabConfig.url(),
						GitlabAPI.connect(GitLabConfig.url(),
								GitLabConfig.username(),
								GitLabConfig.password())
								.getPrivateToken());
			} else {
				api = GitlabAPI.connect(GitLabConfig.url(),
						GitLabConfig.token());
			}
		} catch (IOException e) {
			log.error("Error while connecting to GitLab", e);
			throw new IllegalStateException(e);
		}
	}

	public String getProjectUrl(String projectName) {
		Optional<GitlabProject> project;

		if (GitLabConfig.group() != null) {
			// try the projects from assigned group
			project = getProjects(getGroupId())
					.filter(p -> p.getName().equals(projectName))
					.findFirst();
			if (!project.isPresent() && getGroupId() != getGroupId(GitLabConfig.group())) {
				log.debug("Couldn't find project {} in assigned group", projectName);

				// try the default group
				project = getProjects(getGroupId(GitLabConfig.group()))
						.filter(p -> p.getName().equals(projectName))
						.findFirst();
			}
		} else {
			project = getProjects()
					.filter(p -> p.getName().equals(projectName))
					.findFirst();
		}

		if (!project.isPresent()) {
			throw new IllegalArgumentException("Project '" + projectName + "' was not found");
		}

		return project.get().getHttpUrl();
	}

	private GitProject createAndInitGitLabProject() {
		GitProject project;
		if (GitLabConfig.Protocol.HTTP.equals(GitLabConfig.protocol())) {
			project = GitUtil.initRepository(gitRepoLocalPath, gitRepoHttpUrl,
					GitLabConfig.username(),
					GitLabConfig.password());
		} else {
			project = GitUtil.initRepository(gitRepoLocalPath, gitRepoSshUrl);
		}
		project.setHttpUrl(gitRepoHttpUrl);
		return project;
	}

	public GitProject createProject() {
		return createProject(null);
	}

	public GitProject createProject(String name) {
		initGitLabProject(name, null);

		return createAndInitGitLabProject();
	}

	public GitProject createProjectFromPath(Path path) {
		return createProjectFromPath(null, path);
	}
	
	public GitProject createProjectFromPath(String name, Path path) {
		initGitLabProject(name, null);

		Path sourcePath = path.toAbsolutePath();

		try {
			FileUtils.copyDirectory(sourcePath.toFile(), gitRepoLocalPath.toFile());
		} catch (IOException e) {
			log.error("Error when copying files to the local repository", e);
			return null;
		}

		GitProject project = createAndInitGitLabProject();

		log.debug("Project \"{}\" was created", name);
		return project;
	}

	private void initGitLabProject(String name, String url) {
		if (name == null || name.length() == 0) {
			name = UUID.randomUUID().toString();
		}

		try {
			GitlabProject project = null;
			int attempt = 0;
			IOException lastExcepion = null;
			while(project == null && attempt < WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS) {
				++attempt;
				try {
					Integer groupId = getGroupId();
					project = api.createProject(name, groupId, null, null, null, null, null, null, true,
							null, url);
				} catch (IOException x) {
					lastExcepion = x;
					if (!x.getMessage().contains(
							WAIT_FOR_GITLAB_CREATE_PROJECT_MESSAGE)
							|| attempt >= WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS) {
						throw x;
					} else {
						log.debug("Error when creating project in GitLab. Attempt {} of {}", attempt, WAIT_FOR_GITLAB_CREATE_PROJECT_MAX_ATTEMPTS, x);
					}

					try {
						Thread.sleep(WAIT_FOR_GITLAB_CREATE_PROJECT_TIME);
					} catch (InterruptedException e) {
						throw x;
					}
				}
			}

			if (project == null) {
				throw new IllegalStateException("Failed to create git repository", lastExcepion);
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
			log.error("Error when creating project in GitLab", e);
		}
	}

	public boolean deleteAllProjects() {
		log.debug("Deleting all projects in this namespace.");
		Stream<GitlabProject> projects = GitLabConfig.group() != null ? getProjects(getGroupId()) : getProjects();
		return projects.map(p -> deleteProject(p.getName())).reduce(true, (b1, b2) -> b1 && b2);
	}

	public boolean deleteProject(String name) {
		Stream<GitlabProject> projects = GitLabConfig.group() != null ? getProjects(getGroupId()) : getProjects();
		return projects.filter(p -> p.getName().equals(name))
				.map(project -> {
					try {
						performDeleteProject(project.getId());
						FileUtils.deleteDirectory(pathToProject(project.getName()).toFile());
						log.debug("Project \"{}\" was deleted", name);
					} catch (IOException e) {
						if (e.getMessage().startsWith("Can not instantiate value of type [simple type, class java.lang.Void]")) {
							log.debug("Project \"{}\" was deleted", name);

							// known exception
							log.trace("Caught known exception when deleting project in GitLab", e);
						} else {
							log.error("Error when deleting project in GitLab", e);
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
		return getGroupId(GitLabConfig.group());
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
		if (inGroup == null) {
			return getProjects();
		}
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
