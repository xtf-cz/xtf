package cz.xtf.openshift.db;

import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.imagestream.ImageRegistry;

public class Derby extends AbstractSQLDatabase {

	public Derby() {
		super("DERBY", "/dbs");
	}

	@Override
	public String getImageName() {
		return ImageRegistry.get().derby();
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
