package cz.xtf.builder.db;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.builder.builders.DeploymentConfigBuilder;
import cz.xtf.builder.builders.EnvironmentConfiguration;
import cz.xtf.builder.builders.ServiceBuilder;
import cz.xtf.builder.builders.pod.ContainerBuilder;
import cz.xtf.builder.builders.pod.PersistentVolumeClaim;

public abstract class AbstractDatabase extends DefaultStatefulAuxiliary {
    private static final String DEFAULT_USERNAME = "testuser";
    private static final String DEFAULT_PASSWORD = "testpwd";
    private static final String DEFAULT_DATABASE_NAME = "testdb";
    protected final String username;
    protected final String password;
    protected final String dbName;
    protected final String symbolicName;
    protected String openShiftName;
    protected String jndiName = null;
    protected boolean isObjectStore = false;
    protected boolean configureEnvironment = true;
    protected boolean external = false;
    protected boolean nonXaDatasource = false;

    protected boolean withLivenessProbe;
    protected boolean withReadinessProbe;
    protected boolean withStartupProbe;

    private String deploymentConfigName;
    private String envVarPrefix;

    public void setDeploymentConfigName(String deploymentConfigName) {
        this.deploymentConfigName = deploymentConfigName;
    }

    public void setEnvVarPrefix(String envVarPrefix) {
        this.envVarPrefix = envVarPrefix;
    }

    public AbstractDatabase(
            String symbolicName,
            String dataDir,
            PersistentVolumeClaim pvc,
            String username,
            String password,
            String dbName,
            boolean configureEnvironment,
            boolean withLivenessProbe,
            boolean withReadinessProbe,
            boolean withStartupProbe,
            String deploymentConfigName,
            String envVarPrefix) {
        super(symbolicName, dataDir, pvc);
        this.symbolicName = symbolicName;
        this.username = (username == null || username.isEmpty()) ? DEFAULT_USERNAME : username;
        this.password = (password == null || password.isEmpty()) ? DEFAULT_PASSWORD : password;
        this.dbName = (dbName == null || dbName.isEmpty()) ? DEFAULT_DATABASE_NAME : dbName;
        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
        this.withStartupProbe = withStartupProbe;
        this.configureEnvironment = configureEnvironment;
        this.deploymentConfigName = deploymentConfigName;
        this.envVarPrefix = envVarPrefix;
    }

    public AbstractDatabase(String symbolicName, String dataDir) {
        this(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_DATABASE_NAME, symbolicName, dataDir);
    }

