package cz.xtf.openshift.logs;

import cz.xtf.docker.DockerContainer;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.tuple.Tuple;

import io.fabric8.kubernetes.api.model.Pod;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class LogCheckerUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(LogCheckerUtils.class);

	public static boolean[] findPatternsInLogs(Pod pod, Pattern... patterns) throws IOException {
		boolean found[] = new boolean[patterns.length];

		// TODO: use a method that don't require whole log in memory
		StringReader stringReader = new StringReader(OpenshiftUtil.getInstance().getRuntimeLog(pod));

		try (BufferedReader br = new BufferedReader(stringReader)) {
			br.lines().forEach(line -> {
				for (int i = 0; i < patterns.length; ++i) {
					Pattern pattern = patterns[i];
					if (pattern.matcher(line).find()) {
						LOGGER.info("Found pattern {} on line '{}'", pattern, LogCleaner.cleanLine(line));
						found[i] = true;
					}
				}
			});
		}

		return found;
	}

	public static boolean[] findPatternsInLogs(DockerContainer dockerContainer, Pattern... patterns) throws IOException {
		boolean found[] = new boolean[patterns.length];
		dockerContainer.dockerLogs(istream -> {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(istream, "UTF-8"))) {
				br.lines().forEach(line -> {
					for (int i = 0; i < patterns.length; ++i) {
						Pattern pattern = patterns[i];
						if (pattern.matcher(line).find()) {
							LOGGER.info("Found pattern {} on line '{}'", pattern, LogCleaner.cleanLine(line));
							found[i] = true;
						}
					}
				});
			}
		});
		return found;
	}

	public static String[] getLinesWithFoundPatternsInLogs(Pod pod, Pattern... patterns) throws IOException {
		String found[] = new String[patterns.length];

		// TODO: use a method that don't require whole log in memory
		StringReader stringReader = new StringReader(OpenshiftUtil.getInstance().getRuntimeLog(pod));

		try (BufferedReader br = new BufferedReader(stringReader)) {
			br.lines().forEach(line -> {
				for (int i = 0; i < patterns.length; ++i) {
					Pattern pattern = patterns[i];
					if (pattern.matcher(line).find()) {
						LOGGER.info("Found pattern {} on line '{}'", pattern, LogCleaner.cleanLine(line));
						found[i] = line;
					}
				}
			});
		}

		return found;
	}

	private static boolean[] vectorOr(boolean[] b1, boolean[] b2) {

		if (b1 == null && b2 != null) return b2;
		if (b2 == null && b1 != null) return b1;

		assert b1 != null;
		assert b2 != null;
		assert b1.length == b2.length;

		boolean found[] = new boolean[b1.length];
		for (int i = 0; i < found.length; ++i) {
			found[i] = b1[i] || b2[i];
		}
		return found;
	}

	public static boolean[] findPatternsInLogs(Collection<Pod> pods, Pattern... patterns) throws IOException {

		AtomicReference<boolean[]> foundRef = new AtomicReference<>(null);

		pods.forEach(pod -> {
			try {
				foundRef.set(vectorOr(foundRef.get(), findPatternsInLogs(pod, patterns)));
			} catch (Exception x) {
				LOGGER.error("Failed to get logs for pod {}", pod.getMetadata().getLabels().get("name"), x);
			}
		});

		return foundRef.get();
	}

	/**
	 * List of pods with name label matching containerName
	 *
	 * @param containerName
	 * @return
	 */
	static Collection<Pod> defaultPods(String containerName) {
		return OpenshiftUtil.getInstance().findNamedPods(containerName);
	}

	private static String formatPodLists(Collection<Pod> pods) {
		StringBuilder sb = new StringBuilder();

		sb.append('[');

		for (Pod pod : pods) {
			if (sb.length() > 1) sb.append(", ");
			sb.append(pod.getMetadata().getName());
		}
		sb.append(']');

		return sb.toString();
	}

	public static boolean[] findPatternsInLogs(String appName, Pattern... patterns) throws IOException {
		return findPatternsInLogs(defaultPods(appName), patterns);
	}

	public static void assertLogsContains(DockerContainer dockerContainer, String... strings) throws IOException {
		Pattern patterns[] = new Pattern[strings.length];
		for (int i = 0; i < strings.length; ++i) {
			patterns[i] = Pattern.compile(strings[i]);
		}

		boolean found[] = findPatternsInLogs(dockerContainer, patterns);

		for (int i = 0; i < patterns.length; ++i) {
			Assertions.assertThat(found[i]).as("Didn't find pattern '" + patterns[i].toString() + "' in docker container " + dockerContainer.getOpenShiftNode().getHostname() + " " + dockerContainer.getContainerId() + " logs").isEqualTo(true);
		}
	}

	public static void assertLogsContains(Collection<Pod> pods, String... strings) throws IOException {
		Pattern patterns[] = new Pattern[strings.length];
		for (int i = 0; i < strings.length; ++i) {
			patterns[i] = Pattern.compile(strings[i]);
		}

		boolean found[] = findPatternsInLogs(pods, patterns);

		for (int i = 0; i < patterns.length; ++i) {
			Assertions.assertThat(found[i]).as("Didn't find pattern '" + patterns[i].toString() + "' in pod " + formatPodLists(pods) + " logs").isEqualTo(true);
		}
	}

	public static void assertLogsContains(String appName, String... strings) throws IOException {
		assertLogsContains(defaultPods(appName), strings);
	}

	public static void assertLogsContains(String appName, Pattern... patterns) throws IOException {
		boolean found[] = findPatternsInLogs(appName, patterns);

		for (int i = 0; i < patterns.length; ++i) {
			Assertions.assertThat(found[i]).as("Didn't find pattern '" + patterns[i].toString() + "' in pod logs").isEqualTo(true);
		}
	}

	public static void assertLogsContains(Collection<Pod> pods, Pattern... patterns) throws IOException {
		boolean found[] = findPatternsInLogs(pods, patterns);

		for (int i = 0; i < patterns.length; ++i) {
			Assertions.assertThat(found[i]).as("Didn't find pattern '" + patterns[i].toString() + "' in pod " + formatPodLists(pods) + " logs").isEqualTo(true);
		}
	}

	/**
	 * Asserts logs contain (or not) given Pair&lt;issue-id, pattern>, and report the assert as &lt;issue> Didn't find pattern|Found pattern &lt;pattern>
	 * @param pods
	 * @param shouldFinds Pair&lt;issue-id, pattern>...
	 * @param shouldNotFinds Pair&lt;issue-id, pattern>...
	 * @throws IOException
	 */
	public static void assertLogsContainsOrNot(Collection<Pod> pods, Tuple.Pair<String, String>[] shouldFinds, Tuple.Pair<String, String>[] shouldNotFinds) throws IOException {
		Pattern patterns[] = new Pattern[shouldFinds.length + shouldNotFinds.length];

		for (int i = 0; i < shouldFinds.length; ++i) {
			patterns[i] = Pattern.compile(shouldFinds[i].getSecond());
		}

		for (int i = 0; i < shouldNotFinds.length; ++i) {
			patterns[shouldFinds.length + i] = Pattern.compile(shouldNotFinds[i].getSecond());
		}

		boolean found[] = findPatternsInLogs(pods, patterns);

		for (int i = 0; i < patterns.length; ++i) {
			if (i < shouldFinds.length) {
				Assertions.assertThat(found[i]).as(shouldFinds[i].getFirst() + " Didn't find pattern '" + patterns[i].toString() + "' in pod " + formatPodLists(pods) + "logs").isEqualTo(true);
			} else {
				Assertions.assertThat(found[i]).as(shouldNotFinds[i - shouldFinds.length].getFirst() + " Found pattern '" + patterns[i].toString() + "' in pod " + formatPodLists(pods) + "logs").isEqualTo(false);
			}
		}
	}

	public static void assertLogsContainsOrNot(Collection<Pod> pods, String[] shouldFinds, String[] shouldNotFinds) throws IOException {
		Pattern patterns[] = new Pattern[shouldFinds.length + shouldNotFinds.length];

		for (int i = 0; i < shouldFinds.length; ++i) {
			patterns[i] = Pattern.compile(shouldFinds[i]);
		}

		for (int i = 0; i < shouldNotFinds.length; ++i) {
			patterns[shouldFinds.length + i] = Pattern.compile(shouldNotFinds[i]);
		}

		boolean found[] = findPatternsInLogs(pods, patterns);

		for (int i = 0; i < patterns.length; ++i) {
			if (i < shouldFinds.length) {
				Assertions.assertThat(found[i]).as("Didn't find pattern '" + patterns[i].toString() + "' in pod " + formatPodLists(pods) + "logs").isEqualTo(true);
			} else {
				Assertions.assertThat(found[i]).as("Found pattern '" + patterns[i].toString() + "' in pod " + formatPodLists(pods) + "logs").isEqualTo(false);
			}
		}
	}

	/**
	 * Asserts logs contain (or not) given Pair&lt;issue-id, pattern>, and report the assert as &lt;issue> Didn't find pattern|Found pattern &lt;pattern>
	 * @param appName
	 * @param shouldFinds
	 * @param shouldNotFinds
	 * @throws IOException
	 */
	public static void assertLogsContainsOrNot(String appName, Tuple.Pair<String, String>[] shouldFinds, Tuple.Pair<String, String>[] shouldNotFinds) throws IOException {
		assertLogsContainsOrNot(defaultPods(appName), shouldFinds, shouldNotFinds);
	}

	public static void assertLogsContainsOrNot(String appName, String[] shouldFinds, String[] shouldNotFinds) throws IOException {
		assertLogsContainsOrNot(defaultPods(appName), shouldFinds, shouldNotFinds);
	}
}
