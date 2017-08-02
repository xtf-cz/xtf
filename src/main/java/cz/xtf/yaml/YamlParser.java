package cz.xtf.yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import cz.xtf.TestParent;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.db.AbstractDatabase;
import cz.xtf.openshift.db.MongoDB;
import cz.xtf.openshift.db.MySQL;
import cz.xtf.openshift.db.PostgreSQL;

public class YamlParser {
	private Map<String, Object> appData;

	private final String appName;
	private final String image;
	private final String testSuite;
	private final String buildStrategy;
	private final String projectName;

	private final Path appDirectory;

	private final int replicas;

	private final Map<String, String> enviroments;
	private final Map<String, List<String>> waitRules;

	private final List<String> services;
	private final List<String> routes;
	private final List<AbstractDatabase> databases;

	public YamlParser(String yamlApp) throws Exception {
		this(yamlApp, null);
	}

	@SuppressWarnings("unchecked")
	public YamlParser(String yamlApp, String appName) throws Exception {
		InputStream is = new FileInputStream(new File(yamlApp));
		appData = (HashMap<String, Object>) new Yaml().load(is);
		is.close();

		this.appName = initAppName(appName);
		this.image = initImage();
		this.testSuite = initTestSuite();
		this.buildStrategy = initBuildStrategy();
		this.projectName = initProject();

		this.appDirectory = initAppDirectory();

		this.replicas = initReplicas();

		this.enviroments = initEnviroments();
		this.waitRules = initWaitRules();

		this.services = initServices();
		this.routes = initRoutes();
		this.databases = initDatabases();

		replaceDynamic();
	}

	private String initAppName(String appName) {
		return appName != null ? appName : appData.get("name").toString();
	}

	private String initImage() throws Exception {
		String image = appData.get("image").toString();
		if (image == null)
			throw new Exception(
					"No image defined! Define image to use, e.g.:\nimage: eap");
		return ImageRegistry.get().getClass().getDeclaredMethod(image)
				.invoke(ImageRegistry.get()).toString();
	}

	private String initTestSuite() throws Exception {
		if (appData.get("testSuite") == null)
			throw new Exception(
					"No testSuite definded! Define testSuite, e.g.:\ntestSuite: ews");
		else
			return appData.get("testSuite").toString();
	}

	private String initBuildStrategy() {
		return appData.get("build") == null ? "none" : appData.get("build")
				.toString();
	}

	private String initProject() {
		return appData.get("project") == null ? null : appData.get("project")
				.toString();
	}

	private Path initAppDirectory() {
		return projectName == null ? null : TestParent.findApplicationDirectory(
				testSuite, projectName);
	}

	private int initReplicas() {
		return appData.get("replicas") != null ? (Integer) appData
				.get("replicas") : 1;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> initEnviroments() {
		if (appData.get("envs") == null)
			return new HashMap<String, String>();
		else
			return (HashMap<String, String>) appData.get("envs");
	}

	@SuppressWarnings("unchecked")
	private Map<String, List<String>> initWaitRules() {
		if (appData.get("waitFor") == null)
			return new HashMap<String, List<String>>();
		else
			return (HashMap<String, List<String>>) appData.get("waitFor");
	}

	@SuppressWarnings("unchecked")
	private List<String> initServices() {
		if (appData.get("services") == null)
			return new ArrayList<String>();
		else
			return (ArrayList<String>) appData.get("services");
	}

	@SuppressWarnings("unchecked")
	private List<String> initRoutes() {
		if (appData.get("routes") == null)
			return new ArrayList<String>();
		else
			return (ArrayList<String>) appData.get("routes");
	}

	@SuppressWarnings("unchecked")
	private List<AbstractDatabase> initDatabases() throws Exception {
		if (appData.get("databases") == null)
			return null;

		ArrayList<AbstractDatabase> dbs = new ArrayList<>();
		for (String db : (ArrayList<String>) appData.get("databases")) {
			switch (db) {
			case "PostgreSQL":
				dbs.add(new PostgreSQL());
				break;
			case "MySQL":
				dbs.add(new MySQL());
				break;
			case "MongoDB":
				dbs.add(new MongoDB());
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported database: " + db);
			}
		}

		return dbs;
	}

	private void replaceDynamic() {
		enviroments.forEach((k, v) -> {
			if (v.equals("namespace"))
				enviroments.put(k, OpenshiftUtil.getInstance().getContext()
						.getNamespace());
		});
	}

	String getAppName() {
		return appName;
	}

	String getImage() {
		return image;
	}

	String getTestSuite() {
		return testSuite;
	}

	String getBuildStrategy() {
		return buildStrategy;
	}

	String getProject() {
		return projectName;
	}

	Path getAppDirectory() {
		return appDirectory;
	}
	
	public int getReplicas() {
		return replicas;
	}

	Map<String, String> getEnviroments() {
		return enviroments;
	}

	Map<String, List<String>> getWaitRules() {
		return waitRules;
	}

	List<String> getServices() {
		return services;
	}

	List<String> getRoutes() {
		return routes;
	}

	List<AbstractDatabase> getDatabases() {
		return databases;
	}
}
