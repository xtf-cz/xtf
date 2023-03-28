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
 * Note that to be able to use the oracleXE on Openshift you need to run as user with 54321 user id.
 * This means that this class is only usable on clusters with admin privileges to be able to add securityContextConstraint
 * More info on this topic: https://github.com/oracle/docker-images/issues/228
 *
 * Also, the OracleXE image doesn't provide any option to add custom database and user on setup.
 */
public class OracleXE extends AbstractSQLDatabase {
    private static final String ANYUID_SERVICE_ACCOUNT = "anyuid-sa";
    private static final Long USER_ID = 54321L;
    private static final String USERNAME = "system";
    private static final String PASSWORD = "oracle";
    private static final String DB_NAME = "XE";
    private static final String SYMBOLIC_NAME = "ORACLE";
    private static final String DATA_DIR = "/var/lib/oracle/data";

    public OracleXE() {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR);
    }

    public OracleXE(boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR, withLivenessProbe, withReadinessProbe);
    }

    public OracleXE(PersistentVolumeClaim pvc) {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR, pvc);
    }

    public OracleXE(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(USERNAME, PASSWORD, DB_NAME, SYMBOLIC_NAME, DATA_DIR, pvc, withLivenessProbe, withReadinessProbe);
    }

    public OracleXE(String username, String password, String dbName) {
        super(username, password, dbName, SYMBOLIC_NAME, DATA_DIR);
    }

    public OracleXE(String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
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
    protected ProbeSettings getProbeSettings() {
        return new ProbeSettings(30,
                String.valueOf(this.getPort()),
                5,
                "/opt/oracle/checkDBStatus.sh",
                5,
                "/opt/oracle/checkDBStatus.sh",
                10,
                10);
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
        //OracleXE database image doesn't create custom database and user on setup, thus we don't need the default ImageVariable
        Map<String, String> vars = new HashMap<>();
        vars.put("ORACLE_PWD", getPassword());
        return vars;
    }

    @Override
    public int getPort() {
        return 1521;
    }
}
