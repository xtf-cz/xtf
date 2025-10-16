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
 * Note that MsSQL Server database needs to run with custom user id on Openshift (0 should do the trick)
 * This means that this class is only usable on clusters with admin privileges to be able to add securityContextConstraint
 *
 * Also, the MsSQL image doesn't provide any option to add custom database and user on setup.
 *
 * By using this class you are accepting the Microsoft's EULA: https://go.microsoft.com/fwlink/?linkid=857698
 */
public class MsSQL extends AbstractSQLDatabase {

    private final String ANYUID_SERVICE_ACCOUNT = "anyuid-sa";
    private final long USER_ID = 0;
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "myMsSQLPassword123";
    private static final String DB_NAME = "master";
    private static final String SYMBOLIC_NAME = "MSSQL";
    private static final String DATA_DIR = "/var/lib/mssql/data";

    public MsSQL() {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR);
    }

    public MsSQL(boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR, withLivenessProbe, withReadinessProbe);
    }

    public MsSQL(PersistentVolumeClaim pvc) {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR, pvc);
    }

    public MsSQL(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR, pvc, withLivenessProbe, withReadinessProbe);
    }

    public MsSQL(String username, String password, String dbName) {
        super(username, password, dbName, SYMBOLIC_NAME, DATA_DIR);
    }

    public MsSQL(String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, DB_NAME, symbolicName, DATA_DIR, withLivenessProbe, withReadinessProbe);
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
    public Map<String, String> getImageVariables() {
        //MsSQL server image doesn't create custom database and user on setup, thus we don't need the default ImageVariable
        Map<String, String> vars = new HashMap<>();
        vars.put("MSSQL_SA_PASSWORD", this.getPassword());
        vars.put("ACCEPT_EULA", "Y");
        return vars;
    }

    @Override
    protected ProbeSettings getProbeSettings() {
        return ProbeSettings.builder()
                .livenessInitialDelaySeconds(30)
                .livenessTcpProbe(String.valueOf(this.getPort()))
                .readinessInitialDelaySeconds(5)
                .readinessProbeCommand("/opt/mssql-tools18/bin/sqlcmd -S localhost -d " + getDbName() + " -U " + getUsername()
                        + " -P " + getPassword()
                        + " -C -Q \"Select 1\"")
                .startupInitialDelaySeconds(5)
                .startupProbeCommand("/opt/mssql-tools18/bin/sqlcmd -S localhost -d " + getDbName() + " -U " + getUsername()
                        + " -P " + getPassword()
                        + " -C -Q \"Select 1\"")
                .startupFailureThreshold(10)
                .startupPeriodSeconds(10)
                .build();
    }

    @Override
    protected String getJDBCConnectionStringPattern() {
        return "jdbc:sqlserver://%s:%s;databaseName=%s";
    }

    @Override
    public String getImageName() {
        return Image.resolve("mssql").getUrl();
    }

    @Override
    public int getPort() {
        return 1433;
    }
}
