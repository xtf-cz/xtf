package cz.xtf.builder.db;

import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.image.Image;

public class MySQL extends AbstractSQLDatabase {

    private static final String DATA_DIR_PROPERTY = "xtf.mysql.datadir";
    private static final String MYSQL_DATA_DIR = XTFConfig.get(DATA_DIR_PROPERTY, "/var/lib/mysql/data");

    public MySQL() {
        super("MYSQL", MYSQL_DATA_DIR);
    }

    public MySQL(boolean withLivenessProbe, boolean withReadinessProbe) {
        super("MYSQL", MYSQL_DATA_DIR, withLivenessProbe, withReadinessProbe);
    }

    public MySQL(boolean withLivenessProbe, boolean withReadinessProbe, boolean withStartupProbe) {
        super("MYSQL", MYSQL_DATA_DIR, withLivenessProbe, withReadinessProbe, withStartupProbe, true);
    }

    public MySQL(PersistentVolumeClaim pvc) {
        super("MYSQL", MYSQL_DATA_DIR, pvc);
    }

    public MySQL(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
        super("MYSQL", MYSQL_DATA_DIR, pvc, withLivenessProbe, withReadinessProbe);
    }

    public MySQL(PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe, boolean withStartupProbe) {
        super("MYSQL", MYSQL_DATA_DIR, pvc, withLivenessProbe, withReadinessProbe, withStartupProbe);
    }

    public MySQL(String username, String password, String dbName) {
        super(username, password, dbName, "MYSQL", MYSQL_DATA_DIR);
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
                "MYSQL_PWD=\"$MYSQL_PASSWORD\" mysql -h 127.0.0.1 -u $MYSQL_USER -D $MYSQL_DATABASE -e 'SELECT 1'",
                5,
                "MYSQL_PWD=\"$MYSQL_PASSWORD\" mysql -h 127.0.0.1 -u $MYSQL_USER -D $MYSQL_DATABASE -e 'SELECT 1'",
                10,
                10);
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
