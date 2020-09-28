package cz.xtf.builder.db;

import java.sql.Connection;
import java.util.function.Consumer;

public interface SQLExecutor {
    void executeSQL(Consumer<Connection> execute);

    void executeSQLFile(String resourceName);
}
