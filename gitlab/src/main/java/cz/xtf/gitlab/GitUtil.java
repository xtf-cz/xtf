package cz.xtf.gitlab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import cz.xtf.gitlab.config.GitLabConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GitUtil {
	public static final Path REPOSITORIES = Paths.get("tmp").toAbsolutePath().resolve("git");

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
		SshSessionFactory.setInstance(initSshSessionFactory());

		if (Files.exists(target)) {
			try {
				FileUtils.deleteDirectory(target.toFile());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

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
		SshSessionFactory.setInstance(initSshSessionFactory());

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
			log.debug("Repository \"" + url + "\" does not exist.");
			return false;
		}

		return true;
	}

	private static JschConfigSessionFactory initSshSessionFactory() {
		final JSch jSch = new JSch();

		try {
			if (GitLabConfig.sshIdentityFile() != null) {
				jSch.addIdentity(GitLabConfig.sshIdentityFile());
			}
		}
		catch(JSchException x) {
			throw new RuntimeException(x);
		}

		return new JschConfigSessionFactory() {
			@Override
			protected void configure(OpenSshConfig.Host hc, Session session) {
				session.setConfig("StrictHostKeyChecking", "no");
			}

			// add JSch instance to SessionFactory to enable key auth for OSE Git repo
			@Override
			protected JSch getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
				return jSch;
			}
		};
	}
}
