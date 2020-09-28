package cz.xtf.junit5.listeners;

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class TestResultWriter {
    private final Document xml;
    private final Path reportPath;

    private final AtomicInteger tests = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    private final AtomicInteger failures = new AtomicInteger(0);
    private final AtomicInteger ignored = new AtomicInteger(0);
    private final AtomicLong suiteStartTime = new AtomicLong(0L);

    private final AtomicReference<Element> testsuite = new AtomicReference<>();

    private final Map<String, Long> testTimes = new HashMap<>();

    public TestResultWriter() {
        Document xml = null;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            xml = builder.newDocument();
        } catch (ParserConfigurationException ex) {
            log.error("Failed to create XML DOM!", ex);
        }

        Path reportPath = null;
        try {
            reportPath = Paths.get("log", "junit-report.xml").toAbsolutePath().normalize();
            Files.createFile(reportPath);
        } catch (IOException ex) {
            log.error("Failed to create destination file!", ex);
        }

        this.xml = xml;
        this.reportPath = reportPath;
    }

    public void recordSuiteStart() {
        suiteStartTime.set(System.currentTimeMillis());

        Element testsuite = xml.createElement("testsuite");
        testsuite.setAttribute("name", "integration-suite");

        xml.appendChild(testsuite);
        this.testsuite.set(testsuite);
        writeXml();
    }

    public void recordTestStart(String testId) {
        testTimes.put(testId, System.currentTimeMillis());
    }

    public void recordSkippedTest(String testId, String className, String methodName, String reason) {
        ignored.incrementAndGet();

        Element testcase = createTestCase(testId, className, methodName);
        Element skipped = xml.createElement("skipped");
        skipped.setAttribute("message", reason);

        testcase.appendChild(skipped);

        testsuite.get().appendChild(testcase);
        writeXml();
    }

    public void recordSuccessfulTest(String testId, String className, String methodName) {
        if (testTimes.containsKey(testId)) {
            testsuite.get().appendChild(createTestCase(testId, className, methodName));
            writeXml();
        }
    }

    public void recordFailedTest(String testId, String className, String methodName, Throwable throwable) {
        Element testcase = createTestCase(testId, className, methodName);

        Element element;
        if (throwable instanceof AssertionError) {
            failures.incrementAndGet();
            element = xml.createElement("failure");
        } else {
            errors.incrementAndGet();
            element = xml.createElement("error");
        }

        testcase.appendChild(element);

        element.setAttribute("type", throwable.getClass().getName());
        element.setAttribute("message", throwable.getMessage());
        element.appendChild(xml.createCDATASection(stackTraceToString(throwable.getStackTrace())));

        testsuite.get().appendChild(testcase);
        writeXml();
    }

    private String stackTraceToString(StackTraceElement[] stackTrace) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement stackTraceElement : stackTrace) {
            sb.append(stackTraceElement.toString()).append("\n");
        }
        return sb.toString();
    }

    public void recordFinishedSuite() {
        writeXml();
    }

    private void writeXml() {
        Element testsuite = this.testsuite.get();

        testsuite.setAttribute("tests", Integer.toString(tests.get()));
        testsuite.setAttribute("errors", Integer.toString(errors.get()));
        testsuite.setAttribute("skipped", Integer.toString(ignored.get()));
        testsuite.setAttribute("failures", Integer.toString(failures.get()));
        testsuite.setAttribute("time", computeTestTime(suiteStartTime.get()));

        try (Writer writer = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            t.transform(new DOMSource(xml), new StreamResult(writer));
        } catch (TransformerConfigurationException ex) {
            log.error("Misconfigured transformer", ex);
        } catch (TransformerException ex) {
            log.error("Unable to save XML file", ex);
        } catch (IOException ex) {
            log.warn("Unable to open report file", ex);
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

    private Element createTestCase(String testId, String className, String methodName) {
        tests.incrementAndGet();

        Element testcase = xml.createElement("testcase");

        testcase.setAttribute("name", methodName);
        testcase.setAttribute("classname", className);
        testcase.setAttribute("time", computeTestTime(testTimes.remove(testId)));

        return testcase;
    }
}
