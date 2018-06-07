package cz.xtf.mm;

import cz.xtf.docker.DockerContainer;
import cz.xtf.keystore.ProcessKeystoreGenerator;
import cz.xtf.openshift.OpenShiftAuxiliary;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;

import java.io.IOException;
import java.nio.file.Files;

import io.fabric8.kubernetes.api.model.Pod;

public class MiddlewareManager implements OpenShiftAuxiliary {

	private String hawkularUsername;
	private String hawkularPassword;
	private String tenantId;
	private boolean isSecure;

	private byte[] clientKeyPem;
	private byte[] clientCertPem;
	private byte[] clientTruststore;

	public MiddlewareManager(String tenantId, String hawkularUsername, String hawkularPassword, boolean isSecure) {
		this.hawkularUsername = hawkularUsername;
		this.hawkularPassword = hawkularPassword;
		this.tenantId = tenantId;
		this.isSecure = isSecure;

		final ProcessKeystoreGenerator.CertPaths certPaths = ProcessKeystoreGenerator.generateCerts(MiddlewareManagerUtil.httpsHostname());

		try {
			clientKeyPem = Files.readAllBytes(certPaths.keyPem);
			clientCertPem = Files.readAllBytes(certPaths.certPem);
			clientTruststore = Files.readAllBytes(certPaths.truststore);
		}
		catch(IOException x) {
			throw new RuntimeException(x);
		}
	}

	@Override
	public DeploymentConfigBuilder configureDeployment(final ApplicationBuilder appBuilder, final boolean synchronous) {
		MiddlewareManagerUtil.configureDatastore(appBuilder).synchronousDeployment(1);
		DeploymentConfigBuilder dcb = MiddlewareManagerUtil.configureManager(appBuilder, hawkularUsername, hawkularPassword);

		if (synchronous) {
			dcb.synchronousDeployment(2);
		}

		appBuilder.addSecret("hawkular-client-secrets", "hawkular-services-private.key", clientKeyPem);
		appBuilder.addSecret("hawkular-client-secrets", "hawkular-services-public.pem", clientCertPem);

		appBuilder.addSecret("hawkular-client-public", "truststore", clientTruststore);

		dcb.podTemplate().addSecretVolume("client-secrets", "hawkular-client-secrets");
		dcb.podTemplate().container().addVolumeMount("client-secrets", "/client-secrets", true);

		return dcb;
	}

	@Override
	public void configureApplicationDeployment(DeploymentConfigBuilder dcBuilder) {
		dcBuilder.podTemplate().container()
				.envVar("AB_HAWKULAR_REST_USER", hawkularUsername)
				.envVar("AB_HAWKULAR_REST_PASSWORD", hawkularPassword)
				.envVar("AB_HAWKULAR_REST_TENANT_ID", tenantId);

		if (isSecure) {
			dcBuilder.podTemplate()
					.addSecretVolume("hawkular-client-public", "hawkular-client-public")
					.container()
					.addVolumeMount("hawkular-client-public", "/ssl/hawkular", true)
					.envVar("AB_HAWKULAR_REST_URL", "https://" + MiddlewareManagerUtil.httpsHostname() + "/")
					.envVar("AB_HAWKULAR_REST_KEYSTORE", "truststore")
					.envVar("AB_HAWKULAR_REST_KEYSTORE_DIR", "/ssl/hawkular")
					.envVar("AB_HAWKULAR_REST_KEYSTORE_PASSWORD", "password");
		}
		else {
			dcBuilder.podTemplate().container().envVar("AB_HAWKULAR_REST_URL", "http://" +  MiddlewareManagerUtil.httpHostname() + "/");
		}
	}

	@Override
	public Pod getPod() {
		throw new UnsupportedOperationException();
	}

	@Override
	public DockerContainer getContainer() {
		throw new UnsupportedOperationException();
	}
}
