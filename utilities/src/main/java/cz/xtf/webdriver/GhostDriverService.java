package cz.xtf.webdriver;

import java.util.Optional;
import java.util.function.Predicate;

import cz.xtf.openshift.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.PodService;
import cz.xtf.openshift.builder.PodBuilder;
import cz.xtf.openshift.builder.RouteBuilder;
import cz.xtf.openshift.builder.ServiceBuilder;
import cz.xtf.wait.WaitUtil;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.Route;

public class GhostDriverService implements Service {

	private static final int PHANTOMJS_DEPLOY_TIMEOUT_SECONDS = 600;

	private static final Logger LOGGER = LoggerFactory.getLogger(GhostDriverService.class);

	private Pod ghostDriverPod = null;
	private String hostName = null;

	private static GhostDriverService instance;

	private PodService podService =null;
	private int localPort;

	public synchronized static GhostDriverService get() {
		if (instance == null) {
			instance = new GhostDriverService();
		}

		return instance;
	}

	public synchronized boolean isStarted() throws Exception {
		return ghostDriverPod != null;
	}

	public synchronized void start() {

		if (ghostDriverPod != null) {
			Optional<Pod> p = OpenshiftUtil.getInstance().getPods().stream().filter(pod -> pod.getMetadata().getName().equals(ghostDriverPod.getMetadata().getName())).findFirst();
			if (!p.isPresent()) {
				ghostDriverPod = null;
			}
		}
		else {
			Optional<Pod> p = OpenshiftUtil.getInstance().getPods().stream().filter(pod -> pod.getMetadata().getName().equals("phantomjs")).findFirst();
			if (p.isPresent()) {

				Optional<Route> route = OpenshiftUtil.getInstance().getRoutes().stream().filter(pod -> pod.getMetadata().getName().startsWith("phantomjs")).findFirst();

				if (route.isPresent()) {

					ghostDriverPod = p.get();
					hostName = route.get().getSpec().getHost();

					try {
						WaitUtil.waitFor(WaitUtil.urlReturnsCode("http://" + hostName + "/status", 200), null, 1000L, PHANTOMJS_DEPLOY_TIMEOUT_SECONDS * 1000L);

						podService = new PodService(ghostDriverPod);
						localPort = podService.portForward(4444);
					}
					catch (Exception x)  {
						LOGGER.error("PhantomJS at {} not started after {} s", hostName, PHANTOMJS_DEPLOY_TIMEOUT_SECONDS, x);
					}

					return;
				}
			}
		}

		if (ghostDriverPod == null) {

			ServiceBuilder sb = new ServiceBuilder("phantomjs");
			sb.addLabel("name", "phantomjs");
			sb.addContainerSelector("name", "phantomjs");
			sb.setContainerPort(4444);

			PodBuilder pb = new PodBuilder("phantomjs");
			pb.addLabel("name", "phantomjs");
			pb.gracefulShutdown(0);
			pb.container()
					.envVar("IGNORE_SSL_ERRORS", "true")
					.fromImage(ImageRegistry.get().phantomJs())
					.port(4444, "webdriver");

			OpenshiftUtil.getInstance().createService(sb.build());

			ghostDriverPod = pb.build();
			OpenshiftUtil.getInstance().createPod(ghostDriverPod);

			hostName = RouteBuilder.createHostName("phantomjs");
			RouteBuilder rb = new RouteBuilder("phantomjs");
			rb
					.addLabel("name", "phantomjs")
					.forService("phantomjs")
					.exposedAsHost(hostName);

			OpenshiftUtil.getInstance().createRoute(rb.build());

			try {
				WaitUtil.waitFor(WaitUtil.urlReturnsCode("http://" + hostName + "/status", 200), null, 1000L, PHANTOMJS_DEPLOY_TIMEOUT_SECONDS * 1000L);

				podService = new PodService(ghostDriverPod);
				localPort = podService.portForward(4444);
			}
			catch (Exception x)  {
				LOGGER.error("PhantomJS at {} not started after {} s", hostName, PHANTOMJS_DEPLOY_TIMEOUT_SECONDS, x);
			}
		}
	}

	public synchronized String getHostName() {
		return hostName;
	}

	public synchronized void close() {
		if (ghostDriverPod != null) {

			Predicate<HasMetadata> isPhantomJs = resource -> "phantomjs".equals(resource.getMetadata().getLabels() != null ? resource.getMetadata().getLabels().get("name") : null);

			OpenshiftUtil.getInstance().getPods().stream().filter(isPhantomJs).forEach(OpenshiftUtil.getInstance()::deletePod);
			//FIXME OpenshiftUtil.getInstance().getRoutes().stream().filter(isPhantomJs).forEach(OpenshiftUtil.getInstance()::deleteResource);
			OpenshiftUtil.getInstance().getServices().stream().filter(isPhantomJs).forEach(OpenshiftUtil.getInstance()::deleteService);

			ghostDriverPod = null;
			try {
				podService.close();
			} catch (Exception e) {
				LOGGER.error("Error closing podService", e);
			}
			podService = null;
		}
	}

	public synchronized int getLocalPort() {
		return localPort;
	}
}
