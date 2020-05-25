package cz.xtf.junit5.listeners;

import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.platform.engine.TestExecutionResult.Status.FAILED;

/**
 * Listener for storing information from openshift in case a test fails.
 * It stores pod logs, openshift routes, secret, events etc. From both work and build namespace.
 * It implements two distinct interfaces TestExecutionListener and LifecycleMethodExecutionExceptionHandler
 * both serving different purpose and need to be applied differently.
 *
 * TestExecutionListener
 *    Is called whenever test ends. Stores openshift state if test failed.
 *    To use this interface add this class as a listener
 *
 * LifecycleMethodExecutionExceptionHandler
 *    This interface is executed when @BeforeAll or @BeforeEach method fails - throw an exception.
 *    To use this interface a test class needs to be extended with this class using @ExtendWith
 */
@Slf4j
public class OpenshiftStateRecorder implements TestExecutionListener, LifecycleMethodExecutionExceptionHandler, BeforeAllCallback {
	private static final String ALWAYS_REBUILD_PROPERTY = "xtf.state_recorder.store_always";
	private final boolean storeAlways;

	private final Path statusDir;

	private final OpenShift openShift;
	private final OpenShift buildOpenShift;

	private final String namespace;
	private final String buildNamespace;

	private final DateTimeFormatter openshiftTimestampFormatter;
	private static LocalDateTime testStartTime;

	public OpenshiftStateRecorder(){
		statusDir = Paths.get("log","status");
		openShift = OpenShifts.master();
		namespace = openShift.getNamespace();
		buildNamespace = XTFConfig.get(BuildManagerConfig.BUILD_NAMESPACE);
		buildOpenShift = OpenShifts.master(buildNamespace);
		openshiftTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
		storeAlways = Boolean.parseBoolean(XTFConfig.get(ALWAYS_REBUILD_PROPERTY, "false"));
	}

	private String getTestDisplayName(TestIdentifier testIdentifier) {
		String className = testIdentifier.getParentId().get()
			.replaceAll(".*class:", "")
			.replaceAll("].*", "");
		return String.format("%s#%s", className, testIdentifier.getDisplayName());
	}

	/**
	 * Store time every time test class is started.
	 * During storing state, only events after this time are stored.
	 */
	@Override
	public void beforeAll(ExtensionContext extensionContext) throws Exception {
		testStartTime = LocalDateTime.now(ZoneOffset.UTC);
	}

	// Log the openshift state if BeforeAll fail
	@Override
	public void handleBeforeAllMethodExecutionException(final ExtensionContext context, final Throwable ex) throws Throwable {
		try {
			storeOpenshiftState(context.getDisplayName());
		} catch(Exception newEx){
			// if new exception is thrown, add it to original one so it is not lost
			ex.addSuppressed(newEx);
		}
		throw ex;
	}

	// Log the openshift state if BeforeEach fail
	@Override
	public void handleBeforeEachMethodExecutionException(final ExtensionContext context, final Throwable ex) throws Throwable {
		try {
			storeOpenshiftState(context.getDisplayName());
		} catch(Exception newEx){
			// if new exception is thrown, add it to original one so it is not lost
			ex.addSuppressed(newEx);
		}
		throw ex;
	}

	// Log the openshift state if test fail
	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		try {
			if ((storeAlways || FAILED.equals(testExecutionResult.getStatus())) && testIdentifier.isTest()) {
				storeOpenshiftState(getTestDisplayName(testIdentifier));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void storeOpenshiftState(String testIdentifier) throws IOException {
		final Path testLogDir = statusDir.resolve(testIdentifier);
		Files.createDirectories(testLogDir);

		storeNamespaceState(testLogDir, openShift);
		storePods(testLogDir, openShift);

		if (!namespace.equals(buildNamespace)){
			storeNamespaceState(testLogDir, buildOpenShift);
			storePods(testLogDir, buildOpenShift);
		}
	}

	private void storePods(Path dir, OpenShift openshift){
		for (Pod pod : openshift.getPods()) {
			try {
				openshift.storePodLog(pod, dir, pod.getMetadata().getName() + ".log");
			} catch (IOException e) {
				log.warn("IOException storing pod logs", e);
			} catch (KubernetesClientException e) {
				log.warn("KubernetesClientException getting pod logs", e);
			}
		}
	}

	private void storeNamespaceState(Path directory, OpenShift openShift) throws IOException {
		String namespace = openShift.getNamespace();
		BufferedWriter writer = new BufferedWriter(new FileWriter(directory.toAbsolutePath() + "/namespace-" + namespace + ".txt"));
		writer.write("Namespace " + namespace + " state - " + LocalDateTime.now() + " - UTC:" + LocalDateTime.now(ZoneOffset.UTC));
		writer.newLine();

		writer.write("Pods: (name ; status ; readyContainers/totalContainers ; containerRestart)");
		writer.newLine();
		for (Pod pod : openShift.getPods()) {
			long readyContainers = pod.getStatus().getContainerStatuses().stream()
						.filter(ContainerStatus::getReady).count();

			writer.write(pod.getMetadata().getName()+ " ; " + pod.getStatus().getPhase()
					+ " ; " + readyContainers + "/" + pod.getStatus().getContainerStatuses().size()
					+ " ; " + pod.getStatus().getContainerStatuses().stream()
						.map(ContainerStatus::getRestartCount)
						.mapToInt(Integer::intValue)
						.sum());
			writer.newLine();
		}
		writer.newLine();


		writer.write("DeploymentConfigs: (name - status)");
		writer.newLine();
		for (DeploymentConfig deploymentConfig : openShift.getDeploymentConfigs()) {
			writer.write(deploymentConfig.getMetadata().getName()+ " replicas:" + deploymentConfig.getStatus().getReplicas() + " readyReplicas: " + deploymentConfig.getStatus().getReadyReplicas());
			writer.newLine();
		}
		writer.newLine();


		writer.write("Events since: " + testStartTime);
		writer.newLine();
		for (Event event : openShift.getEvents()){
			if (LocalDateTime.parse(event.getFirstTimestamp(), openshiftTimestampFormatter).isAfter(testStartTime)) {
				writer.write(event.getFirstTimestamp() + " " +
						event.getInvolvedObject().getKind() + ":" + event.getInvolvedObject().getName() + " => " +
						event.getMessage());
				writer.newLine();
			}
		}
		writer.newLine();


		writer.write("Routes:  (name - host)");
		writer.newLine();
		for (Route route : openShift.getRoutes()){
			writer.write(route.getMetadata().getName() + "  " + route.getSpec().getHost());
			writer.newLine();
		}
		writer.newLine();

		writer.write("Services: (name - selector)");
		writer.newLine();
		for (Service service : openShift.getServices()){
			writer.write(service.getMetadata().getName() + "  " + service.getSpec().getSelector());
			writer.newLine();
		}
		writer.newLine();

		writer.write("Secrets: ");
		writer.newLine();
		for (Secret secret : openShift.getSecrets()){
			writer.write(secret.getMetadata().getName() + "  " + secret.getType());
			writer.newLine();
		}
		writer.newLine();

		writer.close();
	}
}
