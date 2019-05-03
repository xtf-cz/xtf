package cz.xtf.core.openshift;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenShiftBinary {
	private final String path;

	@Getter
	private String ocConfigPath;

	public OpenShiftBinary(String path) {
		this.path = path;
	}

	public OpenShiftBinary(String path, String ocConfigPath) {
		this(path);
		this.ocConfigPath = ocConfigPath;
	}

	public void login(String url, String token) {
		this.execute("login", url, "--insecure-skip-tls-verify=true", "--token=" + token);
	}

	public void login(String url, String username, String password) {
		this.execute("login", url, "--insecure-skip-tls-verify=true", "-u", username, "-p", password);
	}

	public void project(String projectName) {
		this.execute("project", projectName);
	}

	public void startBuild(String buildConfig, String sourcePath) {
		this.execute("start-build", buildConfig, "--from-dir=" + sourcePath);
	}

	// Common method for any oc command call
	public String execute(String... args) {
		if (ocConfigPath == null) {
			return executeCommand(ArrayUtils.addAll(new String[] {path}, args));
		} else {
			return executeCommand(ArrayUtils.addAll(new String[] {path, "--config=" + ocConfigPath}, args));
		}
	}

	// Internal
	private String executeCommand(String... args) {
		ProcessBuilder pb = new ProcessBuilder(args);

		pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
		pb.redirectErrorStream(true);

		try {
			Process p = pb.start();

			ExecutorService es = Executors.newSingleThreadExecutor();

			Future<String> res = es.submit(() -> {
				try (InputStream is = p.getInputStream(); StringWriter sw = new StringWriter()) {
					IOUtils.copy(is, sw);
					sw.flush();
					return sw.toString();
				}
			});

			int result = p.waitFor();

			if (result == 0) {
				return res.get();
			} else {
				log.error("Failed while executing (code {}): {}", result, Arrays.toString(args));
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			log.error("Failed while executing: " + Arrays.toString(args), e);
		}

		return null;
	}
}
