package cz.xtf.tracing;

import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.Service;
import cz.xtf.openshift.builder.PodBuilder;
import cz.xtf.openshift.builder.RouteBuilder;
import cz.xtf.openshift.builder.ServiceBuilder;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.wait.WaitUtil;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * @author Radek Koubsky
 */
public class Zipkin implements Service {
	public static final String ZIPKIN_LABEL_KEY = "name";
	private static final Logger LOGGER = LoggerFactory.getLogger(Zipkin.class);
	private static final String ZIPKIN_LABEL_VALUE = "zipkin";
	private static final String ZIPKIN_SERVICE_NAME = "zipkin-service";
	private static final String ZIPKIN_POD_NAME = "zipkin-pod";
	private static final String ZIPKIN_ROUTE_NAME = "zipkin-route";
	private static final long ZIPKIN_DEPLOY_TIMEOUT_SECONDS = 360_000L;
	private static Zipkin instance;
	private String hostName;
	private boolean started = false;

	public synchronized static Zipkin instance() {
		if (instance == null) {
			instance = new Zipkin();
		}
		return instance;
	}

	private Zipkin() {
	}

	@Override
	public synchronized void start() throws Exception {
		if (!isStarted()) {
			deployZipkin();
			waitForDeployment();
			this.started = true;
		}
	}

	@Override
	public synchronized void close() throws Exception {
		if (isStarted()) {
			final Predicate<HasMetadata> hasZipkinLabel = resource -> resource.getMetadata()
					.getLabels() != null && ZIPKIN_LABEL_VALUE.equals(resource.getMetadata()
					.getLabels()
					.get(ZIPKIN_LABEL_KEY));
			deleteRoute(hasZipkinLabel);
			deleteService(hasZipkinLabel);
			deletePod(hasZipkinLabel);
			this.started = false;
		}
	}

	@Override
	public synchronized boolean isStarted() throws Exception {
		return this.started && waitForDeployment();
	}

	@Override
	public synchronized String getHostName() {
		return this.hostName;
	}

	private boolean waitForDeployment() throws Exception {
		return WaitUtil.waitFor(WaitUtil.urlReturnsCode("http://" + this.hostName + "/", 200), null,
				WaitUtil.DEFAULT_WAIT_INTERVAL,
				ZIPKIN_DEPLOY_TIMEOUT_SECONDS);
	}

	private void deletePod(final Predicate<HasMetadata> hasZipkinLabel) {
		OpenshiftUtil.getInstance()
				.getPods()
				.stream()
				.filter(hasZipkinLabel)
				.forEach(OpenshiftUtil.getInstance()::deletePod);
	}

	private void deleteService(final Predicate<HasMetadata> hasZipkinLabel) {
		OpenshiftUtil.getInstance()
				.getServices()
				.stream()
				.filter(hasZipkinLabel)
				.forEach(OpenshiftUtil.getInstance()::deleteService);
	}

	private void deleteRoute(final Predicate<HasMetadata> hasZipkinLabel) {
		OpenshiftUtil.getInstance()
				.getRoutes()
				.stream()
				.filter(hasZipkinLabel)
				.forEach(OpenshiftUtil.getInstance()::deleteRoute);
	}


	private void deployZipkin() {
		createPod();
		createService();
		createRoute();
	}

	private void createPod() {
		OpenshiftUtil.getInstance()
				.createPod(
						new PodBuilder(ZIPKIN_POD_NAME).addLabel(ZIPKIN_LABEL_KEY, ZIPKIN_LABEL_VALUE)
								.container()
								.fromImage(ImageRegistry.get()
										.zipkin())
								.port(9411)
								.pod()
								.build());
	}

	private void createService() {
		OpenshiftUtil.getInstance()
				.createService(
						new ServiceBuilder(ZIPKIN_SERVICE_NAME).addLabel(ZIPKIN_LABEL_KEY, ZIPKIN_LABEL_VALUE)
								.addContainerSelector(ZIPKIN_LABEL_KEY, ZIPKIN_LABEL_VALUE)
								.setContainerPort(9411)
								.setPort(9411)
								.build());
	}

	private void createRoute() {
		this.hostName = RouteBuilder.createHostName("zipkin");
		OpenshiftUtil.getInstance()
				.createRoute(
						new RouteBuilder(ZIPKIN_ROUTE_NAME).addLabel(ZIPKIN_LABEL_KEY, ZIPKIN_LABEL_VALUE)
								.exposedAsHost(this.hostName)
								.forService(ZIPKIN_SERVICE_NAME)
								.build());
	}
}
