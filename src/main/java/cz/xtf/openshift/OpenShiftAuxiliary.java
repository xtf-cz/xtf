package cz.xtf.openshift;

import cz.xtf.docker.DockerContainer;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;

import io.fabric8.kubernetes.api.model.Pod;

public interface OpenShiftAuxiliary {

	public DeploymentConfigBuilder configureDeployment(final ApplicationBuilder appBuilder, final boolean synchronous);

	public default DeploymentConfigBuilder configureDeployment(final ApplicationBuilder appBuilder) {
		return configureDeployment(appBuilder, true);
	}

	public void configureApplicationDeployment(final DeploymentConfigBuilder dcBuilder);

	public Pod getPod();

	public DockerContainer getContainer();
}
