package cz.xtf.openshift.db;

import cz.xtf.docker.DockerContainer;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import cz.xtf.openshift.builder.EnvironmentConfiguration;
import cz.xtf.openshift.builder.ServiceBuilder;
import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;
import cz.xtf.openshift.storage.DefaultStatefulAuxiliary;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Pod;

public abstract class AbstractDatabase extends DefaultStatefulAuxiliary {

	private final String username;
	private final String password;
	private final String dbName;
	private final String symbolicName;
	private String openShiftName;
	private boolean isObjectStore = false;
	private boolean configureEnvironment = true;
	private String jndiName = null;
	private boolean external = false;
	private boolean nonXaDatasource = false;

	protected boolean withLivenessProbe;
	protected boolean withReadinessProbe;

	public AbstractDatabase(final String symbolicName, final String dataDir) {
		this("testuser", "testpwd", "testdb", symbolicName, dataDir);
	}

	public AbstractDatabase(final String symbolicName, final String dataDir, boolean withLivenessProbe, boolean withReadinessProbe) {
		this("testuser", "testpwd", "testdb", symbolicName, dataDir);

		this.withLivenessProbe = withLivenessProbe;
		this.withReadinessProbe = withReadinessProbe;
	}

	public AbstractDatabase(final String symbolicName, final String dataDir, boolean withLivenessProbe, boolean withReadinessProbe,
							boolean configureEnvironment) {
		this(symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
		this.configureEnvironment = configureEnvironment;
	}


	public AbstractDatabase(final String symbolicName, final String dataDir, final PersistentVolumeClaim pvc) {
		this("testuser", "testpwd", "testdb", symbolicName, dataDir, pvc);
	}

	public AbstractDatabase(final String symbolicName, final String dataDir, final PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
		this("testuser", "testpwd", "testdb", symbolicName, dataDir, pvc);

		this.withLivenessProbe = withLivenessProbe;
		this.withReadinessProbe = withReadinessProbe;
	}

	public AbstractDatabase(final String username, final String password,
							final String dbName, final String symbolicName, final String dataDir) {
		this(username, password, dbName, symbolicName, dataDir, null);
	}

	public AbstractDatabase(final String username, final String password,
							final String dbName, final String symbolicName, final String dataDir, final PersistentVolumeClaim pvc) {
		super(symbolicName, dataDir, pvc);
		this.username = username;
		this.password = password;
		this.dbName = dbName;
		this.symbolicName = symbolicName;
	}

	public AbstractDatabase(final String username, final String password,
							final String dbName, final String symbolicName, final String dataDir,
							final boolean withLivenessProbe, final boolean withReadinessProbe) {
		this(username, password, dbName, symbolicName, dataDir);
		this.withLivenessProbe = withLivenessProbe;
		this.withReadinessProbe = withReadinessProbe;
	}

	public AbstractDatabase(final String username, final String password,
							final String dbName, final String symbolicName, final String dataDir,
							final boolean withLivenessProbe, final boolean withReadinessProbe,
							final boolean configureEnvironment) {
		this(username, password, dbName, symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
		this.configureEnvironment = configureEnvironment;
	}


	public String getSymbolicName() {
		return symbolicName;
	}

	public abstract String getImageName();

	public abstract int getPort();

	protected void configureContainer(ContainerBuilder containerBuilder) {
	}

	public Map<String, String> getImageVariables() {
		final Map<String, String> vars = new HashMap<>();
		vars.put(getSymbolicName() + "_USER", getUsername());
		vars.put(getSymbolicName() + "_USERNAME", getUsername());
		vars.put(getSymbolicName() + "_PASSWORD", getPassword());
		vars.put(getSymbolicName() + "_DATABASE", getDbName());
		return vars;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getDbName() {
		return dbName;
	}

	public String getOpenShiftName() {
		if (openShiftName == null) {
			openShiftName = dbName + "-" + getSymbolicName().toLowerCase();
		}
		return openShiftName;
	}

	public String getEnvVarPrefix() {
		return dbName.toUpperCase() + "_" + getSymbolicName().toUpperCase();
	}

	private String getEnvVarName(final String paramName) {
		return getEnvVarPrefix() + "_" + paramName.toUpperCase();
	}

	@Override
	public Pod getPod() {
		return OpenshiftUtil.getInstance().findNamedPod(getOpenShiftName());
	}

	@Override
	public void configureApplicationDeployment(final DeploymentConfigBuilder dcBuilder) {
		configureEnvironment(dcBuilder.podTemplate().container());
	}

	public void configureService(ApplicationBuilder appBuilder) {
		final ServiceBuilder serviceBuilder = appBuilder.service(getOpenShiftName()).setPort(getPort())
				.setContainerPort(getPort())
				.addContainerSelector("name", getOpenShiftName()).addLabel("application", appBuilder.getName());
		if (external) {
			serviceBuilder.withoutSelectors();
		}
	}

	@Override
	public DeploymentConfigBuilder configureDeployment(final ApplicationBuilder appBuilder) {
		return configureDeployment(appBuilder, true);
	}

	public DeploymentConfigBuilder configureDeployment(final ApplicationBuilder appBuilder, final boolean synchronous) {
		final DeploymentConfigBuilder builder = appBuilder.deploymentConfig(
				getOpenShiftName(), getOpenShiftName(), false).addLabel("application", appBuilder.getName());
		builder.podTemplate().container().fromImage(getImageName())
				.envVars(getImageVariables()).port(getPort());
		if (synchronous) {
			builder.onConfigurationChange();
			builder.synchronousDeployment();
		}

		configureContainer(builder.podTemplate().container());

		if (isStateful) {
			storagePartition.configureApplicationDeployment(builder);
		}
		if (this.persistentVolClaim != null) {
			builder.podTemplate().addPersistenVolumeClaim(
					this.persistentVolClaim.getName(),
					this.persistentVolClaim.getClaimName());
			builder.podTemplate().container().addVolumeMount(this.persistentVolClaim.getName(), dataDir, false);
		}

		configureService(appBuilder);

		return builder;
	}

	public void configureEnvironment(final EnvironmentConfiguration envConfig) {
		String dbServiceMapping;
		dbServiceMapping = envConfig.getConfigEntries()
				.getOrDefault("DB_SERVICE_PREFIX_MAPPING", "");
		if (dbServiceMapping.length() != 0) {
			dbServiceMapping = dbServiceMapping.concat(",");
		}
		envConfig
				.configEntry("DB_SERVICE_PREFIX_MAPPING",
						dbServiceMapping.concat(getOpenShiftName() + "="
								+ getEnvVarPrefix()))
				.configEntry(getEnvVarName("USERNAME"), getUsername())
				.configEntry(getEnvVarName("PASSWORD"), getPassword())
				.configEntry(getEnvVarName("DATABASE"), getDbName());
		if (isObjectStore) {
			envConfig.configEntry("TX_DATABASE_PREFIX_MAPPING", getEnvVarPrefix());
		}
		if (jndiName != null) {
			envConfig.configEntry(getEnvVarName("JNDI"), jndiName);
		}
		if (nonXaDatasource) {
			envConfig.configEntry(getEnvVarPrefix() + "_NONXA", "true");
		}
	}

	public AbstractDatabase jndiName(final String jndiName) {
		this.jndiName = jndiName;
		return this;
	}

	@Override
	public DockerContainer getContainer() {
		return DockerContainer.createForPod(getPod(), getOpenShiftName());
	}

	public AbstractDatabase asObjectStore() {
		isObjectStore = true;
		return this;
	}

	public AbstractDatabase external() {
		external = true;
		return this;
	}

	public AbstractDatabase withProbes() {
		withLivenessProbe = true;
		withReadinessProbe = true;
		return this;
	}

	public AbstractDatabase nonXaDatasource() {
		nonXaDatasource = true;
		return this;
	}

	@Override
	public String toString() {
		return "AbstractDatabase{" +
				"dbName='" + dbName + '\'' +
				'}';
	}
}
