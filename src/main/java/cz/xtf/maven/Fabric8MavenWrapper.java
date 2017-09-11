package cz.xtf.maven;

import cz.xtf.openshift.KubernetesVersion;
import org.apache.maven.it.VerificationException;

import org.jboss.dmr.ModelNode;

import org.junit.Assert;

import com.google.common.collect.ImmutableMap;
import cz.xtf.TestConfiguration;
import cz.xtf.io.IOUtils;
import cz.xtf.openshift.OpenShiftBinaryClient;
import cz.xtf.openshift.OpenshiftUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.client.Config;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Executes a maven goal of a project with fabric8-maven-plugin in it. Especially sets properties for openshift mode and setsup the openshift client.
 */
@Slf4j
public class Fabric8MavenWrapper {

	public static final String MASTER_URL = "masterUrl";
	public static final String MASTER_USER_NAME = "masterUserName";
	public static final String MASTER_PASSWORD = "masterPassword";
	public static final String MASTER_TOKEN = "masterToken";
	public static final String NAMESPACE = "namespace";
	private static final Path DEFAULT_SETTINGS = FileSystems.getDefault().getPath("../utilities/src/main/resources/settings.xml").toAbsolutePath();
	static final String MANIFEST_PATH = "target/classes/META-INF/fabric8/openshift.json";

	@Getter(lazy = true)
	private static final String currentNameSpace = OpenshiftUtil.getInstance().getContext().getNamespace();
	private static final Map<String, String> FABRIC8_LOGIN_PROPERTIES = new ImmutableMap.Builder<String, String>()
			.put("kubernetes.auth.basic.username", TestConfiguration.masterUsername())
			.put("kubernetes.auth.basic.password", TestConfiguration.masterPassword())
			.put("kubernetes.master", resolveIpAddressUrl(TestConfiguration.masterUrl(), false))
			.put("kubernetes.trust.certificates", "true")
			.put("kubernetes.auth.tryKubeConfig", "false")
			.put("kubernetes.auth.tryServiceAccount", "false")
			.build();

	/**
	 * I created this enum in order to keep track which goals are already covered.
	 * They are in order as mentioned in <a href="https://maven.fabric8.io/">documentation</a>.
	 *
	 * @author skrupa
	 */
	public enum Goal {

		/**
		 * Pure maven goal
		 */
		INSTALL("install", false),
		CLEAN("clean", false),
		PACKAGE("package", false),
		VERIFY("verify", false),
		// build goals
		BUILD("fabric8:build"),
		RESOURCE("fabric8:resource"),
		PUSH("fabric8:push"),
		// deployment goals
		/**
		 * should call build nad resource-deploy
		 */
		DEPLOY("fabric8:deploy"),
		/**
		 * Delete all resources deployed by deploy
		 */
		UNDEPLOY("fabric8:undeploy"),
		/**
		 * After creating resources via deploy and then subsequently stopping via stop you can resurrect deployments.
		 */
		START("fabric8:start"),
		/**
		 * scale deployed deployments to 0
		 */
		STOP("fabric8:stop"),
		/**
		 * Should connect to app and track log for you
		 */
		LOG("fabric8:log"),
		/**
		 * Enable debugging on remote pod via TCP socket - default port is 5005
		 * It is possible to change by
		 * -Dfabric8.debug.port=8000
		 */
		DEBUG("fabric8:debug"),
		/**
		 * Do rebuild of a image on a source code change.
		 * watchMode:
		 * <ul>
		 * <li> <b>build</b> -
		 * <p> Only if there is a build section in a image configuration.
		 * Watch changes in assembly</p>
		 * </li>
		 * <li><b>run</b> -
		 * <p> automatically restart container after image change - (must be used with start)</p>
		 * </li>
		 * <li> <b>copy</b> -
		 * <p> Changed files are copied into the container. </p>
		 * </li>
		 * <li> <b>both</b> -
		 * <p> build and run combined </p>
		 * </li>
		 * <li> <b>none</b> -
		 * <p> If there are images which do not need watching (i.e.) db </p>
		 * </li>
		 * </ul>
		 */
		/**
		 * Both goals has been dropped for FIS 2.0 release
		 * https://issues.jboss.org/browse/OSFUSE-540
		 * https://issues.jboss.org/browse/OSFUSE-541
		 */
//		WATCH("fabric8:watch"),
//		WATCH_SPRING("fabric8:watch-spring-boot"),
		// short cuts
		/**
		 * Do a deploy then logs - after ^C stop is called
		 * the undeploy can be called instead -   -Dfabric8.onExit=undeploy
		 */
		RUN("fabric8:run"),
		/**
		 * only regenerate the manifests and apply  into the current kubernetes cluster.
		 */
		RESOURCE_APPLY("fabric8:resource-apply"),
		HELP("fabric8:help"),
		APPLY("fabric8:apply");

