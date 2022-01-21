package cz.xtf.core.service.logs.streaming.k8s;

import static java.util.stream.Collectors.toList;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cz.xtf.core.service.logs.streaming.ServiceLogColor;
import cz.xtf.core.service.logs.streaming.ServiceLogColoredPrintStream;
import cz.xtf.core.service.logs.streaming.ServiceLogUtils;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Watches Pods on a <b>single</b> namespace and streams logs for all Pods containers to the selected
 * {@link PrintStream}.
 *
 * Each log is prefixed with <code>namespace.pod-name.container-name</code> and colored differently.
 */
@Slf4j
class PodLogsWatcher implements Watcher<Pod> {
    private static final int LOG_TAILING_LINES = 100;
    private static final int LOG_WAIT_TIMEOUT = 60000;
    private KubernetesClient client;
    private String namespace;
    private PrintStream printStream;
    private Pattern filter;

    protected PodLogsWatcher(KubernetesClient client, String namespace, PrintStream printStream, Pattern filter) {
        this.client = client;
        this.namespace = namespace;
        // The life cycle of the PrintStream instance that is being used must be handled in the outer scope, which is
        // not here.
        // Here it is just passed in to be used, hence not altered at all.
        this.printStream = printStream;
        this.filter = filter;
    }

    private List<ContainerStatus> runningStatusesBefore = Collections.emptyList();
    private List<ContainerStatus> terminatedStatusesBefore = Collections.emptyList();
    private final Map<String, LogWatch> logWatches = new HashMap<>();

