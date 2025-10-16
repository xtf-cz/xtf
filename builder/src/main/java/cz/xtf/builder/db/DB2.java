package cz.xtf.builder.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.core.image.Image;

public class DB2 extends AbstractSQLDatabase {
    private static final String DEFAULT_SYMBOLIC_NAME = "DB2";

    // data directory for the Official Docker DB2 image
    private static final String DEFAULT_DATA_DIR = "/database";

    // env variables names for the Official Docker DB2 image
    private static final String DEFAULT_DB2_USER_ENV_VAR = "DB2INSTANCE";
    private static final String DEFAULT_DB2_PASS_ENV_VAR = "DB2INST1_PASSWORD";
    private static final String DEFAULT_DB2_DATABASE_ENV_VAR = "DBNAME";

    private Map<String, String> vars;
    private List<String> args;
    private String serviceAccount;

    public DB2(Builder builder) {
        super(
                builder.symbolicName,
                builder.dataDir,
                builder.pvc,
                builder.username,
                builder.password,
                builder.dbName,
                builder.configureEnvironment,
                builder.withLivenessProbe,
                builder.withReadinessProbe,
                builder.withStartupProbe,
                builder.deploymentConfigName,
                builder.envVarPrefix);
        this.vars = builder.vars;
        this.args = builder.args;
        this.serviceAccount = builder.serviceAccount;
    }

    @Override
    public String getImageName() {
        return Image.resolve("db2").getUrl();
    }

    @Override
    public int getPort() {
        return 50000;
    }

    protected ProbeSettings getProbeSettings() {
        return ProbeSettings.builder()
                .livenessInitialDelaySeconds(120)
                .livenessTcpProbe(String.valueOf(getPort()))
                .readinessInitialDelaySeconds(30)
                .readinessProbeCommand(
                        String.format("su - %s -c 'db2 -x \"connect to %s\" && db2 -x \"select 1 from sysibm.sysdummy1\"'",
                                getUsername(), getDbName()))
                .readinessTimeoutSeconds(5)
                .readinessPeriodSeconds(10)
                .readinessFailureThreshold(10)
                .startupInitialDelaySeconds(120)
                .startupProbeCommand(String.format("su - %s -c 'db2 -x connect to %s'",
                        getUsername(), getDbName()))
                .startupFailureThreshold(10)
                .startupPeriodSeconds(10)
                .build();
    }

    @Override
    public Map<String, String> getImageVariables() {
        Map<String, String> vars;
        vars = new HashMap<>();
        vars.put("LICENSE", "accept"); // Required for DB2 container to start
        vars.put(DEFAULT_DB2_DATABASE_ENV_VAR, getDbName());
        vars.put(DEFAULT_DB2_USER_ENV_VAR, getUsername());
        vars.put(DEFAULT_DB2_PASS_ENV_VAR, getPassword());
        vars.put("PERSISTENT_HOME", "false");
        vars.putAll(this.vars);
        return vars;
    }

    @Override
    public String toString() {
        return "DB2";
    }

    @Override
    protected String getJDBCConnectionStringPattern() {
        return "jdbc:db2://%s:%s/%s";
    }

    @Override
    public List<String> getImageArgs() {
        return args;
    }

    @Override
    public String getServiceAccount() {
        return serviceAccount;
    }

    @Override
    boolean requiresPrivileged() {
        return true;
    }

    public static class Builder {
        private String symbolicName = DEFAULT_SYMBOLIC_NAME;
        private String dataDir = DEFAULT_DATA_DIR;
        private PersistentVolumeClaim pvc;
        private String username;
        private String password;
        private String dbName;
        private boolean configureEnvironment = true;
        private boolean withLivenessProbe;
        private boolean withReadinessProbe;
        private boolean withStartupProbe;
        private Map<String, String> vars = new HashMap<>();
        private List<String> args;
        private String deploymentConfigName;
        private String envVarPrefix;
        private String serviceAccount;

        public Builder withArgs(List<String> args) {
            this.args = args;
            return this;
        }

        public Builder withConfigureEnvironment(boolean configureEnvironment) {
            this.configureEnvironment = configureEnvironment;
            return this;
        }

        public Builder withDataDir(String dataDir) {
            this.dataDir = dataDir;
            return this;
        }

        public Builder withDbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        public Builder withDeploymentConfigName(String deploymentConfigName) {
            this.deploymentConfigName = deploymentConfigName;
            return this;
        }

        public Builder withEnvVarPrefix(String envVarPrefix) {
            this.envVarPrefix = envVarPrefix;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder withPvc(PersistentVolumeClaim pvc) {
            this.pvc = pvc;
            return this;
        }

        public Builder withSymbolicName(String symbolicName) {
            this.symbolicName = symbolicName;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withVars(Map<String, String> vars) {
            this.vars = vars;
            return this;
        }

        public Builder withWithLivenessProbe(boolean withLivenessProbe) {
            this.withLivenessProbe = withLivenessProbe;
            return this;
        }

        public Builder withWithReadinessProbe(boolean withReadinessProbe) {
            this.withReadinessProbe = withReadinessProbe;
            return this;
        }

        public Builder withWithStartupProbe(boolean withStartupProbe) {
            this.withStartupProbe = withStartupProbe;
            return this;
        }

        public Builder withServiceAccount(String serviceAccount) {
            this.serviceAccount = serviceAccount;
            return this;
        }

        public DB2 build() {
            return new DB2(this);
        }
    }
}