		private final String goal;
		private final boolean isFmpGoal;

		Goal(String goal, boolean isFmpGoal) {
			this.goal = goal;
			this.isFmpGoal = isFmpGoal;
		}

		Goal(String goal) {
			this(goal, true);
		}

		public String getGoal() {
			return goal;
		}

		public boolean isFmpGoal() {
			return isFmpGoal;
		}
	}

	protected MavenUtil maven;
	private Path projectPath;
	private Map<String, String> overrideOptions;

	public Fabric8MavenWrapper(Path projectPath) throws VerificationException {
		this(projectPath, DEFAULT_SETTINGS);
	}

	public Fabric8MavenWrapper(Path projectPath, Path settingsPath) throws VerificationException {
		this.maven = MavenUtil.forProject(projectPath, settingsPath).forkJvm();

		this.maven.addCliOptions("-Dfabric8.imagePullPolicy=Always");

		// These should no longer be needed (https://issues.jboss.org/browse/OSFUSE-316 )
		//this.maven.addCliOptions("-Dfabric8.mode=openshift");
		//this.maven.addCliOptions("-Dfabric8.build.strategy=s2i");

		this.maven.addCliOptions("-D" + Config.KUBERNETES_KUBECONFIG_FILE + "=" + IOUtils.TMP_DIRECTORY.resolve("oc").resolve("oc.config"));
		this.maven.disableAutoclean();
		this.overrideOptions = new HashMap<>();
		this.projectPath = projectPath;
	}

	public Fabric8MavenWrapper withImage(String image) {
		this.maven.addCliOptions("-Dfabric8.generator.from=" + image);
		this.maven.addCliOptions("-Dfabric8.generator.fromMode=docker");
		return this;
	}

	/**
	 * OSFUSE-479 - routes are not created by setting this parameter anymore
	 */
	public Fabric8MavenWrapper withRoute() {
		this.maven.addCliOptions("-Dfabric8.deploy.createExternalUrls=true");
		return this;
	}

	public Fabric8MavenWrapper inCurrentNamespace() {
		return this.inNamespace(getCurrentNameSpace());
	}
	public Fabric8MavenWrapper inNamespace(String namespace) {
		this.maven.addCliOptions("-Dfabric8.namespace=" + namespace);
		return this;
	}

	public Fabric8MavenWrapper withRollingUpgrades(boolean rollingUpgrades) {
		this.maven.addCliOptions("-Dfabric8.rolling=" + rollingUpgrades);
		return this;
	}

	public Fabric8MavenWrapper withProfile(String profile) {
		this.maven.addCliOptions("-P" + profile);
		return this;
	}

	/**
	 * Default is
	 * ${basedir}/target/classes/META-INF/fabric8/openshift.yml
	 *
	 * @param manifestPath path to the new manifest
	 */
	public Fabric8MavenWrapper withManifestPath(String manifestPath) {
		this.maven.addCliOptions("-Dfabric8.openshiftManifest=" + manifestPath);
		return this;
	}

	public Fabric8MavenWrapper withCliOptions(String... options) {
		this.maven.addCliOptions(options);
		return this;
	}

	public Fabric8MavenWrapper withOverrideOptions(Map<String, String> values) {
		this.overrideOptions = values;
		return this;
	}

	public Fabric8MavenWrapper withCliProperty(String property, String value) {
		this.withCliOptions("-D" + property + "=" + value);
		return this;
	}

	/**
	 * Set properties for node used in tests
	 * according to documentation at
	 * https://github.com/fabric8io/kubernetes-client
	 *
	 * @return instance of maven wrapper
	 */
	public Fabric8MavenWrapper withExplicitPropertiesForClient() {
		for (val entry : FABRIC8_LOGIN_PROPERTIES.entrySet()) {
			this.withCliProperty(entry.getKey(), entry.getValue());
		}
		this.inCurrentNamespace();
		return this;
	}

