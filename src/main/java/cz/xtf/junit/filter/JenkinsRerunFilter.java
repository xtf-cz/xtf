package cz.xtf.junit.filter;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import cz.xtf.TestConfiguration;
import cz.xtf.http.HttpClient;
import cz.xtf.tuple.Tuple;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * A type of {@link DefaultExclusionTestFilter} filtering test classes using a jenkins run that don't contain failed test, but don't exclude those that don't even have a passed one
 */
@ToString
@EqualsAndHashCode
@Slf4j
public class JenkinsRerunFilter implements ExclusionTestClassFilter {

	final static int BUILDS_TO_TRY = 20;

	// Class names containing at least one passed test
	@Getter
	private Set<String> passedClassNames;

	// Class names containing at least one non-passed test
	@Getter
	private Set<String> failedClassNames;

	private boolean isEnabled = false;

	private boolean isInitialized = false;

	private HttpClient jenkinsHttpGet(String url, String username, String password) throws IOException, InterruptedException {
		HttpClient client;
		// TODO: use RedHat CA truststore
		if (StringUtils.isNotBlank(username)) {
			log.info("curl -k --user " + TestConfiguration.ciUsername() + ":<blank> " + url);
			client = HttpClient.get(url)
					.basicAuth(username, password)
					.preemptiveAuth()
					.trustStore(null, (char[]) null);
		} else {
			log.info("curl -k " + url);

			client = HttpClient.get(url)
					.trustStore(null, (char[]) null);
		}

		return client;
	}

	private HttpClient jenkinsHttpGet(String url) throws IOException, InterruptedException {
		return jenkinsHttpGet(url, TestConfiguration.ciUsername(), TestConfiguration.ciPassword());
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
				Tuple.Pair<String, Integer> responseAndCode = null;
				try {
					responseAndCode = jenkinsHttpGet(prefix + "/" + build + "/api/xml").responseAndCode();
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				} catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				}

				if (responseAndCode.getSecond() != 200) {
					continue builds;
				}

				// We ignore aborted runs
				try {
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					DocumentBuilder db = dbf.newDocumentBuilder();
					StringReader sr = new StringReader(responseAndCode.getFirst());

					Document doc = db.parse(new ReaderInputStream(sr));
					NodeList nodeList = doc.getElementsByTagName("result");
					for (int i = 0; i < nodeList.getLength(); ++i) {
						Element result = (Element) nodeList.item(i);
						if ("ABORTED".equals(result.getTextContent().trim())) {
							log.info("ignoring ABORTED build " + prefix + build);
							continue builds;
						}

						break builds;
					}
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
				} catch (SAXException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (build >= minBuild) {
				log.info("using build " + prefix + build);
				jobToRerun = prefix + build;
			}
			else {
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

		String jobToRerun = TestConfiguration.testJenkinsRerun();

		passedClassNames = new HashSet<>();
		failedClassNames = new HashSet<>();

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

				String responseXml = jenkinsHttpGet(jobToRerun + "/testReport/api/xml").response();

				StringReader sr = new StringReader(responseXml);

				Document doc = db.parse(new ReaderInputStream(sr));

				NodeList nodeList = doc.getElementsByTagName("case");
				for (int i = 0; i < nodeList.getLength(); ++i) {
					Element caseElement = (Element) nodeList.item(i);

					String className = caseElement.getElementsByTagName("className").item(0).getTextContent();
					String status = caseElement.getElementsByTagName("status").item(0).getTextContent();

					log.trace("className {} status {}", className, status);

					if ("SKIPPED".equals(status)) {
						// do nothing, don't count as passed, and don't count as failed either...
						// if there are only skipped tests, no harm in not excluding it
						// but if there are only passed and skipped, no need to rerun (as we expect they would be skipped again anyway)
					} else if (("PASSED".equals(status) || "FIXED".equals(status))) {
						passedClassNames.add(className);
					} else {
						failedClassNames.add(className);
					}
				}

				isEnabled = true;

			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean exclude(Class<?> testClass) {

		lazyInit();

		if (isEnabled) {
			// Exclude those that were not mentioned at all in the previous build
			if (!failedClassNames.contains(testClass.getName()) && !passedClassNames.contains(testClass.getName())) {
				log.debug("Excluding " + testClass.getName() + " not mentioned in previous test results");
				return true;
			}

			if (!failedClassNames.contains(testClass.getName()) && passedClassNames.contains(testClass.getName())) {
				log.debug("Excluding " + testClass.getName() + " containing only passed tests in previous test results");
				return true;
			}
		}

		return false;
	}
}
