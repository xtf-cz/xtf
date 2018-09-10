package cz.xtf.core.openshift;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
public class OpenShiftBinary {
	private final String path;

	public OpenShiftBinary(String path) {
		this.path = path;
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
	public void execute(String... args) {
		executeCommand(ArrayUtils.addAll(new String[]{path}, args));
	}

	// Internal
	private void executeCommand(String... args) {
		ProcessBuilder pb = new ProcessBuilder(args);

		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		int result = -1;

		try {
			result = pb.start().waitFor();
		} catch (IOException | InterruptedException e) {
			log.error("Failed while executing: " + Arrays.toString(args), e);
		}

		if(result != 0) {
			log.error("Failed while executing (code {}): {}", result, Arrays.toString(args));
		}
	}
}