	public Fabric8MavenWrapper withEnvironmentVariable(String key, String value) {
		this.maven.setEnvironmentVariable(key, value);
		return this;
	}

	/**
	 * Set system properties for node used in tests
	 * according to documentation at
	 * https://github.com/fabric8io/kubernetes-client
	 *
	 * @return instance of maven wrapper
	 */
	public Fabric8MavenWrapper withExplicitSystemVariablesForClient() {
		for (val entry : FABRIC8_LOGIN_PROPERTIES.entrySet()) {
			this.withEnvironmentVariable(formatAsEnvironment(entry.getKey()), entry.getValue());
		}
		this.inCurrentNamespace();
		return this;
	}

	private String formatAsEnvironment(String key) {
		return key.toUpperCase().replace(".", "_");
	}

	public void executeGoals(String... goals) throws VerificationException {

		log.info("f-m-p uses oc.config from tmp dir");
		String masterUrl = overrideOptions.getOrDefault(MASTER_URL, TestConfiguration.masterUrl());
		String masterUserName = overrideOptions.getOrDefault(MASTER_USER_NAME, TestConfiguration.masterUsername());
		String masterPassword = overrideOptions.getOrDefault(MASTER_PASSWORD, TestConfiguration.masterPassword());
		String masterToken = overrideOptions.getOrDefault(MASTER_TOKEN, TestConfiguration.getMasterToken());
		String ipUrl = TestConfiguration.openshiftOnline() ? masterUrl : resolveIpAddressUrl(masterUrl);
		try {
			OpenShiftBinaryClient.getInstance().login(ipUrl, masterUserName, masterPassword, masterToken);
		} catch (IOException | InterruptedException e) {
			throw new VerificationException("Unable to connect to openshift.", e);
		}

		String defaultNamespace = overrideOptions.getOrDefault(NAMESPACE, getCurrentNameSpace());
		OpenShiftBinaryClient.getInstance().executeCommandWithReturn("oc project failed!", "project", defaultNamespace);

		this.maven.executeGoals(goals);
	}

	public static String resolveIpAddressUrl(String masterUrl) {
		return resolveIpAddressUrl(masterUrl, true);
	}

	public static String resolveIpAddressUrl(String masterUrl, boolean useHttps) {
		try {

			return (useHttps ? "https://" : "") + InetAddress.getByName(new URL(masterUrl).getHost()).getHostAddress() + ":8443";
		} catch (UnknownHostException | MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	public void executeBasicBuild() throws VerificationException {
		executeGoals(true, Goal.RESOURCE, Goal.BUILD, Goal.APPLY);
	}

	public void executeGoalsWithCleanInstall(Goal... goals) throws VerificationException {
		executeGoals(true, goals);
	}

	public void executeGoals(Goal... goals) throws VerificationException {
		executeGoals(false, goals);
	}

	private void executeGoals(boolean withCleanInstall, Goal... goals) throws VerificationException {
		String[] stringGoals = new String[goals.length + (withCleanInstall ? 2 : 0)];
		int index = 0;
		if (withCleanInstall) {
			stringGoals[index++] = "clean";
			stringGoals[index++] = "install";
		}
		for (Goal goal : goals) {
			stringGoals[index++] = goal.getGoal();
		}
		log.info("Starting goals: {}", Arrays.toString(stringGoals));
		this.executeGoals(stringGoals);
		log.info("Ended goals: {}", Arrays.toString(stringGoals));
	}

	public static String createFabric8Url(String appName) {
		return createFabric8Url(appName, OpenshiftUtil.getInstance().getContext().getNamespace(), TestConfiguration.routeDomain());
	}

	public static String createFabric8Url(String appName, String namespace, String domain) {
		int limit = Integer.min(KubernetesVersion.get().serviceNameLimit(), TestConfiguration.fabric8ServiceNameLimit());
		String urlName = appName.length() > limit ? appName.substring(0, limit) : appName;
		return String.format("http://%s-%s.%s", urlName, namespace, domain);
	}

	public ModelNode getDeployment() {
		File jsonFile = projectPath.resolve(MANIFEST_PATH).toFile();
		ModelNode template = null;
		try {
			template = ModelNode.fromJSONStream(new FileInputStream(jsonFile));
			log.debug(template.toJSONString(false));
		} catch (IOException e) {
			Assert.fail("Failed to parse or access JSON: " + jsonFile.getAbsolutePath());
		}
		return template;
	}
}