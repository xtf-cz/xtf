package cz.xtf.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.io.IOUtils;
import cz.xtf.ssh.SshUtil;

public final class GitUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(GitUtil.class);
	public static final Path REPOSITORIES = IOUtils.TMP_DIRECTORY.resolve("git");

	private GitUtil() {
		// prevent instantiation
	}

	public static GitProject cloneRepository(Path target, String url) {
		return cloneRepository(target, url, null, null, "master");
	}

	public static GitProject cloneRepository(Path target, String url, String branch) {
		return cloneRepository(target, url, null, null, branch);
	}

	public static GitProject cloneRepository(Path target, String url, String username, String password) {
		return cloneRepository(target, url, username, password, "master");
	}

	public static GitProject cloneRepository(Path target, String url, String username, String password, String branch) {
		SshSessionFactory.setInstance(SshUtil.getInstance().initSshSessionFactory());

		if (Files.exists(target)) {
			try {
				FileUtils.deleteDirectory(target.toFile());
			} catch (IOException e) {
				LOGGER.warn("Unable to delete directory", e);
			}
		}

		//CloneCommand cloneCommand = Git.cloneRepository().setURI(url).setDirectory(target.toFile());
		CloneCommand cloneCommand = Git.cloneRepository().setBranch(branch).setURI(url).setDirectory(target.toFile()); 

		CredentialsProvider credentials = null;
		if (username != null && password != null) {
			credentials = new UsernamePasswordCredentialsProvider(username, password);
			cloneCommand = cloneCommand.setCredentialsProvider(credentials);
		}
		try {
			Git git = cloneCommand.call();
			if (StringUtils.isNotBlank(branch)) {
				git.checkout().setName(branch).call();
			}

			return new GitProject(target, git, credentials);
		} catch (GitAPIException ex) {
			throw new RuntimeException("Unable to clone git repository", ex);
		}
	}

	public static GitProject initRepository(Path target) {
		return initRepository(target, null, null, null);
	}

	public static GitProject initRepository(Path target, String url) {
		return initRepository(target, url, null, null);
	}

	public static GitProject initRepository(Path target, String url, String username, String password) {
		SshSessionFactory.setInstance(SshUtil.getInstance().initSshSessionFactory());

		InitCommand initCommand = Git.init().setDirectory(target.toFile());

		CredentialsProvider credentials = null;
		if (username != null && password != null) {
			credentials = new UsernamePasswordCredentialsProvider(username, password);
		}

		try {
			Git repo = initCommand.call();

			GitProject project = new GitProject(target, repo, credentials);
			project.setUrl(url);

			return project;
		} catch (GitAPIException ex) {
			throw new RuntimeException("Unable to init git repository", ex);
		}
	}

	public static boolean checkIfRepositoryExist(String url) {
		try {
			Git.lsRemoteRepository().setRemote(url).call();
		} catch (GitAPIException e) {
			LOGGER.debug("Repository \"" + url + "\" does not exist.");
			return false;
		}

		return true;
	}
}
