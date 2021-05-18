package cz.xtf.builder.db;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.core.image.Image;

public class MySQL extends AbstractSQLDatabase {

    public MySQL() {
        super("MYSQL", "/var/lib/mysql/data");
    }

    public MySQL(boolean withLivenessProbe, boolean withReadinessProbe) {
        super("MYSQL", "/var/lib/mysql/data", withLivenessProbe, withReadinessProbe);
    }

    public MySQL(PersistentVolumeClaim pvc) {
        super("MYSQL", "/var/lib/mysql/data", pvc);
    }

    public MySQL(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super("MYSQL", "/var/lib/mysql/data", pvc, withLivenessProbe, withReadinessProbe);
    }

    public MySQL(String username, String password, String dbName) {
        super(username, password, dbName, "MYSQL", "/var/lib/mysql/data");
    }

    @Override
    public String getImageName() {
        return Image.resolve("mysql").getUrl();
    }

    @Override
    public int getPort() {
        return 3306;
    }

    @Override
    protected ProbeSettings getProbeSettings() {
        return new ProbeSettings(30,
                String.valueOf(getPort()),
                5,
                "MYSQL_PWD=\"$MYSQL_PASSWORD\" mysql -h 127.0.0.1 -u $MYSQL_USER -D $MYSQL_DATABASE -e 'SELECT 1'");
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
