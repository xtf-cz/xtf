package cz.xtf.builder.db;

import java.util.Map;

import cz.xtf.builder.builders.DeploymentConfigBuilder;
import cz.xtf.core.image.Image;

public class MongoDB extends AbstractDatabase {

    public MongoDB() {
        super("MONGODB", "/var/lib/mongodb/data");
    }

    public MongoDB(String username, String password, String dbName) {
        super(username, password, dbName, "MONGODB", "/var/lib/mongodb/data");
    }

    @Override
    public String getImageName() {
        return Image.resolve("mongodb").getUrl();
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