    public AbstractDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe) {
        this(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_DATABASE_NAME, symbolicName, dataDir);

        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
    }

    public AbstractDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe,
            boolean configureEnvironment) {
        this(symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
        this.configureEnvironment = configureEnvironment;
    }

    public AbstractDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe,
            boolean withStartupProbe,
            boolean configureEnvironment) {
        this(symbolicName, dataDir);
        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
        this.withStartupProbe = withStartupProbe;
        this.configureEnvironment = configureEnvironment;
    }

    public AbstractDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc) {
        this(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_DATABASE_NAME, symbolicName, dataDir, pvc);
    }

    public AbstractDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc, boolean withLivenessProbe,
            boolean withReadinessProbe) {
        this(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_DATABASE_NAME, symbolicName, dataDir, pvc, withLivenessProbe,
                withReadinessProbe);
    }

    public AbstractDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            PersistentVolumeClaim pvc, boolean withLivenessProbe,
            boolean withReadinessProbe) {
        this(username, password, dbName, symbolicName, dataDir, pvc);

        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
    }

    public AbstractDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc, boolean withLivenessProbe,
            boolean withReadinessProbe, boolean withStartupProbe) {
        this(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_DATABASE_NAME, symbolicName, dataDir, pvc);

        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
        this.withStartupProbe = withStartupProbe;
    }

    public AbstractDatabase(String username, String password, String dbName, String symbolicName, String dataDir) {
        this(username, password, dbName, symbolicName, dataDir, null);
    }

    public AbstractDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            PersistentVolumeClaim pvc) {
        super(symbolicName, dataDir, pvc);
        this.username = username;
        this.password = password;
        this.dbName = dbName;
        this.symbolicName = symbolicName;
    }

    public AbstractDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            boolean withLivenessProbe, boolean withReadinessProbe) {
        this(username, password, dbName, symbolicName, dataDir);
        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
    }

    public AbstractDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            boolean withLivenessProbe, boolean withReadinessProbe, boolean configureEnvironment) {
        this(username, password, dbName, symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
        this.configureEnvironment = configureEnvironment;
    }

    public AbstractDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            boolean withLivenessProbe, boolean withReadinessProbe, boolean withStartupProbe, boolean configureEnvironment) {
        this(username, password, dbName, symbolicName, dataDir);
        this.configureEnvironment = configureEnvironment;
        this.withLivenessProbe = withLivenessProbe;
        this.withReadinessProbe = withReadinessProbe;
        this.withStartupProbe = withStartupProbe;
    }

    public abstract String getImageName();

    public abstract int getPort();

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getDbName() {
        return dbName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

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

    public List<String> getImageArgs() {
        return Collections.emptyList();
    }

    public String getServiceAccount() {
        return null;
    }

    public String getDeploymentConfigName() {
        if (deploymentConfigName != null) {
            return deploymentConfigName;
        }
        if (openShiftName == null) {
            openShiftName = dbName.toLowerCase() + "-" + getSymbolicName().toLowerCase();
        }
        return openShiftName;
    }

    public String getEnvVarPrefix() {
        if (envVarPrefix != null) {
            return envVarPrefix;
        }
        return dbName.toUpperCase() + "_" + getSymbolicName().toUpperCase();
    }

    private String getEnvVarName(final String paramName) {
        return getEnvVarPrefix() + "_" + paramName.toUpperCase();
    }

    @Override
    public void configureApplicationDeployment(DeploymentConfigBuilder dcBuilder) {
        configureEnvironment(dcBuilder.podTemplate().container());
    }

    public void configureService(ApplicationBuilder appBuilder) {
        final ServiceBuilder serviceBuilder = appBuilder.service(getDeploymentConfigName()).port(getPort())
                .addContainerSelector("deploymentconfig", getDeploymentConfigName());
        if (external) {
            serviceBuilder.withoutSelectors();
        }
    }

    @Override
    public DeploymentConfigBuilder configureDeployment(ApplicationBuilder appBuilder) {
        return configureDeployment(appBuilder, true);
    }

    public DeploymentConfigBuilder configureDeployment(ApplicationBuilder appBuilder, boolean synchronous) {
        final DeploymentConfigBuilder builder = appBuilder.deploymentConfig(getDeploymentConfigName());
        ContainerBuilder containerBuilder = builder.podTemplate().container().fromImage(getImageName())
                .envVars(getImageVariables())
                .port(getPort());
        if (getImageArgs() != null) {
            getImageArgs().forEach(containerBuilder::args);
        }
        if (synchronous) {
            builder.onConfigurationChange();
            builder.synchronousDeployment();
        }
        if (getServiceAccount() != null) {
            builder.podTemplate().addServiceAccount(getServiceAccount());
        }

        configureContainer(builder.podTemplate().container());

        if (isStateful) {
            storagePartition.configureApplicationDeployment(appBuilder, builder);
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

    public void configureEnvironment(EnvironmentConfiguration envConfig) {
        String dbServiceMapping;
        dbServiceMapping = envConfig.getConfigEntries().getOrDefault("DB_SERVICE_PREFIX_MAPPING", "");
        if (dbServiceMapping.length() != 0) {
            dbServiceMapping = dbServiceMapping.concat(",");
        }
        envConfig
                .configEntry("DB_SERVICE_PREFIX_MAPPING",
                        dbServiceMapping.concat(getDeploymentConfigName() + "=" + getEnvVarPrefix()))
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

    public AbstractDatabase asObjectStore() {
        isObjectStore = true;
        return this;
    }

    public boolean isObjectStore() {
        return isObjectStore;
    }

    public AbstractDatabase external() {
        external = true;
        return this;
    }

    public AbstractDatabase withProbes() {
        withLivenessProbe = true;
        withReadinessProbe = true;
        withStartupProbe = true;
        return this;
    }

    public AbstractDatabase nonXaDatasource() {
        nonXaDatasource = true;
        return this;
    }

    @Override
    public String toString() {
        return "AbstractDatabase{" + "dbName='" + dbName + '\'' + '}';
    }
}
