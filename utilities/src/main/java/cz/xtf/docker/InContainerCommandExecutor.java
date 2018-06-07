package cz.xtf.docker;

import io.fabric8.kubernetes.api.model.Pod;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class InContainerCommandExecutor {
	private DockerContainer dockerContainer;

	/**
	 * Creates instance connected to specified pod.
	 *
	 * @param pod in which commands should be executed
	 * @return new class instance
	 */
	public static InContainerCommandExecutor inPod(Pod pod) {
		return new InContainerCommandExecutor(pod);
	}

	private InContainerCommandExecutor(Pod pod) {
		dockerContainer = DockerContainer.createForPod(pod);
	}

	/**
	 * Returns content of directory specified by path without hidden dirs. Runs "ls -1 path" command in container
	 * and splits result by new line.
	 *
	 * @param path which will be queried
	 * @return directory content
	 */
	public List<String> listDirContent(String path) {
		String cmd = String.format("ls -1 %s", path);
		return executeBashCommandAndExpectList(cmd);
	}

	/**
	 * Returns content of directory specified by path with hidden dirs. Runs "ls -a1 path" command in container
	 * and splits result by new line.
	 *
	 * @param path which will be queried
	 * @return directory content
	 */
	public List<String> listFullDirContent(String path) {
		String cmd = String.format("ls -a1 %s", path);
		return executeBashCommandAndExpectList(cmd);
	}

	/**
	 * Executes env command in container and parses result.
	 *
	 * @return map of environments in container
	 */
	public Map<String, String> listEnvs() {
		Map<String, String> containerEnv = new HashMap<>();

		Stream.of(executeCommand("env").split("\n")).forEach(env -> {
			String[] parsedEnv = env.split("=", 2);
			containerEnv.put(parsedEnv[0], parsedEnv[1]);
		});

		return containerEnv;
	}

	/**
	 * Executes given bash command. (Wraps given command with "bash -c "\command"\").
	 *
	 * @param command that will be executed as bash command
	 * @return whatever the result is
	 */
	public String executeBashCommand(String command) {
		String cmd = String.format("bash -c \"%s\"", command);
		return executeCommand(cmd);
	}

	/**
	 * Executes given command as "docker exec containerID command".
	 *
	 * @param command to be executed
	 * @return whatever is the result
	 */
	public String executeCommand(String command) {
		return dockerContainer.dockerCmd(containerID -> String.format("docker exec %s %s", containerID, command));
	}

	/**
	 * Executes bash command in container and parses the result by new lines.
	 * (Wraps given command with "bash -c "\command"\").
	 *
	 * @param command to be executed
	 * @return output parsed by new lines
	 */
	public List<String> executeBashCommandAndExpectList(String command) {
		String cmd = String.format("bash -c \"%s\"", command);
		return executeCommandAndExpectList(cmd);
	}

	/**
	 * Executes given command as "docker exec containerID command" and parses the result by new line.
	 *
	 * @param command that will be executed
	 * @return output parsed by new lines
	 */
	public List<String> executeCommandAndExpectList(String command) {
		String[] result = StringUtils.split(executeCommand(command), '\n');
		return Arrays.asList(result);
	}
}
