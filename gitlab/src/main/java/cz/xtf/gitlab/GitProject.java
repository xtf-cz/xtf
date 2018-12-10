package cz.xtf.gitlab;

import org.apache.commons.io.FileUtils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;

import java.io.IOException;
import java.nio.file.Path;


public class GitProject {

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

	public Path getPath() {
		return path;
	}

	public void setCredentials(CredentialsProvider credentials) {
		this.credentials = credentials;
	}

	public String getUrl() {
		return getUrl("origin");
	}

	public String getUrl(String remote) {
		return repo.getRepository().getConfig().getString("remote", remote, "url");
	}

	public String getRevHash() {
		try {
			return repo.getRepository().getRef("HEAD").getObjectId().getName();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void setUrl(String url) {
		setUrl(url, "origin");
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
			throw new RuntimeException(ex);
		}
	}

	public void add(Path file) {
		try {
			repo.add().addFilepattern(path.relativize(file).toString()).call();
		} catch (GitAPIException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void addAll() {
		try {
			repo.add().addFilepattern(".").call();
		} catch (GitAPIException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void commit(String message) {
		try {
			repo.commit().setAll(true).setMessage(message).call();
		} catch (GitAPIException ex) {
			throw new RuntimeException(ex);
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
			throw new RuntimeException(ex);
		}
	}

	public void checkout(String ref) {
		try {
			repo.checkout().setName(ref).call();
		} catch (GitAPIException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void delete() {
		repo.close();
		try {
			FileUtils.deleteDirectory(path.toFile());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void submodulesInitialization() {
		try {
			repo.submoduleInit().call();
			repo.submoduleUpdate().call();
		} catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	public void close() {
		repo.close();
	}
}
