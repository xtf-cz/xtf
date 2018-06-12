package cz.xtf.openshift.external;

import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import cz.xtf.openshift.builder.EnvironmentConfiguration;

import java.io.Closeable;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Endpoints;

public interface ExternalService<T> extends Closeable {
	public void configureApplicationDeployment(final DeploymentConfigBuilder dcBuilder);
	public void configureEnvironment(final EnvironmentConfiguration envConfig);
	public void configureEnvironmentWithHostPort(final EnvironmentConfiguration envConfig);
	public void configureService(final ApplicationBuilder appBuilder);
	public Endpoints getEndpoints(final ApplicationBuilder appBuilder);
	public void modify(Consumer<T> x);
}
