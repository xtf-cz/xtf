package cz.xtf.openshift.db;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;

public class SQLExecutorImpl implements SQLExecutor {
	private final String connectionURL;
	private final String username;
	private final String password;

	public SQLExecutorImpl(final String connectionURL, final String username, final String password) {
		this.connectionURL = connectionURL;
		this.username = username;
		this.password = password;
	}

	@Override
	public void executeSQL(final Consumer<Connection> execute) {
		try {
			final Connection db = DriverManager.getConnection(connectionURL, username, password);
			execute.accept(db);
			db.close();
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void executeSQLFile(final String resourceName) {
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
