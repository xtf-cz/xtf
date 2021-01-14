package cz.xtf.junit5.extensions.helpers;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.ConfigMap;
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

public class ResourcesPrinterHelper<X> implements AutoCloseable {
    private final Path file;
    private final Function<X, LinkedHashMap<String, String>> resourceToCols;
    private final List<String[]> rows;
    private int[] maxLengths;
    private String[] headers = null;

    private ResourcesPrinterHelper(Path file, Function<X, LinkedHashMap<String, String>> resourceToCols) {
        this.file = file;
        this.resourceToCols = resourceToCols;
        rows = new ArrayList<>();
    }

    public static ResourcesPrinterHelper<Event> forEvents(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getEventCols);
    }

    public static ResourcesPrinterHelper<Pod> forPods(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getPodCols);
    }

    public static ResourcesPrinterHelper<DeploymentConfig> forDCs(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getDCsCols);
    }

    public static ResourcesPrinterHelper<Route> forRoutes(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getRoutesCols);
    }

    public static ResourcesPrinterHelper<ConfigMap> forConfigMaps(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getConfigMapCols);
    }

    public static ResourcesPrinterHelper<Service> forServices(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getServicesCols);
    }

    public static ResourcesPrinterHelper<Secret> forSecrets(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getSecretsCols);
    }

    public static ResourcesPrinterHelper<Build> forBuilds(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getBuildCols);
    }

    public static ResourcesPrinterHelper<BuildConfig> forBCs(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getBCCols);
    }

    public static ResourcesPrinterHelper<ImageStream> forISs(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getISCols);
    }

    public static ResourcesPrinterHelper<StatefulSet> forStatefulSet(Path filePath) {
        return new ResourcesPrinterHelper<>(filePath, ResourcesPrinterHelper::getStatefulSetCols);
    }

    private static LinkedHashMap<String, String> getStatefulSetCols(StatefulSet statefulSet) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        map.put("NAME", statefulSet.getMetadata().getName());
        map.put("REPLICAS", statefulSet.getStatus().getReadyReplicas() + "/" + statefulSet.getStatus().getReplicas());
        return map;
    }

    private static LinkedHashMap<String, String> getISCols(ImageStream is) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        map.put("NAME", is.getMetadata().getName());
        map.put("IMAGE REPOSITORY", is.getStatus().getDockerImageRepository());
        map.put("PUBLIC IMAGE REPOSITORY", is.getStatus().getPublicDockerImageRepository());
        return map;
    }

    private static LinkedHashMap<String, String> getBCCols(BuildConfig bc) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
        map.put("NAME", bc.getMetadata().getName());
        map.put("TYPE", bc.getSpec().getStrategy().getType());
        map.put("FROM", bc.getSpec().getSource().getType());
        map.put("LATEST", String.valueOf(bc.getStatus().getLastVersion()));
        return map;
    }

    private static LinkedHashMap<String, String> getBuildCols(Build build) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
        map.put("NAME", build.getMetadata().getName());
        map.put("TYPE", build.getSpec().getStrategy().getType());
        map.put("FROM", build.getSpec().getSource().getType());
        map.put("STATUS", build.getStatus().getPhase());
        map.put("STARTED", build.getStatus().getStartTimestamp());
        map.put("DURATION",
                build.getStatus().getDuration() == null ? "" : (build.getStatus().getDuration() / 1000000000) + "s");
        return map;
    }

    private static LinkedHashMap<String, String> getEventCols(Event event) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(7);
        map.put("FIRST SEEN", event.getFirstTimestamp());
        map.put("LAST SEEN", event.getLastTimestamp());
        map.put("COUNT", String.valueOf(event.getCount()));
        map.put("TYPE", event.getType());
        map.put("REASON", event.getReason());
        map.put("OBJECT", event.getInvolvedObject().getKind() + "/"
                + event.getInvolvedObject().getName().replaceFirst("(.*?)\\..*", "$1"));
        map.put("MESSAGE", event.getMessage());
        return map;
    }

    private static LinkedHashMap<String, String> getPodCols(Pod pod) {
        int restart = pod.getStatus().getContainerStatuses().stream()
                .map(ContainerStatus::getRestartCount)
                .mapToInt(Integer::intValue)
                .sum();
        long ready = pod.getStatus().getContainerStatuses().stream().filter(ContainerStatus::getReady).count();
        long total = pod.getStatus().getContainerStatuses().size();

        LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
        map.put("NAME", pod.getMetadata().getName());
        map.put("READY", ready + "/" + total);
        map.put("STATUS", pod.getStatus().getPhase());
        map.put("RESTARTS", String.valueOf(restart));
        return map;
    }

    private static LinkedHashMap<String, String> getDCsCols(DeploymentConfig dc) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        map.put("NAME", dc.getMetadata().getName());
        map.put("READY", dc.getStatus().getReadyReplicas() + "/" + dc.getStatus().getReplicas());
        return map;
    }

    private static LinkedHashMap<String, String> getRoutesCols(Route route) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(3);
        map.put("NAME", route.getMetadata().getName());
        map.put("HOST", route.getSpec().getHost());
        map.put("SERVICES", route.getSpec().getTo().getName());
        return map;
    }

    private static LinkedHashMap<String, String> getConfigMapCols(ConfigMap configMap) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("NAME", configMap.getMetadata().getName());
        return map;
    }

    private static LinkedHashMap<String, String> getSecretsCols(Secret secret) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        map.put("NAME", secret.getMetadata().getName());
        map.put("TYPE", secret.getType());
        return map;
    }

    private static LinkedHashMap<String, String> getServicesCols(Service service) {
        final StringBuilder selector = new StringBuilder();
        service.getSpec().getSelector().forEach((k, v) -> selector.append(k).append("=").append(v).append(";"));
        final StringBuilder ports = new StringBuilder();
        service.getSpec().getPorts().stream()
                .forEach(
                        port -> ports.append(port.getPort()).append("->").append(port.getTargetPort().getIntVal()).append(";"));

        LinkedHashMap<String, String> map = new LinkedHashMap<>(3);
        map.put("NAME", service.getMetadata().getName());
        map.put("SELECTOR", selector.toString());
        map.put("PORTS", ports.toString());
        return map;
    }

    public void row(X resource) {
        row(resourceToCols.apply(resource));
    }

    public void row(LinkedHashMap<String, String> map) {
        // first row
        if (headers == null) {
            headers = map.keySet().toArray(new String[] {});
            maxLengths = new int[headers.length];
            row(headers);
        }
        String[] cols = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            cols[i] = map.get(headers[i]);
        }
        row(cols);
    }

    private void row(String[] cols) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i] != null) {
                maxLengths[i] = Math.max(maxLengths[i], cols[i].length());
            }
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
            if (!rows.isEmpty()) {
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
}