    /**
     * Handles newly running containers
     * 
     * @param pod the Pod where to look for newly running containers
     */
    private void handleNewRunningContainers(Pod pod) {
        // existing containers running statuses
        List<ContainerStatus> existingContainersRunningStatuses = getRunningContainersStatuses(pod);
        log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                String.format("existingContainersRunningStatuses.size=%s names=%s existingContainersRunningStatuses=%s",
                        existingContainersRunningStatuses.size(),
                        existingContainersRunningStatuses.stream().map(cs -> cs.getName()).collect(Collectors.joining()),
                        existingContainersRunningStatuses)));
        // newly running containers statuses
        List<ContainerStatus> newContainersRunningStatuses = getNewContainers(runningStatusesBefore,
                existingContainersRunningStatuses);
        log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                String.format("newContainersRunningStatuses.size=%s names=%s newContainersRunningStatuses=%s",
                        newContainersRunningStatuses.size(),
                        newContainersRunningStatuses.stream().map(cs -> cs.getName()).collect(Collectors.joining()),
                        newContainersRunningStatuses)));
        // let's update the currently running containers
        runningStatusesBefore = existingContainersRunningStatuses;
        // now let's create and start the LogWatch instances that will monitor the containers' logs
        for (ContainerStatus status : newContainersRunningStatuses) {
            log.info(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                    String.format("Container %s.%s.%s running...", namespace, pod.getMetadata().getName(), status.getName())));
            log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                    String.format(
                            "CONTAINER status: name=%s, started=%s, ready=%s \n\t waiting=%s \n\t running=%s \n\t terminated=%s \n\t complete status=%s",
                            status.getName(),
                            status.getStarted(),
                            status.getReady(),
                            status.getState().getWaiting(),
                            status.getState().getRunning(),
                            status.getState().getTerminated(),
                            pod.getStatus())));
            if (filter != null && filter.matcher(pod.getMetadata().getName()).matches()) {
                log.info(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                        String.format("Skipped Pod %s.%s", namespace, pod.getMetadata().getName())));
                continue;
            }
            if (filter != null && filter.matcher(status.getName()).matches()) {
                log.info(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                        String.format("Skipped Container %s.%s.%s", namespace, pod.getMetadata().getName(), status.getName())));
                continue;
            }
            final LogWatch lw = client.pods().inNamespace(namespace).withName(pod.getMetadata().getName())
                    .inContainer(status.getName())
                    .tailingLines(LOG_TAILING_LINES)
                    .withLogWaitTimeout(LOG_WAIT_TIMEOUT)
                    .watchLog(
                            new ServiceLogColoredPrintStream.Builder()
                                    .outputTo(printStream)
                                    .withColor(ServiceLogColor.getNext())
                                    .withPrefix(
                                            forgeContainerLogPrefix(pod, status))
                                    .build());
            logWatches.put(status.getContainerID(), lw);
            log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                    String.format("PodLogsWatcher started for Container %s in Pod %s in Namespace %s",
                            status.getName(),
                            pod.getMetadata().getName(),
                            namespace)));
        }
    }

    private String forgeContainerLogPrefix(Pod pod, ContainerStatus status) {
        return String.format("%s.%s.%s",
                namespace,
                pod.getMetadata().getName(),
                status.getName());
    }

    /**
     * Handles newly terminated containers
     * 
     * @param pod the Pod where to look for newly terminated containers
     */
    private void handleNewTerminatedContainers(Pod pod) {
        // existing containers terminated statuses
        List<ContainerStatus> existingContainersTerminatedStatuses = getTerminatedContainers(pod);
        log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                String.format("existingContainersTerminatedStatuses.size=%s names=%s existingContainersTerminatedStatuses=%s",
                        existingContainersTerminatedStatuses.size(),
                        existingContainersTerminatedStatuses.stream().map(cs -> cs.getName()).collect(Collectors.joining()),
                        existingContainersTerminatedStatuses)));
        // newly terminated containers statuses
        List<ContainerStatus> newContainersTerminatedStatuses = getNewContainers(terminatedStatusesBefore,
                existingContainersTerminatedStatuses);
        log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                String.format("newContainersTerminatedStatuses.size=%s names=%s newContainersTerminatedStatuses=%s",
                        newContainersTerminatedStatuses.size(),
                        newContainersTerminatedStatuses.stream().map(cs -> cs.getName()).collect(Collectors.joining()),
                        newContainersTerminatedStatuses)));
        // let's update the currently terminated containers
        terminatedStatusesBefore = existingContainersTerminatedStatuses;
        // now let's terminate the LogWatch instances that are monitoring the containers' logs
        for (ContainerStatus status : newContainersTerminatedStatuses) {
            log.info(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                    String.format("Container %s.%s.%s was terminated!", namespace, pod.getMetadata().getName(),
                            status.getName())));
            if (logWatches.containsKey(status.getContainerID())) {
                // the log watch must be closed so that internal resources are managed properly (e.g.:
                // `Closeable` instances will be closed)
                logWatches.get(status.getContainerID()).close();
                log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage("Terminating PodLogsWatcher"));
                logWatches.remove(status.getContainerID());
            }
        }
    }

    @SneakyThrows
    @Override
    public void eventReceived(Action action, Pod pod) {
        log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                String.format("%s %s: %s", action.name(), pod.getMetadata().getName(), pod.getStatus())));
        switch (action) {
            case ADDED:
                handleNewRunningContainers(pod);
                break;
            case MODIFIED:
                handleNewRunningContainers(pod);
                handleNewTerminatedContainers(pod);
                break;
            case DELETED:
            case ERROR:
                handleNewTerminatedContainers(pod);
                break;
            default:
                log.error(ServiceLogUtils.getConventionallyPrefixedLogMessage(
                        String.format("Unrecognized event: %s", action.name())));
        }
    }

    /**
     * Gets <i>just</i> new containers statuses by filtering the existing ones out.
     * 
     * @param before A list of {@link ContainerStatus} instances belonging to already existing containers
     * @param now A list of {@link ContainerStatus} instances belonging to new containers
     * @return A list of {@link ContainerStatus} instances representing the difference between those belonging to
     *         new containers and the existing ones, at a given moment in time.
     */
    private List<ContainerStatus> getNewContainers(final List<ContainerStatus> before, final List<ContainerStatus> now) {
        List<String> namesBefore = before.stream().map(cs -> cs.getContainerID()).collect(Collectors.toList());
        return now.stream()
                .filter(element -> !namesBefore.contains(element.getContainerID()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all the terminated statuses belonging to containers inside a Pod
     *
     * @param pod a {@link Pod} instance where to look for containers
     * @return A list of containers running statuses related to containers belonging to the {@link Pod} instance
     */
    private List<ContainerStatus> getTerminatedContainers(final Pod pod) {
        final List<ContainerStatus> containers = new ArrayList<>();
        containers.addAll(
                pod.getStatus().getInitContainerStatuses().stream().filter(
                        containerStatus -> containerStatus.getState().getTerminated() != null).collect(toList()));
        containers.addAll(
                pod.getStatus().getContainerStatuses().stream().filter(
                        containerStatus -> containerStatus.getState().getTerminated() != null).collect(toList()));
        return containers;
    }

    /**
     * Returns all the running statuses belonging to containers inside a Pod
     *
     * @param pod a {@link Pod} instance where to look for containers
     * @return A list of containers running statuses related to containers belonging to the {@link Pod} instance
     */
    private List<ContainerStatus> getRunningContainersStatuses(final Pod pod) {
        final List<ContainerStatus> statuses = new ArrayList<>();
        statuses.addAll(
                pod.getStatus().getInitContainerStatuses().stream().filter(
                        containerStatus -> containerStatus.getState().getRunning() != null).collect(toList()));
        statuses.addAll(
                pod.getStatus().getContainerStatuses().stream().filter(
                        containerStatus -> containerStatus.getState().getRunning() != null).collect(toList()));
        return statuses;
    }

    @Override
    public void onClose(WatcherException e) {
        logWatches.forEach(
                (s, logWatch) -> {
                    // the log watch must be closed so that internal resources are managed properly (e.g.:
                    // `Closeable` instances will be closed)
                    logWatch.close();
                });
        log.debug(ServiceLogUtils.getConventionallyPrefixedLogMessage("Terminating PodLogsWatcher"));
    }

    static class Builder {
        private KubernetesClient client;
        private String namespace;
        private PrintStream printStream;
        private Pattern filter;

        protected Builder withClient(final KubernetesClient client) {
            this.client = client;
            return this;
        }

        protected Builder inNamespace(final String namespace) {
            this.namespace = namespace;
            return this;
        }

        protected Builder outputTo(final PrintStream printStream) {
            this.printStream = printStream;
            return this;
        }

        protected Builder filter(final Pattern filter) {
            this.filter = filter;
            return this;
        }

        protected PodLogsWatcher build() {
            if (client == null) {
                throw new IllegalStateException("KubernetesClient must be specified!");
            }
            if (namespace == null) {
                throw new IllegalStateException("Namespace must be specified!");
            }
            if (printStream == null) {
                throw new IllegalStateException("PrintStream must be specified!");
            }

            return new PodLogsWatcher(client, namespace, printStream, filter);
        }
    }
}
