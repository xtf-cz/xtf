package cz.xtf.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.it.VerificationException;

import org.assertj.core.api.JUnitSoftAssertions;

import cz.xtf.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Simple (based on groupId regex) dependency check for test deployments / archetypes, etc.
 */
public class SimpleDependencyCheck {

	private JUnitSoftAssertions softly = null;

	public SimpleDependencyCheck() {

	}

	public SimpleDependencyCheck(JUnitSoftAssertions softly) {
		this.softly = softly;
	}

	public void verifyDeploymentHaveRightVersions( Path dir, Path settingsPath, Map<Pattern, String> groupIdRegexToExpectedVersion) throws VerificationException, IOException {

		IOUtils.TMP_DIRECTORY.toFile().mkdirs();
		Path tmp = Files.createTempDirectory(IOUtils.TMP_DIRECTORY, dir.getFileName().toString());

		IOUtils.copy(dir, tmp);

		MavenUtil.forProject(tmp, settingsPath).forkJvm().addCliOptions("-X").executeGoals("dependency:tree");

		final Pattern pattern = Pattern.compile(".* (([^ :]+):([^ :]+):([^ :]+):([^ :]+)(:[^ :]+)?(:[^ :]+)?)");

		Files.lines(tmp.resolve("log.txt"))
				.map(line -> pattern.matcher(line))
				.filter(m -> m.find())
				.forEach(t -> {
					for (Map.Entry<Pattern, String> entry : groupIdRegexToExpectedVersion.entrySet()) {

						if (entry.getKey().matcher(t.group(1)).matches()) {

							String version;
							String groupId;
							String artifactId;

							groupId = t.group(2);
							artifactId = t.group(3);

							// We support the following
							// cz.xtf.karaf:karaf-shell:bundle:1.0-SNAPSHOT   (4 elements)
							// com.sun.xml.bind:jaxb-impl:jar:2.2.11.redhat-2:compile   (5 elements)
							// org.apache.karaf.assemblies.features:pax-cdi-features:xml:features:2.4.0.redhat-630159:compile  (6 elements)

							if (t.group(7) != null) {
								version = t.group(6).substring(1); // remove the initial :
							}
							else {
								version = t.group(5);
							}

							if (softly != null) {
								softly.assertThat(version)
										.as("" + dir + " " + groupId + ":" + artifactId + " version")
										.isEqualTo(entry.getValue());
							}
							else {
								assertThat(version)
										.as("" + dir + " " + groupId + ":" + artifactId + " version")
										.isEqualTo(entry.getValue());
							}
						}
					}
				});
	}
}
