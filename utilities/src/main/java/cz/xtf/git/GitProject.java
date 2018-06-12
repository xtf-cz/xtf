package cz.xtf.git;

import org.apache.commons.io.FileUtils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GitProject {
	private static final Logger LOGGER = LoggerFactory.getLogger(GitProject.class);
	public static final String ENV_FILE_LOCATION = ".s2i/environment";

	private final Git repo;
	private final Path path;
	private CredentialsProvider credentials;
	private String httpUrl;

	public GitProject(Path location) throws IOException {
		this(location, Git.open(location.toFile()), null);
	}

	GitProject(Path path, Git repo, CredentialsProvider credentials) {
		this.path = path;
		this.repo = repo;
		this.credentials = credentials;
	}

	public String getName() {
		return path.getFileName().toString();
	}

	public String getUrl() {
		return getUrl("origin");
	}

	public void setUrl(String url) {
		setUrl(url, "origin");
	}

	public Path getPath() {
		return path;
	}

	public void setCredentials(CredentialsProvider credentials) {
		this.credentials = credentials;
	}

	public String getUrl(String remote) {
		return repo.getRepository().getConfig().getString("remote", remote, "url");
	}

	public String getRevHash() {
		try {
			return repo.getRepository().getRef("HEAD").getObjectId().getName();
		} catch (IOException ex) {
			LOGGER.error("Error retrieving HEAD revision", ex);
		}
		return null;
	}

	void setUrl(String url, String remote) {
		if (url != null && url.length() > 0) {
			StoredConfig config = repo.getRepository().getConfig();
			config.setString("remote", remote, "url", url);
			try {
				config.save();
			} catch (IOException ex) {
				throw new RuntimeException("Unable to save repository configuration", ex);
			}
		}
	}

	void setHttpUrl(String httpUrl) {
		this.httpUrl = httpUrl;
	}

	public String getHttpUrl() {
		return httpUrl;
	}

	public void pull() {
		try {
			if (credentials != null) {
				repo.pull().setCredentialsProvider(credentials).setRebase(true).call();
			} else {
				repo.pull().setRebase(true).call();
			}
		} catch (GitAPIException ex) {
			LOGGER.error("Error during pull.", ex);
		}
	}

	public void add(Path file) {
		try {
			repo.add().addFilepattern(path.relativize(file).toString()).call();
		} catch (GitAPIException ex) {
			LOGGER.error("Unable to add files", ex);
		}
	}

	public void addAll() {
		try {
			repo.add().addFilepattern(".").call();
		} catch (GitAPIException ex) {
			LOGGER.error("Unable to add all files", ex);
		}
	}

	public void commit(String message) {
		try {
			repo.commit().setAll(true).setMessage(message).call();
		} catch (GitAPIException ex) {
			LOGGER.error("Unable to commit changes.", ex);
		}
	}

	public void push() {
		try {
			if (credentials != null) {
				repo.push().setCredentialsProvider(credentials).call();
			} else {
				repo.push().call();
			}
		} catch (GitAPIException ex) {
			LOGGER.error("Error during push.", ex);
		}
	}

	public void checkout(String ref) {
		try {
			repo.checkout().setName(ref).call();
		} catch (GitAPIException ex) {
			LOGGER.error("Failed to checkout ref '" + ref + "'", ex);
		}
	}

	public void delete() {
		repo.close();
		try {
			IOUtils.deleteDirectory(path);
		} catch (IOException ex) {
			throw new RuntimeException("Failed to delete project '" + getName() + "'", ex);
		}
	}

	public void configureSTIBuildFile(Map<String, String> env) {
		try {
			Path envFile = path.resolve(ENV_FILE_LOCATION);
			FileUtils.writeLines(
					envFile.toFile(),
					env.entrySet().stream()
							.map(entry -> entry.getKey() + "=" + entry.getValue())
							.collect(Collectors.toList()), true);
			repo.add().addFilepattern(ENV_FILE_LOCATION).call();
		} catch (IOException | GitAPIException ex) {
			LOGGER.error("Error when configuring STI", ex);
			throw new IllegalStateException("Error when configuring STI", ex);
		}
	}

	public void addProfileToSTIBuild(String profileName) {
		Map<String, String> env = new HashMap<>();
		env.put("MAVEN_ARGS", "package -Popenshift," + profileName + " -DskipTests -Dcom.redhat.xpaas.repo.redhatga");
		configureSTIBuildFile(env);
	}

	public void removeSTIBuildFile() {
		Path envFile = path.resolve(ENV_FILE_LOCATION);
		if (Files.exists(envFile)) {
			try {
				repo.rm().addFilepattern(ENV_FILE_LOCATION).call();
			} catch (GitAPIException ex) {
				LOGGER.error("Error when configuring STI", ex);
				throw new IllegalStateException("Error when deleting STI configuration file", ex);
			}
		}
	}

	public void submodulesInitialization() {
		try {
			repo.submoduleInit().call();
			repo.submoduleUpdate().call();
		} catch (GitAPIException e) {
			throw new IllegalArgumentException("Unable to update submodules");
		}
	}

	public void close() {
		repo.close();
	}
}
