package cz.xtf.builder.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

public class SQLExecutorImpl implements SQLExecutor {
    private final String connectionURL;
    private final String username;
    private final String password;

    public SQLExecutorImpl(String connectionURL, String username, String password) {
        this.connectionURL = connectionURL;
        this.username = username;
        this.password = password;
    }

    @Override
    public void executeSQL(Consumer<Connection> execute) {
        try {
            final Connection db = DriverManager.getConnection(connectionURL, username, password);
            execute.accept(db);
            db.close();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void executeSQLFile(String resourceName) {
        executeSQL(db -> {
            try {
                IOUtils.readLines(SQLExecutorImpl.class.getResourceAsStream(resourceName)).forEach(x -> {
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

}
