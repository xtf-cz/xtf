package cz.xtf.builder.db;

import java.util.HashMap;
import java.util.Map;

import cz.xtf.builder.builders.pod.ContainerBuilder;
import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.openshift.api.model.SecurityContextConstraintsBuilder;

/**
 * Note that to be able to use the Oracle Database Express or Oracle Database Free on Openshift you need to run as user with
 * 54321 user id.
 * This means that this class is only usable on clusters with admin privileges to be able to add securityContextConstraint
 * More info on this topic: https://github.com/oracle/docker-images/issues/228
 *
 * Also, the Oracle Database Express and Oracle Database Free images doesn't provide any option to add custom database and user
 * on setup.
 */
public abstract class AbstractOracle extends AbstractSQLDatabase {
    protected static final String ANYUID_SERVICE_ACCOUNT = "anyuid-sa";
    protected static final Long USER_ID = 54321L;
    protected static final String USERNAME = "system";
    protected static final String PASSWORD = "oracle";
    protected static String DB_NAME;
    protected static final String SYMBOLIC_NAME = "ORACLE";
    protected static final String DATA_DIR = "/var/lib/oracle/data";

    public AbstractOracle(String dbName) {
        super(USERNAME, PASSWORD, dbName, SYMBOLIC_NAME, DATA_DIR);
    }

    public AbstractOracle(String dbName, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, dbName, SYMBOLIC_NAME, DATA_DIR, withLivenessProbe, withReadinessProbe);
    }

    public AbstractOracle(String dbName, PersistentVolumeClaim pvc) {
        super(USERNAME, PASSWORD, dbName, SYMBOLIC_NAME, DATA_DIR, pvc);
    }

    public AbstractOracle(String dbName, PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, dbName, SYMBOLIC_NAME, DATA_DIR, pvc, withLivenessProbe, withReadinessProbe);
    }

    public AbstractOracle(String dbName, String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, dbName, symbolicName, DATA_DIR, withLivenessProbe, withReadinessProbe);
    }

    @Override
    protected void configureContainer(ContainerBuilder containerBuilder) {
        super.configureContainer(containerBuilder);

        //Setting up the anyuid service account
        //Don't close the OpenShift Client instance, it could cause trouble with pod shell access
        OpenShift master = OpenShifts.master();
        if (master.getServiceAccount(ANYUID_SERVICE_ACCOUNT) == null) {
            master.createServiceAccount(
                    new ServiceAccountBuilder().withNewMetadata().withName(ANYUID_SERVICE_ACCOUNT).endMetadata().build());
        }
        OpenShift admin = OpenShifts.admin();
        admin.securityContextConstraints().withName("anyuid")
                .edit(new Visitor<SecurityContextConstraintsBuilder>() {
                    @Override
                    public void visit(SecurityContextConstraintsBuilder builder) {
                        builder.addToUsers(
                                "system:serviceaccount:" + OpenShiftConfig.namespace() + ":" + ANYUID_SERVICE_ACCOUNT);
                    }
                });

        containerBuilder.pod().addServiceAccount(ANYUID_SERVICE_ACCOUNT);
        containerBuilder.pod().addRunAsUserSecurityContext(USER_ID);
    }

    @Override
    protected ProbeSettings getProbeSettings() {
        return ProbeSettings.builder()
                .livenessInitialDelaySeconds(30)
                .livenessTcpProbe(String.valueOf(this.getPort()))
                .readinessInitialDelaySeconds(5)
                .readinessProbeCommand("/opt/oracle/checkDBStatus.sh")
                .startupInitialDelaySeconds(5)
                .startupProbeCommand("/opt/oracle/checkDBStatus.sh")
                .startupFailureThreshold(10)
                .startupPeriodSeconds(10)
                .build();
    }

    @Override
    protected String getJDBCConnectionStringPattern() {
        return "jdbc:oracle:thin:@%s:%s:%s";
    }

    @Override
    public String getImageName() {
        return Image.resolve("oracle").getUrl();
    }

    @Override
    public Map<String, String> getImageVariables() {
        //Oracle database image doesn't create custom database and user on setup, thus we don't need the default ImageVariable
        Map<String, String> vars = new HashMap<>();
        vars.put("ORACLE_PWD", getPassword());
        return vars;
    }

    @Override
    public int getPort() {
        return 1521;
    }
}
