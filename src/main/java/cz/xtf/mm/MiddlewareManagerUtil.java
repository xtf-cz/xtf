package cz.xtf.mm;

import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import cz.xtf.openshift.builder.RouteBuilder;
import cz.xtf.openshift.imagestream.ImageRegistry;

import java.util.concurrent.TimeoutException;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MiddlewareManagerUtil {

	static DeploymentConfigBuilder configureDatastore(final ApplicationBuilder appBuilder) {
		final DeploymentConfigBuilder cassandra = appBuilder.deploymentConfig("middleware-manager-datastore", "middleware-manager-datastore", false);

		cassandra
				.onConfigurationChange()
				.podTemplate()
				.container()
				.fromImage(ImageRegistry.get().midlewareManagerStorage())
				.envVar("CASSANDRA_START_RPC", "true")
				.port(9042, "cql-port")
				.port(9160, "thift-port")
				.port(7000, "tcp-port")
				.port(7001, "ssl-port")
				.port(7199, "jmx-port")
				.addVolumeMount("cassandra-data", "/opt/apache-cassandra/data", false)
				.pod()
				.addEmptyDirVolume("cassandra-data").container()
				.addReadinessProbe().createTcpProbe("7000");

		appBuilder.service("middleware-manager-datastore")
				.addContainerSelector("name", "middleware-manager-datastore")
				.ports(new ServicePortBuilder().withName("cql-port").withPort(9042).withTargetPort(new IntOrString("cql-port")).build(),
						new ServicePortBuilder().withName("thift-port").withPort(9160).withTargetPort(new IntOrString("thift-port")).build(),
						new ServicePortBuilder().withName("tcp-port").withPort(7000).withTargetPort(new IntOrString("tcp-port")).build(),
						new ServicePortBuilder().withName("ssl-port").withPort(7001).withTargetPort(new IntOrString("ssl-port")).build(),
						new ServicePortBuilder().withName("jmx-port").withPort(7199).withTargetPort(new IntOrString("jmx-port")).build()
				);

		return cassandra;
	}

	static DeploymentConfigBuilder configureManager(final ApplicationBuilder appBuilder, final String hawkularUser, final String hawkularPassword) {
		// MW Manager
		final String httpHostName = httpHostname();
		final String httpsHostName = httpsHostname();
		DeploymentConfigBuilder dcb = appBuilder.deploymentConfig("middleware-manager", "middleware-manager", false);

		dcb
				.onConfigurationChange()
				.podTemplate()
				.container()
				.fromImage(ImageRegistry.get().midlewareManagerService())
				.envVar("HAWKULAR_BACKEND", "remote")
				.envVar("CASSANDRA_NODES", "middleware-manager-datastore")
				.envVar("HAWKULAR_USER", hawkularUser)
				.envVar("HAWKULAR_PASSWORD", hawkularPassword)
				.envVar("HAWKULAR_USE_SSL", "true")
				.envVar("HAWKULAR_HOSTNAME", httpsHostName)
				.envVar("JAVA_OPTS", "-Xms64m -Xmx512m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs= -Djava.awt.headless=true")
				.port(8080, "http-endpoint")
				.port(8443, "https-endpoint")
				.port(8787, "debug-endpoint")
				.addReadinessProbe().createHttpProbe("hawkular/metrics/status", "http-endpoint");

		appBuilder.service("mm-http")
				.addContainerSelector("name", "middleware-manager")
				.setContainerPort(8080);

		appBuilder.service("mm-https")
				.addContainerSelector("name", "middleware-manager")
				.setContainerPort(8443)
				.setPort(443);

		appBuilder.route("mm").forService("mm-http").exposedAsHost(httpHostName);
		appBuilder.route("mms").forService("mm-https").exposedAsHost(httpsHostName).passthrough();

		return dcb;
	}

	public static String deployMiddlewareManager(final String hawkularUser, final String hawkularPassword) throws TimeoutException, InterruptedException {
		// Cassandra
		ApplicationBuilder appBuilder = new ApplicationBuilder("middleware-manager");

		configureDatastore(appBuilder);
		configureManager(appBuilder, hawkularUser, hawkularPassword);

		appBuilder.buildApplication().deployWithoutBuild();

		return httpHostname();
	}

	public static String httpsHostname() {
		return RouteBuilder.createHostName("mms");
	}

	public static String httpHostname() {
		return RouteBuilder.createHostName("mm");
	}

}
