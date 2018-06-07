package cz.xtf.openshift.db;

import java.sql.Connection;
import java.util.function.Consumer;

public interface SQLExecutor {
	public void executeSQL(Consumer<Connection> execute);
	public void executeSQLFile(String resourceName);
}
