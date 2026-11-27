package cz.xtf.core.service.logs.streaming.k8s;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import cz.xtf.core.service.logs.streaming.ServiceLogs;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import lombok.extern.slf4j.Slf4j;

/**
 * Concrete implementation of {@link ServiceLogs} for the Cloud/k8s environment, i.e. it deals with services running
 * on Pods.
 *
 * Watches Pods on <b>multiple</b> namespaces and streams logs for all Pods containers to the selected
 * {@link PrintStream}.
 * Each log is prefixed with <code>namespace.pod-name.container-name</code> and colored differently.
 */
@Slf4j
public class PodLogs implements ServiceLogs {
    private KubernetesClient client;
    private List<String> namespaces;
    private PrintStream printStream;
    private Pattern filter;
    private List<Watch> watches;

    private PodLogs(KubernetesClient client, List<String> namespaces, PrintStream printStream, Pattern filter) {
        this.client = client;
        this.namespaces = namespaces;
        // The life cycle of the PrintStream instance that is being used must be handled in the outer scope, which is
        // not here.
        // Here it will be either passed (i.e. printStream != null) or set by default to System.out, which is created
        // somewhere else.
        // For this reason it is not altered at all.
        this.printStream = printStream == null ? System.out : printStream;
        this.filter = filter;
        this.watches = new ArrayList<>();
    }

    /**
     * Start watching Pods logs in all the specified namespaces
     */
    @Override
    public void start() {
        if (namespaces != null && !namespaces.isEmpty()) {
            for (String namespace : namespaces) {
                log.info(
                        "=============================================================================================================================");
                log.info(
                        "Service Logs Streaming (SLS) was started in order to stream logs belonging to all pods in the {} namespace",
                        namespace);
                log.info(
                        "=============================================================================================================================");
                Watch watch = client.pods().inNamespace(namespace).watch(
                        new PodLogsWatcher.Builder()
                                .withClient(client)
                                .inNamespace(namespace)
                                .outputTo(printStream)
                                .filter(filter)
                                .build());
                watches.add(watch);
            }
        }
    }

    /**
     * Stop watching Pods logs in all specified namespaces
     */
    @Override
    public void stop() {
        for (Watch watch : watches) {
            watch.close();
        }
    }

    public static class Builder {
        private KubernetesClient client;
        private List<String> namespaces;
        private PrintStream printStream;
        private Pattern filter;

        public Builder withClient(final KubernetesClient client) {
            this.client = client;
            return this;
        }

        public Builder inNamespaces(final List<String> namespaces) {
            this.namespaces = namespaces;
            return this;
        }

        /**
         * Gets a {@link PrintStream} instance that will be passed on and used by the {@link PodLogs} instance
         *
         * @param printStream A {@link PrintStream} instance which will be used for streaming the service logs.
         *        It is expected that the {@link PrintStream} instance life cycle will be handled
         *        at the outer scope level, in the context and by the component which created it.
         * @return A {@link Builder} instance with the {@code outputTo} property set to the given value.
         */
        public Builder outputTo(final PrintStream printStream) {
            this.printStream = printStream;
            return this;
        }

        public Builder filter(final String filter) {
            if (filter != null) {
                this.filter = Pattern.compile(filter);
            }
            return this;
        }

        public PodLogs build() {
            if (client == null) {
                throw new IllegalStateException("The Kubernetes client must be specified!");
            }
            if (namespaces == null) {
                throw new IllegalStateException("A list of namespaces must be specified!");
            }
            if (printStream == null) {
                throw new IllegalStateException("A target print stream must be specified!");
            }

            return new PodLogs(client, namespaces, printStream, filter);
        }
    }
}
