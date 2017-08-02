package cz.xtf.openshift.db;

import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;
import cz.xtf.openshift.imagestream.ImageRegistry;

import java.util.Map;

public class PostgreSQL extends AbstractSQLDatabase {

	public PostgreSQL() {
		super("POSTGRESQL", "/var/lib/pgsql/data");
	}

	public PostgreSQL(boolean withLivenessProbe, boolean withReadinessProbe) {
		super("POSTGRESQL", "/var/lib/pgsql/data", withLivenessProbe, withReadinessProbe);
	}

	public PostgreSQL(final PersistentVolumeClaim pvc) {
		super("POSTGRESQL", "/var/lib/pgsql/data", pvc);
	}

	public PostgreSQL(final PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
		super("POSTGRESQL", "/var/lib/pgsql/data", pvc, withLivenessProbe, withReadinessProbe);
	}

	public PostgreSQL(final String username, final String password, final String dbName) {
		super(username, password, dbName, "POSTGRESQL", "/var/lib/pgsql/data");
	}

	public PostgreSQL(final String symbolicName, boolean withLivenessProbe, boolean withReadinessProbe) {
		super(symbolicName, "/var/lib/pgsql/data", withLivenessProbe, withReadinessProbe);
	}

	@Override
	public String getImageName() {
		return ImageRegistry.get().postgresql();
	}

	@Override
	public int getPort() {
		return 5432;
	}

	@Override
	protected void configureContainer(ContainerBuilder containerBuilder) {

		if (withLivenessProbe) {
			containerBuilder.addLivenessProbe().setInitialDelay(300).createTcpProbe("5432");
		}

		if (withReadinessProbe) {
			containerBuilder.addReadinessProbe().setInitialDelaySeconds(5)
					.createExecProbe("/bin/sh", "-i", "-c", "psql -h 127.0.0.1 -U $POSTGRESQL_USER -q -d $POSTGRESQL_DATABASE -c 'SELECT 1'");
		}
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
		return vars;
	}

}
