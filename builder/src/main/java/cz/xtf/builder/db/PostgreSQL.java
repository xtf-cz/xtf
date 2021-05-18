package cz.xtf.builder.db;

import java.util.Map;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.core.image.Image;

public class PostgreSQL extends AbstractSQLDatabase {

    public PostgreSQL() {
        super("POSTGRESQL", "/var/lib/pgsql/data");
    }

    public PostgreSQL(boolean withLivenessProbe, boolean withReadinessProbe) {
        super("POSTGRESQL", "/var/lib/pgsql/data", withLivenessProbe, withReadinessProbe);
    }

    public PostgreSQL(PersistentVolumeClaim pvc) {
        super("POSTGRESQL", "/var/lib/pgsql/data", pvc);
    }

    public PostgreSQL(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super("POSTGRESQL", "/var/lib/pgsql/data", pvc, withLivenessProbe, withReadinessProbe);
    }

    public PostgreSQL(String username, String password, String dbName) {
        super(username, password, dbName, "POSTGRESQL", "/var/lib/pgsql/data");
    }

    public PostgreSQL(String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(symbolicName, "/var/lib/pgsql/data", withLivenessProbe, withReadinessProbe);
    }

    @Override
    public String getImageName() {
        return Image.resolve("postgresql").getUrl();
    }

    @Override
    public int getPort() {
        return 5432;
    }

    protected ProbeSettings getProbeSettings() {
        return new ProbeSettings(300,
                String.valueOf(getPort()),
                5,
                "psql -h 127.0.0.1 -U $POSTGRESQL_USER -q -d $POSTGRESQL_DATABASE -c 'SELECT 1'");
    }

    @Override
    public String toString() {
        return "PostgreSQL";
    }

    @Override
    protected String getJDBCConnectionStringPattern() {
        return "jdbc:postgresql://%s:%s/%s";
    }

    @Override
    public Map<String, String> getImageVariables() {
        Map<String, String> vars = super.getImageVariables();
        vars.put("POSTGRESQL_MAX_CONNECTIONS", "100");
        vars.put("POSTGRESQL_SHARED_BUFFERS", "16MB");
        vars.put("POSTGRESQL_MAX_PREPARED_TRANSACTIONS", "90");
        // Temporary workaround for https://github.com/sclorg/postgresql-container/issues/297
        // Increase the "set_passwords.sh" timeout from the default 60s to 300s to give the
        // PostgreSQL server chance properly to start under high OCP cluster load
        vars.put("PGCTLTIMEOUT", "300");
        return vars;
    }
}
