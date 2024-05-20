package cz.xtf.builder.db;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;

/**
 * Note that to be able to use the Oracle Database Express on Openshift you need to run as user with 54321 user id.
 * This means that this class is only usable on clusters with admin privileges to be able to add securityContextConstraint
 * More info on this topic: https://github.com/oracle/docker-images/issues/228
 *
 * Also, the Oracle Database Express image doesn't provide any option to add custom database and user on setup.
 */
public class OracleXE extends AbstractOracle {
    private static final String DB_NAME = "XE";

    public OracleXE() {
        super(DB_NAME);
    }

    public OracleXE(boolean withLivenessProbe, boolean withReadinessProbe) {
        super(DB_NAME, withLivenessProbe, withReadinessProbe);
    }

    public OracleXE(PersistentVolumeClaim pvc) {
        super(DB_NAME, pvc);
    }

    public OracleXE(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(DB_NAME, pvc, withLivenessProbe, withReadinessProbe);
    }

    public OracleXE(String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(DB_NAME, symbolicName, withLivenessProbe, withReadinessProbe);
    }
}
