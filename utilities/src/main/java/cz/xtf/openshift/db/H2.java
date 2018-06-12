package cz.xtf.openshift.db;


import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;
import cz.xtf.openshift.imagestream.ImageRegistry;

public class H2 extends AbstractSQLDatabase {

	public H2() {
		super("H2", "/opt/jboss/h2");
	}

	public H2(boolean withLivenessProbe, boolean withReadinessProbe) {
		super("H2", "/opt/jboss/h2", withLivenessProbe, withReadinessProbe);
	}

	public H2(boolean withLivenessProbe, boolean withReadinessProbe, boolean configureEnvironment) {
		super("H2", "/opt/jboss/h2", withLivenessProbe, withReadinessProbe, configureEnvironment);
	}

	public H2(final PersistentVolumeClaim pvc) {
		super("H2", "/opt/jboss/h2", pvc);
	}

	public H2(final PersistentVolumeClaim pvc, boolean withLivenessProbe, boolean withReadinessProbe) {
		super("H2", "/opt/jboss/h2", pvc, withLivenessProbe, withReadinessProbe);
	}

	public H2(String username, String password, String dbName, boolean withLivenessProbe, boolean withReadinessProbe) {
		super(username, password, dbName, "H2", "/opt/jboss/h2", withLivenessProbe, withReadinessProbe);
	}

	public H2(String username, String password, String dbName, boolean withLivenessProbe, boolean withReadinessProbe, boolean configureEnvironment) {
		super(username, password, dbName, "H2", "/opt/jboss/h2", withLivenessProbe, withReadinessProbe, configureEnvironment);
	}


	@Override
	public String getImageName() {
		return ImageRegistry.get().h2();
	}

	@Override
	public int getPort() {
		return 9092;
	}

	@Override
	protected void configureContainer(ContainerBuilder containerBuilder) {

		if (withLivenessProbe) {
			containerBuilder.addLivenessProbe().setInitialDelay(30).createTcpProbe("9092");
		}

		if (withReadinessProbe) {
			containerBuilder.addReadinessProbe().setInitialDelaySeconds(5)
					.createExecProbe("java", "-cp", "/opt/jboss/h2-1.3.168.jar", "org.h2.tools.Shell", "-url", "jdbc:h2:tcp://127.0.0.1/./h2/test1", "-user", "sa", "-password", "", "-driver", "org.h2.Driver", "-sql", "'select 1'");
		}
	}

	@Override
	public String toString() {
		return "H2";
	}

	@Override
	protected String getJDBCConnectionStringPattern() {
		return "jdbc:h2:tcp://%s:%s/./h2/%s";
	}

}
