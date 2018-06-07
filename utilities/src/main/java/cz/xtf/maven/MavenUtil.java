package cz.xtf.maven;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;

import cz.xtf.TestConfiguration;
import cz.xtf.http.HttpUtil;
import cz.xtf.openshift.OpenshiftUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.fabric8.openshift.api.model.Route;

public class MavenUtil {
	private final Verifier maven;

	private MavenUtil(Verifier maven) {
		this.maven = maven;
		maven.setForkJvm(false);
	}

	public static MavenUtil forProject(Path projectPath) throws VerificationException {
		Verifier verifier = new Verifier(projectPath.toAbsolutePath().toString());

		return new MavenUtil(verifier);
	}

	public static MavenUtil forProject(Path projectPath, Path settingsXmlPath) throws VerificationException {
		Verifier verifier = new Verifier(projectPath.toAbsolutePath().toString(), settingsXmlPath.toAbsolutePath().toString());

		MavenUtil result = new MavenUtil(verifier);
		result.useSettingsXml(settingsXmlPath);

		return result;
	}

	public MavenUtil useSettingsXml(Path settingsXmlPath) {
		maven.addCliOption("-s " + settingsXmlPath.toAbsolutePath().toString());
		return this;
	}

	public MavenUtil disableAutoclean() {
		maven.setAutoclean(false);
		return this;
	}

	public MavenUtil forkJvm() {
		maven.setForkJvm(true);

		// copy the DNS configuration
		if (System.getProperty("sun.net.spi.nameservice.nameservers") != null) {
			maven.setSystemProperty("sun.net.spi.nameservice.nameservers", System.getProperty("sun.net.spi.nameservice.nameservers"));
			maven.setSystemProperty("sun.net.spi.nameservice.provider.1", "dns,sun");
			maven.setSystemProperty("sun.net.spi.nameservice.provider.2", "default");
		}

		return this;
	}

	/**
	 * Configures maven to store an artifact in maven proxy.
	 * @return this - to fulfill fluent api pattern
	 */
	public MavenUtil deployToSnapshots() {
		return deployToRepository("maven", TestConfiguration.mavenDeploySnapshotURL());
	}

	public MavenUtil deployToRelease() {
		return deployToRepository("maven", TestConfiguration.mavenDeployReleaseURL());
	}

	public MavenUtil deployToRepository(String name, String repository) {
		maven.setSystemProperty("altDeploymentRepository", String.format("%s::default::%s", name, repository));
		return this;
	}

	public void executeGoals(String... goals) throws VerificationException {
		try {
			maven.executeGoals(Arrays.asList(goals));
		} finally {
			// always reset System.out and System.in streams
			maven.resetStreams();
		}
	}

	public MavenUtil addCliOptions(List<String> options) {
		//use add to avoid override of default options
		options.stream().forEach(maven::addCliOption);
		return this;
	}

	public MavenUtil addCliOptions(String... options) {
		addCliOptions(Arrays.asList(options));
		return this;
	}

	public MavenUtil setEnvironmentVariable(String key, String value) {
		this.maven.setEnvironmentVariable(key, value);
		return this;
	}

	/**
	 * Check if a maven artifact repository is running in Openshift.
	 * Repository is searched for under a route with name 'maven'.
	 *
	 * @return true if http GET on route's host URL returns 200, false otherwise
	 */
	public static boolean checkRepositoryAvailability() {
		Optional<Route> routeOptional = OpenshiftUtil.getInstance().withAdminUser(x -> x.routes().inNamespace("test-infra").list().getItems()).stream()
				.filter(r -> r.getSpec().getHost().startsWith("maven"))
				.findAny();

		if(routeOptional.isPresent()) {
			try {
				HttpUtil.httpGet("http://"+routeOptional.get().getSpec().getHost());
			} catch(Exception e) {
				return false;
			}
		} else {
			return false;
		}
		return true;
	}
}
