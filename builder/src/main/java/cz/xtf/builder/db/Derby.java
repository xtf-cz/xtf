package cz.xtf.builder.db;

import cz.xtf.builder.builders.pod.ContainerBuilder;
import cz.xtf.core.image.Image;

public class Derby extends AbstractSQLDatabase {

    public Derby() {
        super("DERBY", "/dbs");
    }

    @Override
    public String getImageName() {
        return Image.resolve("derby").getUrl();
    }

    @Override
    public int getPort() {
        return 1527;
    }

    @Override
    protected void configureContainer(ContainerBuilder containerBuilder) {
        throw new UnsupportedOperationException("Used only as external database");
    }

    @Override
    public String toString() {
        return "Derby";
    }

    @Override
    protected String getJDBCConnectionStringPattern() {
        return "jdbc:derby://%s:%s/%s;create=true";
    }
}
