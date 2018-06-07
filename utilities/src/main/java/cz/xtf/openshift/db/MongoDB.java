package cz.xtf.openshift.db;

import java.util.Map;

import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;

public class MongoDB extends AbstractDatabase {

	public MongoDB() {
		super("MONGODB", "/var/lib/mongodb/data");
	}

	public MongoDB(String username, String password, String dbName) {
		super(username, password, dbName, "MONGODB", "/var/lib/mongodb/data");
	}

	@Override
	public String getImageName() {
		return ImageRegistry.get().mongodb();
	}

	@Override
	public int getPort() {
		return 27017;
	}

	@Override
	public void configureApplicationDeployment(DeploymentConfigBuilder dcBuilder) {
		dcBuilder.podTemplate().container().envVars(getImageVariables());
	}

	@Override
	public Map<String, String> getImageVariables() {
		Map<String, String> vars = super.getImageVariables();
		vars.put(getSymbolicName() + "_ADMIN_PASSWORD", getPassword());
		return vars;
	}

}
