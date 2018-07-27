package cz.xtf.openshift.logs;

import org.assertj.core.api.AbstractStandardSoftAssertions;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.openshift.OpenshiftUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.Pod;

public class LogChecker {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogChecker.class);
	private static final Pattern defaultLogErrors = Pattern.compile("FATAL|SEVERE|ERROR|WARN|Exception");

	private final Pattern logErrors;
	private final Pattern logWhitelist;
	private final Map<Pattern, String> logReportedIssues;
	private final AbstractStandardSoftAssertions softly;

	public LogChecker(Pattern logErrors, Pattern logWhitelist, AbstractStandardSoftAssertions softly, Map<Pattern, String> logReportedIssues) {
		this.logErrors = logErrors;
		this.logWhitelist = logWhitelist;
		this.softly = softly;
		this.logReportedIssues = (logReportedIssues != null) ? logReportedIssues : new HashMap<>();
	}

	public LogChecker(Pattern logErrors, Pattern logWhitelist, AbstractStandardSoftAssertions softly) {
		this(logErrors, logWhitelist, softly, null);
	}

	public LogChecker(Pattern logErrors, Pattern logWhitelist) {
		this(logErrors, logWhitelist, null, null);
	}

	public static LogChecker withAdditionalWhitelist(LogChecker checker, String... whitelist) {
		StringBuilder sb = new StringBuilder();
		sb.append(checker.getLogWhitelist().pattern());

		for (String white : whitelist) {
			sb.append("|");
			sb.append(white);
		}
		String resultingRegex = whitelist.length == 0 ? "$^" : sb.toString();
		return new LogChecker(checker.logErrors, Pattern.compile(resultingRegex), checker.softly, checker.logReportedIssues);
	}

	public static LogChecker withAdditionalWhitelist(LogChecker checker, AbstractStandardSoftAssertions softly, String... whitelist) {
		StringBuilder sb = new StringBuilder();
		sb.append(checker.getLogWhitelist().pattern());

		for (String white : whitelist) {
			sb.append("|");
			sb.append(white);
		}
		String resultingRegex = whitelist.length == 0 ? "$^" : sb.toString();
		return new LogChecker(checker.logErrors, Pattern.compile(resultingRegex), softly, checker.logReportedIssues);
	}

	public static LogChecker defaultLogCheckerWithWhitelist(String... whitelist) {
		return defaultLogCheckerWithWhitelist(null, whitelist);
	}

	public static LogChecker defaultLogCheckerWithWhitelist(AbstractStandardSoftAssertions softly, String... whitelist) {
		final String builtWhiteList = String.join("|", whitelist);
		String resultingRegex = whitelist.length == 0 ? "$^" : builtWhiteList;
		return new LogChecker(defaultLogErrors, Pattern.compile(resultingRegex), softly);
	}

	/**
	 * Returns LogChecker with configured whiteList and issues that have been reported already
	 * (but tests will still fail on them)
	 *
	 * @param softly hard fail or soft fail
	 * @param reportedIssues map of known and reported issues, defined in test classes (tests will fail with given string)
	 * @param whitelist List of errors to be whitelisted
	 * @return LogChecker
	 */
	public static LogChecker defaultLogCheckerWithWhiteListAndReportedIssues(AbstractStandardSoftAssertions softly,
			Map<String, String> reportedIssues, String... whitelist) {
		final String builtWhiteList = String.join("|", whitelist);
		String resultingRegex = whitelist.length == 0 ? "$^" : builtWhiteList;
		Map<Pattern, String> regexReportedIssues = new HashMap<>();
		for (Map.Entry<String, String> entry : reportedIssues.entrySet()) {
			regexReportedIssues.put(Pattern.compile(entry.getKey()), entry.getValue());
		}
		return new LogChecker(defaultLogErrors, Pattern.compile(resultingRegex), softly, regexReportedIssues);
	}

	public Pattern getLogWhitelist() {
		return logWhitelist;
	}

	public void assertNoErrorsInLogs(Stream<String> logLines) throws IOException {

		logLines.filter(line -> logErrors.matcher(line).find()).forEach(line -> {
			if (logWhitelist.matcher(line).find()) {
				LOGGER.info("log line whitelisted: " + LogCleaner.cleanLine(line));
			} else {
				boolean reported = false;
				for (Map.Entry<Pattern, String> entry : logReportedIssues.entrySet()) {
					if (entry.getKey().matcher(line).find()) {
						failSoftly("Unexpected error in log: " + LogCleaner.cleanLine(line) + ".\n" + entry.getValue());
						reported = true;
					}
				}
				if (!reported) failSoftly("Suspicious error in log '" + LogCleaner.cleanLine(line) + "'");
			}
		});
	}

	public void assertNoErrorsInLogs(Collection<Pod> pods) throws IOException {
		pods.forEach(pod -> {
			try {
				// TODO: use some method that don't require whole log in memory
				StringReader stringReader = new StringReader(OpenshiftUtil.getInstance().getRuntimeLog(pod));
				try (BufferedReader br = new BufferedReader(stringReader)) {
					br.lines().filter(line -> logErrors.matcher(line).find()).forEach(line -> {
						if (logWhitelist.matcher(line).find()) {
							LOGGER.info("log line whitelisted: " + LogCleaner.cleanLine(line));
						} else {
							boolean reported = false;
							for (Map.Entry<Pattern, String> entry : logReportedIssues.entrySet()) {
								if (entry.getKey().matcher(line).find()) {
									failSoftly("Unexpected error in pod " + pod.getMetadata().getName() +
											" log '" + LogCleaner.cleanLine(line) + ".\n"+ entry.getValue() + "'");
									reported = true;
								}
							}
							if (!reported) failSoftly("Suspicious error in pod " + pod.getMetadata().getName() +
									" log '" + LogCleaner.cleanLine(line) + "'");
						}
					});
				}
			} catch (Exception x) {
				LOGGER.error("Failed to get logs for pod {}", pod.getMetadata().getLabels().get("name"), x);
			}
		});
	}

	public void assertNoErrorsInLogs(String appName) throws IOException {
		assertNoErrorsInLogs(LogCheckerUtils.defaultPods(appName));
	}

	private void failSoftly(String logLine){
		if (softly == null) {
			LOGGER.error(logLine);
			Assertions.fail(logLine);
		} else {
			softly.assertThat(logLine).isEmpty();
		}
	}
}
