package cz.xtf.builder.db;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.builder.builders.DeploymentConfigBuilder;

public interface OpenShiftAuxiliary {

    default DeploymentConfigBuilder configureDeployment(ApplicationBuilder appBuilder) {
        return configureDeployment(appBuilder, true);
    }

    DeploymentConfigBuilder configureDeployment(ApplicationBuilder appBuilder, boolean synchronous);

    void configureApplicationDeployment(DeploymentConfigBuilder dcBuilder);
}
