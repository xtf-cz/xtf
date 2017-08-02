package cz.xtf.yaml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestParent;
import cz.xtf.docker.OpenShiftNode;
import cz.xtf.git.GitProject;
import cz.xtf.http.HttpClient;
import cz.xtf.openshift.OpenshiftApplication;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.RouteBuilder;
import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.db.AbstractDatabase;

import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

public class YamlDeployer {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(YamlDeployer.class);
	private static final OpenshiftUtil OPENSHIFT = OpenshiftUtil.getInstance();
	
	private static final boolean RECREATE_BY_DEFAULT = false;
	
	private YamlParser config;

	private ApplicationBuilder appBuilder;
	private ContainerBuilder containerBuilder;

	private final String appName;
	private final String image;
	private final String testSuite;

	public YamlDeployer(String fileLocation) throws Exception {
		this(fileLocation, null, RECREATE_BY_DEFAULT);
	}

	public YamlDeployer(String fileLocation, boolean recreateProject)
			throws Exception {
		this(fileLocation, null, recreateProject);
	}

	public YamlDeployer(String fileLocation, String appName) throws Exception {
		this(fileLocation, appName, RECREATE_BY_DEFAULT);
	}

	public YamlDeployer(String fileLocation, String appName,
			boolean recreateProject) throws Exception {
		OPENSHIFT.createProject(OPENSHIFT.getContext().getNamespace(),
				recreateProject);

		config = new YamlParser(fileLocation, appName);

		this.appName = config.getAppName();
		this.image = config.getImage();
		this.testSuite = config.getTestSuite();

		setup();
	}

	private void setup() throws Exception {
		switch (config.getBuildStrategy()) {
		case "none":
			setupPureDeployment();
			break;
		case "S2I":
			setupS2I();
			break;
		case "Docker":
			setupDocker();
			break;
		default:
			throw new UnsupportedOperationException("Unknown build strategy: "
					+ config.getBuildStrategy());
		}
	}

	private void setupPureDeployment() throws Exception {
		LOGGER.info("Setting up pure deployment...");

		appBuilder = new ApplicationBuilder(appName);
		appBuilder.imageStream().fromExternalImage(image)
				.addLabel("application", appName);

		setCommon();

		containerBuilder.fromImage(image);
	}

	private void setupS2I() throws Exception {
		LOGGER.info("Running S2I setup...");

		String projectPath = config.getProject();

		TestParent.gitlab().deleteProject(projectPath);
		GitProject project = TestParent.gitlab().createProjectFromPath(
				projectPath, config.getAppDirectory());

		appBuilder = new ApplicationBuilder(appName, project.getHttpUrl(),
				image);

		setCommon();
	}

	private void setupDocker() {
		throw new UnsupportedOperationException(
				"Docker build strategy not yet implemented by Yaml deployer");
	}

	private void setCommon() throws Exception {
		containerBuilder = appBuilder.deploymentConfig(appName, appName, false)
				.onConfigurationChange()
				.addLabel("application", appName)
				.setReplicas(config.getReplicas()).podTemplate()
				.addLabel("application", appName).container(appName);

		List<AbstractDatabase> databases = config.getDatabases();
		if (databases != null && databases.size() > 0)
			appBuilder.addDatabases(databases
					.toArray(new AbstractDatabase[databases.size()]));

		config.getEnviroments()
				.forEach((k, v) -> containerBuilder.envVar(k, v));

		setServices();
		setRoutes();
	}

	private void setServices() throws Exception {
		Class<?> productService = YamlDeployer.class.getClassLoader()
				.loadClass(
						"cz.xtf.openshift.xtfservices."
								+ testSuite.toUpperCase() + "Services");

		for (String s : config.getServices()) {
			productService.getDeclaredMethod("add" + s + "Service",
					ApplicationBuilder.class, String.class).invoke(
					productService, appBuilder, appName);
		}
	}

	private void setRoutes() throws Exception {
		Class<?> productRoute = YamlDeployer.class.getClassLoader().loadClass(
				"cz.xtf.openshift.xtfservices."
						+ testSuite.toUpperCase() + "Services");

		for (String r : config.getRoutes()) {
			productRoute.getDeclaredMethod("add" + r + "Route",
					ApplicationBuilder.class, String.class).invoke(
					productRoute, appBuilder, appName);
		}
	}

	public void build() {
		OpenshiftApplication app = new OpenshiftApplication(appBuilder);
		app.triggerManualBuild();
	}

	public YamlDeployer deploy() {
		OpenshiftApplication app = new OpenshiftApplication(appBuilder);

		if (config.getBuildStrategy().equals("none"))
			app.deployWithoutBuild();
		else
			app.deploy();

		return this;
	}

	public void delete() {
		OpenShiftNode
				.master()
				.executeCommand(
						String.format(
								"sudo oc -n %s delete dc,svc,routes,is -l application=%s",
								OPENSHIFT.getContext().getNamespace(), appName));
	}

	public void waitForDeploymentReady() throws Exception {
		for (Entry<String, List<String>> entry : config.getWaitRules()
				.entrySet()) {
			waitTillReady(entry.getKey(), entry.getValue());
		}
	}

	private void waitTillReady(String waitCondition, List<String> addresses)
			throws Exception {
		for (String address : addresses) {
			String waitAddress = address.replace("defaultRoute",
					RouteBuilder.createHostName(appName));

			switch (waitCondition) {
			case "httpOK":
				LOGGER.debug("Waiting for HTTP_OK: {}", waitAddress);
				HttpClient.get(waitAddress).waitForOk(3, TimeUnit.MINUTES);
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported wait condition: " + waitCondition);
			}
		}
	}

	public ApplicationBuilder getAppBuilder() {
		return appBuilder;
	}

	public ContainerBuilder getContainerBuilder() {
		return containerBuilder;
	}

	public YamlParser getConfig() {
		return config;
	}
}
