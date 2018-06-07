package cz.xtf.openshift.db;

import cz.xtf.openshift.PodService;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

public abstract class AbstractSQLDatabase extends AbstractDatabase implements SQLExecutor {

	public AbstractSQLDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe) {
		super(symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
	}

	public AbstractSQLDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe, boolean configureEnvironment) {
		super(symbolicName, dataDir, withLivenessProbe, withReadinessProbe, configureEnvironment);
	}

	public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe) {
		super(username, password, dbName, symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
	}

	public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe, boolean configureEnvironment) {
		super(username, password, dbName, symbolicName, dataDir, withLivenessProbe, withReadinessProbe, configureEnvironment);
	}

	public AbstractSQLDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
		super(symbolicName, dataDir, pvc, withLivenessProbe, withReadinessProbe);
	}

	public AbstractSQLDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc) {
		super(symbolicName, dataDir, pvc);
	}

	public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir, PersistentVolumeClaim pvc) {
		super(username, password, dbName, symbolicName, dataDir, pvc);
	}

	public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir) {
		super(username, password, dbName, symbolicName, dataDir);
	}

	public AbstractSQLDatabase(String symbolicName, String dataDir) {
		super(symbolicName, dataDir);
	}

	public SQLExecutor getSQLExecutor(final String hostname, final int port) {
		return new SQLExecutorImpl(String.format(getJDBCConnectionStringPattern(), hostname, port, getDbName()), getUsername(), getPassword());
	}

	public void executeSQL(final Consumer<Connection> execute) {
		try {
			try (final PodService pod = new PodService(getPod())) {
				getSQLExecutor("127.0.0.1", pod.portForward(getPort())).executeSQL(execute);
			}
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public void executeSQLFile(final String resourceName) {
		executeSQL(db -> {
			try {
				IOUtils.readLines(AbstractSQLDatabase.class.getResourceAsStream(resourceName)).forEach(x -> {
					try {
						db.createStatement().execute(x);
					} catch (SQLException e) {
						throw new IllegalArgumentException(e);
					}
				});
			} catch (final IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	protected abstract String getJDBCConnectionStringPattern();
}
