package cz.xtf.junit5.extensions;

import static cz.xtf.core.http.Https.getHttpsConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import cz.xtf.junit5.config.JUnitConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JenkinsRerunCondition implements ExecutionCondition {

    private final static ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("not disabled");
    private final static ConditionEvaluationResult ENABLED_NOT_CLASS = ConditionEvaluationResult.enabled("not a testclass");
    private final static int BUILDS_TO_TRY = 20;

    // Class names containing at least one passed test
    private Set<String> passedTests;
    // Class names containing at least one failed test
    private Set<String> failedTests;

    private boolean isEnabled = false;
    private boolean isInitialized = false;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
        final Optional<Class<?>> optionalTestClass = extensionContext.getTestClass();

        if (!optionalTestClass.isPresent()) {
            return ENABLED_NOT_CLASS;
        }

        Class<?> testClass = optionalTestClass.get();

        lazyInit();

        if (isEnabled) {
            // We operate on the class-level, as parsing test names is not reliable (due to parametrized tests and such)
            // We assume that if the previous run contains test of a class and no failures, it can be safely skip.
            if (!failedTests.contains(testClass.getName()) && passedTests.contains(testClass.getName())) {
                log.debug("Excluding " + testClass.getName() + " containing only passed tests in previous test results");
                return ConditionEvaluationResult.disabled("Disabling " + extensionContext.getDisplayName() + ", All tests from "
                        + testClass.getName() + " passed in previous run");
            }
        }

        return ENABLED;
    }

    private String readContent(InputStream inputStream) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine).append("\n");
        }
        in.close();
        return content.toString().trim();
    }

    private String jenkinsHttpGet(String url, String username, String password) throws IOException {
        String content;

        // TODO: use RedHat CA truststore
        HttpURLConnection connection = getHttpsConnection(new URL(url));

        if (StringUtils.isNotBlank(username)) {
            log.info("curl -k --user " + username + ":<blank> " + url);

            String basicAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + basicAuth);
        } else {
            log.info("curl -k " + url);
        }

        connection.connect();
        content = readContent(connection.getInputStream());

        if (connection.getResponseCode() != 200) {
            content = null;
        }

        connection.disconnect();

        return content;
    }

    private String jenkinsHttpGet(String url) throws IOException {
        return jenkinsHttpGet(url, JUnitConfig.ciUsername(), JUnitConfig.ciPassword());
    }

    private String findJobToRerun() {
        String jobToRerun = null;

        String currentBuildUrl = System.getenv("BUILD_URL");
        Pattern buildNoPattern = Pattern.compile("(.*/)([0-9]+)/$");

        Matcher m = buildNoPattern.matcher(currentBuildUrl);
        if (m.matches()) {
            String prefix = m.group(1);
            String buildNo = m.group(2);

            int build = Integer.parseInt(buildNo);
            int minBuild = build - BUILDS_TO_TRY;

            builds: for (--build; build >= minBuild; build--) {
                String response;
                try {
                    response = jenkinsHttpGet(prefix + "/" + build + "/api/xml");
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }

                if (response == null) {
                    continue;
                }

                // We ignore aborted runs
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    StringReader sr = new StringReader(response);

                    Document doc = db.parse(new ReaderInputStream(sr, StandardCharsets.UTF_8));
                    NodeList nodeList = doc.getElementsByTagName("result");
                    for (int i = 0; i < nodeList.getLength(); ++i) {
                        Element result = (Element) nodeList.item(i);
                        if ("ABORTED".equals(result.getTextContent().trim())) {
                            log.info("ignoring ABORTED build " + prefix + build);
                            continue builds;
                        }

                        break builds;
                    }
                } catch (ParserConfigurationException | SAXException | IOException e) {
                    e.printStackTrace();
                }
            }

            if (build >= minBuild) {
                log.info("using build " + prefix + build);
                jobToRerun = prefix + build;
            } else {
                log.error("Cannot configure JenkinsRerunFilter, didn't find any non-aborted run");
            }
        } else {
            log.error("Cannot configure JenkinsRerunFilter, failed to parse BUILD_URL {}", currentBuildUrl);
        }

        return jobToRerun;
    }

    /**
     * Reads the jenkins run test report to generate the list of classes to exclude
     * <p>
     * <p>
     * If the value of the system property is blank / empty, nothing is excluded.
     * </p>
     */
    private void lazyInit() {
        // initialize lazily, so that we don't need to query jenkins if no tests in testsuite
        if (isInitialized) {
            return;
        }
        isInitialized = true;

        String jobToRerun = JUnitConfig.jenkinsRerun();

        passedTests = new HashSet<>();
        failedTests = new HashSet<>();

        if (StringUtils.isNotBlank(jobToRerun) && ("true".equalsIgnoreCase(jobToRerun) || jobToRerun.startsWith("http"))) {

            if (!jobToRerun.startsWith("http")) {
                // any non-URL value means we should guess the URL on our own,
                // this means getting the current job run from BUILD_URL and decrementing the build number by one

                jobToRerun = findJobToRerun();
                if (jobToRerun == null) {
                    return;
                }
            }

            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();

                String responseXml = jenkinsHttpGet(jobToRerun + "/testReport/api/xml");

                StringReader sr = new StringReader(responseXml);

                Document doc = db.parse(new ReaderInputStream(sr, "UTF-8"));

                NodeList nodeList = doc.getElementsByTagName("case");
                for (int i = 0; i < nodeList.getLength(); ++i) {
                    Element caseElement = (Element) nodeList.item(i);

                    String className = caseElement.getElementsByTagName("className").item(0).getTextContent();
                    String status = caseElement.getElementsByTagName("status").item(0).getTextContent();
                    String name = caseElement.getElementsByTagName("name").item(0).getTextContent();

                    log.trace("className {} name {} status {}", className, name, status);

                    if ("SKIPPED".equals(status) || "PASSED".equals(status) || "FIXED".equals(status)) {
                        passedTests.add(className);
                    } else if ("FAILED".equals(status) || "REGRESSION".equals(status)) {
                        failedTests.add(className);
                    } else {
                        throw new IllegalStateException(
                                "Unrecognized test status " + status + " in test " + name + " of class " + className);
                    }
                }

                isEnabled = true;
            } catch (ParserConfigurationException | SAXException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}
