package cz.xtf.builder.db;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;

/**
 * Note that to be able to use the Oracle Database Free on Openshift you need to run as user with 54321 user id.
 * This means that this class is only usable on clusters with admin privileges to be able to add securityContextConstraint
 * More info on this topic: https://github.com/oracle/docker-images/issues/228
 *
 * Also, the Oracle Database Free image doesn't provide any option to add custom database and user on setup.
 */
public class OracleFREE extends AbstractOracle {
    private static final String DB_NAME = "FREE";

    public OracleFREE() {
        super(DB_NAME);
    }

    public OracleFREE(boolean withLivenessProbe, boolean withReadinessProbe) {
        super(DB_NAME, withLivenessProbe, withReadinessProbe);
    }

    public OracleFREE(PersistentVolumeClaim pvc) {
        super(DB_NAME, pvc);
    }

    public OracleFREE(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(DB_NAME, pvc, withLivenessProbe, withReadinessProbe);
    }

    public OracleFREE(String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(DB_NAME, symbolicName, withLivenessProbe, withReadinessProbe);
    }
}
