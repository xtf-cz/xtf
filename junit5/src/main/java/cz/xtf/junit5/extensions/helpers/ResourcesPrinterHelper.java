package cz.xtf.junit5.extensions.helpers;


import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ResourcesPrinterHelper<X> implements AutoCloseable {
	private final Path file;
	private final Function<X, String[]> resourceToCols;
	private final int[] maxLengths;
	private final List<String[]> rows;

	private ResourcesPrinterHelper(Path file, Function<X, String[]> resourceToCols, String... colNames) {
		this.file = file;
		this.resourceToCols = resourceToCols;
		maxLengths = new int[colNames.length];
		rows = new ArrayList<>();
		row(colNames);
	}

	public static ResourcesPrinterHelper<Event> forEvents(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getEventCols, "FIRST SEEN", "LAST SEEN", "COUNT", "TYPE", "REASON", "OBJECT", "MESSAGE");
	}

	public static ResourcesPrinterHelper<Pod> forPods(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getPodCols, "NAME", "READY", "STATUS", "RESTARTS");
	}

	public static ResourcesPrinterHelper<DeploymentConfig> forDCs(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getDCsCols, "NAME", "READY");
	}

	public static ResourcesPrinterHelper<Route> forRoutes(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getRoutesCols, "NAME", "HOST", "SERVICES");
	}

	public static ResourcesPrinterHelper<Service> forServices(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getServicesCols, "NAME", "SELECTOR", "PORTS");
	}

	public static ResourcesPrinterHelper<Secret> forSecrets(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getSecretsCols, "NAME", "TYPE");
	}

	public static ResourcesPrinterHelper<Build> forBuilds(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getBuildCols, "NAME", "TYPE", "FROM", "STATUS", "STARTED", "DURATION");
	}

	public static ResourcesPrinterHelper<BuildConfig> forBCs(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getBCCols, "NAME", "TYPE", "FROM", "LATEST");
	}


	public static ResourcesPrinterHelper<ImageStream> forISs(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getISCols, "NAME", "IMAGE REPOSITORY");
	}

	public static ResourcesPrinterHelper<StatefulSet> forStatefulSet(Path filePath) {
		return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getStatefulSetCols, "NAME", "REPLICAS");
	}

	private static String[] getStatefulSetCols(StatefulSet statefulSet) {
		return new String[]{statefulSet.getMetadata().getName(), statefulSet.getStatus().getReadyReplicas() + "/" + statefulSet.getStatus().getReplicas()};
	}

	private static String[] getISCols(ImageStream is) {
		return new String[]{is.getMetadata().getName(), is.getStatus().getPublicDockerImageRepository()};
	}

	private static String[] getBCCols(BuildConfig bc) {
		return new String[]{bc.getMetadata().getName(), bc.getSpec().getStrategy().getType(), bc.getSpec().getSource().getType(),
				String.valueOf(bc.getStatus().getLastVersion())};
	}

	private static String[] getBuildCols(Build build) {
		return new String[]{build.getMetadata().getName(), build.getSpec().getStrategy().getType(), build.getSpec().getSource().getType(),
				build.getStatus().getPhase(), build.getStatus().getStartTimestamp(), (build.getStatus().getDuration() / 1000000000) + "s"};
	}

	private static String[] getEventCols(Event event) {
		return new String[]{event.getFirstTimestamp(), event.getLastTimestamp(), String.valueOf(event.getCount()),
				event.getType(), event.getReason(),
				event.getInvolvedObject().getKind() + "/" + event.getInvolvedObject().getName().replaceFirst("(.*?)\\..*", "$1"),
				event.getMessage()};
	}

	private static String[] getPodCols(Pod pod) {
		int restart = pod.getStatus().getContainerStatuses().stream()
				.map(ContainerStatus::getRestartCount)
				.mapToInt(Integer::intValue)
				.sum();
		long ready = pod.getStatus().getContainerStatuses().stream().filter(ContainerStatus::getReady).count();
		long total = pod.getStatus().getContainerStatuses().size();
		return new String[]{pod.getMetadata().getName(), ready + "/" + total, pod.getStatus().getPhase(), String.valueOf(restart)};
	}

	private static String[] getDCsCols(DeploymentConfig dc) {
		return new String[]{dc.getMetadata().getName(), dc.getStatus().getReadyReplicas() + "/" + dc.getStatus().getReplicas()};
	}

	private static String[] getRoutesCols(Route route) {
		return new String[]{route.getMetadata().getName(), route.getSpec().getHost(), route.getSpec().getTo().getName()};
	}

	private static String[] getSecretsCols(Secret secret) {
		return new String[]{secret.getMetadata().getName(), secret.getType()};
	}

	private static String[] getServicesCols(Service service) {
		final StringBuilder selector = new StringBuilder();
		service.getSpec().getSelector().forEach((k, v) -> selector.append(k).append("=").append(v).append(";"));
		final StringBuilder ports = new StringBuilder();
		service.getSpec().getPorts().stream()
				.forEach(port -> ports.append(port.getPort()).append("->").append(port.getTargetPort().getIntVal()).append(";"));
		return new String[]{service.getMetadata().getName(), selector.toString(), ports.toString()};
	}

	public void row(X resource) {
		row(resourceToCols.apply(resource));
	}

	public void row(String... cols) {
		for (int i = 0; i < cols.length; i++) {
			maxLengths[i] = Math.max(maxLengths[i], cols[i].length());
		}
		rows.add(cols);
	}

	@Override
	public void close() throws IOException {
		flush();
	}

	public void flush() throws IOException {
		file.getParent().toFile().mkdirs();

		try (final Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
			StringBuilder formatBuilder = new StringBuilder();
			for (int maxLength : maxLengths) {
				formatBuilder.append("%-").append(maxLength + 2).append("s");
			}
			String format = formatBuilder.toString();

			StringBuilder result = new StringBuilder();
			for (String[] row : rows) {
				writer.append(String.format(format, row)).append("\n");
			}
			writer.flush();
		}
	}

}
