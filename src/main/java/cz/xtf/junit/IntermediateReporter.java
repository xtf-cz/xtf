package cz.xtf.junit;

import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import cz.xtf.junit.annotation.KnownIssue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class IntermediateReporter extends RunListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(IntermediateReporter.class);

	private final Document xml;
	private final Path reportPath;
	private final boolean working;

	private final AtomicInteger tests = new AtomicInteger(0);
	private final AtomicInteger errors = new AtomicInteger(0);
	private final AtomicInteger failures = new AtomicInteger(0);
	private final AtomicInteger ignored = new AtomicInteger(0);
	private final AtomicLong suiteStartTime = new AtomicLong(0L);

	private final AtomicReference<Element> testsuite = new AtomicReference<>();

	private final Map<String, Long> testTimes = new HashMap<>();

	public IntermediateReporter() {
		boolean working = true;
		Document xml = null;
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			xml = builder.newDocument();
		} catch (ParserConfigurationException ex) {
			LOGGER.error("Failed to create XML DOM, reporting will not be provided", ex);
			working = false;
		}

		Path reportPath = null;
		try {
			reportPath = Paths.get("log", "junit-report.xml").toAbsolutePath().normalize();
			Files.createFile(reportPath);
		} catch (IOException ex) {
			LOGGER.error("Failed to create destination file, reporting will not be provided", ex);
			working = false;
		}

		this.working = working;
		this.xml = xml;
		this.reportPath = reportPath;
	}

	@Override
	public void testRunStarted(Description description) throws Exception {
		if (working) {
			suiteStartTime.set(System.currentTimeMillis());

			Element testsuite = xml.createElement("testsuite");

			if (description.getChildren().size() == 1) {
				testsuite.setAttribute("name", safeString(description.getChildren().get(0).getDisplayName()));
			}

			xml.appendChild(testsuite);
			this.testsuite.set(testsuite);
			writeXml();
		}
	}

	@Override
	public void testStarted(Description description) throws Exception {
		if (working) {
			testTimes.put(description.getDisplayName(), System.currentTimeMillis());
		}
	}

	@Override
	public void testFinished(Description description) throws Exception {
		if (working) {
			if (testTimes.containsKey(description.getDisplayName())) {
				testsuite.get().appendChild(createTestCase(description));
				writeXml();
			}
		}
	}

	@Override
	public void testAssumptionFailure(Failure failure) {
		if (working) {
			ignored.incrementAndGet();

			Element testcase = createTestCase(failure.getDescription());
			Element skipped = xml.createElement("skipped");
			skipped.setAttribute("message", safeString(failure.getMessage()));

			testcase.appendChild(skipped);

			testsuite.get().appendChild(testcase);
			writeXml();
		}
	}

	private boolean isKnownIssue(Failure failure) {

		if (failure == null) {
			return false;
		}

		if (failure.getDescription() == null) {
			return false;
		}

		final KnownIssue knownIssue = failure.getDescription().getAnnotation(KnownIssue.class);
		final String message = failure.getMessage();

		if (knownIssue == null || message == null || knownIssue.value() == null) {
			return false;
		}

		if (message.contains(knownIssue.value())) {
			return true;
		}

		return false;
	}

	@Override
	public void testFailure(Failure failure) throws Exception {
		if (working) {
			if (failure.getDescription().getMethodName() == null) {
				// before class failed
				for (Description child : failure.getDescription().getChildren()) {
					// mark all methods failed
					testFailure(new Failure(child, failure.getException()));
				}
			} else {
				// normal failure
				Element testcase = createTestCase(failure.getDescription());

				Element element;
				if (failure.getException() instanceof AssertionError) {

					if (isKnownIssue(failure)) {
						ignored.incrementAndGet();
						element = xml.createElement("skipped");
					}
					else {
						failures.incrementAndGet();
						element = xml.createElement("failure");
					}
				} else {
					errors.incrementAndGet();
					element = xml.createElement("error");
				}

				testcase.appendChild(element);

				element.setAttribute("type", safeString(failure.getException().getClass().getName()));
				element.setAttribute("message", safeString(failure.getMessage()));
				element.appendChild(xml.createCDATASection(safeString(failure.getTrace())));

				testsuite.get().appendChild(testcase);
				writeXml();
			}
		}
	}

	@Override
	public void testIgnored(Description description) throws Exception {
		if (working) {
			ignored.incrementAndGet();

			Element testcase = createTestCase(description);

			Element skipped = xml.createElement("skipped");
			skipped.setAttribute("message", safeString(description.getAnnotation(Ignore.class).value()));

			testcase.appendChild(skipped);

			testsuite.get().appendChild(testcase);
			writeXml();
		}
	}

	@Override
	public void testRunFinished(Result result) throws Exception {
		if (working) {
			writeXml();
		}
	}

	private void writeXml() {
		Element testsuite = this.testsuite.get();

		testsuite.setAttribute("tests", Integer.toString(tests.get()));
		testsuite.setAttribute("errors", Integer.toString(errors.get()));
		testsuite.setAttribute("skipped", Integer.toString(ignored.get()));
		testsuite.setAttribute("failures", Integer.toString(failures.get()));
		testsuite.setAttribute("time", computeTestTime(suiteStartTime.get()));

		try (Writer writer = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
			Transformer t = TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			t.transform(new DOMSource(xml), new StreamResult(writer));
		} catch (TransformerConfigurationException ex) {
			LOGGER.error("Misconfigured transformer", ex);
		} catch (TransformerException ex) {
			LOGGER.error("Unable to save XML file", ex);
		} catch (IOException ex) {
			LOGGER.warn("Unable to open report file", ex);
		}
	}

	private String computeTestTime(Long startTime) {
		if (startTime == null) {
			return "0";
		} else {
			long amount = System.currentTimeMillis() - startTime;
			return String.format("%.3f", amount / 1000F);
		}
	}

	private Element createTestCase(Description description) {
		tests.incrementAndGet();

		Element testcase = xml.createElement("testcase");

		testcase.setAttribute("name", safeString(description.getMethodName()));
		testcase.setAttribute("classname", safeString(description.getClassName()));
		testcase.setAttribute("time", computeTestTime(testTimes.remove(description.getDisplayName())));

		return testcase;
	}

	private String safeString(String input) {
		if (input == null) {
			return "null";
		}

		return input
				// first remove color coding (all of it)
				.replaceAll("\u001b\\[\\d+m", "")
				// then remove control characters that are not whitespaces
				.replaceAll("[\\p{Cntrl}&&[^\\p{Space}]]", "");
	}
}
