package cz.xtf.openshift.db;

import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;

public class MySQL extends AbstractSQLDatabase {

	public MySQL() {
		super("MYSQL", "/var/lib/mysql/data");
	}

	public MySQL(boolean withLivenessProbe, boolean withReadinessProbe) {
		super("MYSQL", "/var/lib/mysql/data", withLivenessProbe, withReadinessProbe);
	}

	public MySQL(final PersistentVolumeClaim pvc) {
		super("MYSQL", "/var/lib/mysql/data", pvc);
	}

	public MySQL(final PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
		super("MYSQL", "/var/lib/mysql/data", pvc, withLivenessProbe, withReadinessProbe);
	}

	public MySQL(String username, String password, String dbName) {
		super(username, password, dbName, "MYSQL", "/var/lib/mysql/data");
	}

	@Override
	public String getImageName() {
		return ImageRegistry.get().mysql();
	}

	@Override
	public int getPort() {
		return 3306;
	}

	@Override
	protected void configureContainer(ContainerBuilder containerBuilder) {
		if (withLivenessProbe) {
			containerBuilder.addLivenessProbe().setInitialDelay(30).createTcpProbe("3306");
		}

		if (withReadinessProbe) {
			containerBuilder.addReadinessProbe().setInitialDelaySeconds(5)
					.createExecProbe("/bin/sh", "-i", "-c", "MYSQL_PWD=\"$MYSQL_PASSWORD\" mysql -h 127.0.0.1 -u $MYSQL_USER -D $MYSQL_DATABASE -e 'SELECT 1'");
		}
	}

	@Override
	public String toString() {
		return "MySQL";
	}

	@Override
	protected String getJDBCConnectionStringPattern() {
		return "jdbc:mysql://%s:%s/%s";
	}
}
