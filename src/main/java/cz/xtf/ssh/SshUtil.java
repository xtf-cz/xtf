package cz.xtf.ssh;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import cz.xtf.TestConfiguration;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import cz.xtf.io.IOUtils;

public class SshUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(SshUtil.class);
	private static final JSch JSCH = new JSch();

	private static final SshUtil INSTANCE = new SshUtil();

	private SshUtil() {
		try {
			JSCH.addIdentity(getPrivateKeyPath());
		} catch (JSchException ex) {
			LOGGER.error("Invalid private key", ex);
		}
	}

	public String getPrivateKeyPath() {

		Path p;

		String keyPath = TestConfiguration.masterSshKeyPath();
		if (keyPath != null) {
			p = FileSystems.getDefault().getPath(keyPath);
			if (p.toFile().exists()) {
				return p.toString();
			}
		}

		/*p = IOUtils.findProjectRoot().resolve("infra").resolve("xtf");
		if (p.toFile().exists()) {
			return p.toString();
		}*/
		p = FileSystems.getDefault().getPath("/ssh", "ssh-key");
		if (p.toFile().exists()) {
			return p.toString();
		}
		throw new IllegalStateException("Cannot load SSH private key");
	}

	public String getPrivateKey() {
		try {
			return IOUtils.readInputStream(new FileInputStream(getPrivateKeyPath()));
		} catch (IOException e) {
			LOGGER.error("Cannot read SSH key", e);
			throw new IllegalStateException("Cannot read SSH key", e);
		}
	}

	public Session createSshSession(String username, String host) {
		return createSshSession(username, host, 22);
	}

	public Session createSshSession(String username, String host, int port) {
		try {
			Session result = JSCH.getSession(username, host, port);
			result.setConfig("StrictHostKeyChecking", "no");
			result.setConfig("PreferredAuthentications", "publickey");

			return result;
		} catch (JSchException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static SshUtil getInstance() {
		return INSTANCE;
	}

	public JschConfigSessionFactory initSshSessionFactory() {
		return new JschConfigSessionFactory() {
			@Override
			protected void configure(OpenSshConfig.Host hc, Session session) {
				session.setConfig("StrictHostKeyChecking", "no");
			}

			// add JSch instance to SessionFactory to enable key auth for OSE Git repo
			@Override
			protected JSch getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
				return JSCH;
			}
		};
	}
}
