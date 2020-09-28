package cz.xtf.builder.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;

public abstract class AbstractSQLDatabase extends AbstractDatabase implements SQLExecutor {

    public AbstractSQLDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe) {
        super(symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
    }

    public AbstractSQLDatabase(String symbolicName, String dataDir, boolean withLivenessProbe, boolean withReadinessProbe,
            boolean configureEnvironment) {
        super(symbolicName, dataDir, withLivenessProbe, withReadinessProbe, configureEnvironment);
    }

    public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            boolean withLivenessProbe, boolean withReadinessProbe) {
        super(username, password, dbName, symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
    }

    public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            boolean withLivenessProbe, boolean withReadinessProbe, boolean configureEnvironment) {
        super(username, password, dbName, symbolicName, dataDir, withLivenessProbe, withReadinessProbe, configureEnvironment);
    }

    public AbstractSQLDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc, boolean withLivenessProbe,
            boolean withReadinessProbe) {
        super(symbolicName, dataDir, pvc, withLivenessProbe, withReadinessProbe);
    }

    public AbstractSQLDatabase(String symbolicName, String dataDir, PersistentVolumeClaim pvc) {
        super(symbolicName, dataDir, pvc);
    }

    public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir,
            PersistentVolumeClaim pvc) {
        super(username, password, dbName, symbolicName, dataDir, pvc);
    }

    public AbstractSQLDatabase(String username, String password, String dbName, String symbolicName, String dataDir) {
        super(username, password, dbName, symbolicName, dataDir);
    }

    public AbstractSQLDatabase(String symbolicName, String dataDir) {
        super(symbolicName, dataDir);
    }

    public void executeSQL(Consumer<Connection> execute) {
        getSQLExecutor("127.0.0.1", getPort()).executeSQL(execute);
    }

    public void executeSQLFile(String resourceName) {
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

    public SQLExecutor getSQLExecutor(String hostname, int port) {
        return new SQLExecutorImpl(String.format(getJDBCConnectionStringPattern(), hostname, port, getDbName()), getUsername(),
                getPassword());
    }

    protected abstract String getJDBCConnectionStringPattern();
}
