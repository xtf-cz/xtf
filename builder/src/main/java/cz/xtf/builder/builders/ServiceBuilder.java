package cz.xtf.builder.builders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.xtf.builder.builders.route.TransportProtocol;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import lombok.Getter;

public class ServiceBuilder extends AbstractBuilder<Service, ServiceBuilder> {
    private SessionAffinity sessionAffinity = SessionAffinity.None;
    private Map<String, String> selectors = new HashMap<>();
    private String clusterIP = null;
    private List<ServicePort> servicePorts = new ArrayList<>();
    private boolean isNodePort = false;
    private boolean isPublishNotReadyAddresses = false;

    public ServiceBuilder(String id) {
        this(null, id);
    }

    ServiceBuilder(ApplicationBuilder applicationBuilder, String id) {
        super(applicationBuilder, id);
    }

    public ServiceBuilder port(int targetPort) {
        return port(null, targetPort, targetPort, TransportProtocol.TCP);
    }

    public ServiceBuilder port(int targetPort, int port) {
        return port(null, targetPort, port, TransportProtocol.TCP);
    }

    public ServiceBuilder port(String name, int targetPort) {
        return port(name, targetPort, targetPort, TransportProtocol.TCP);
    }

    public ServiceBuilder port(String name, int targetPort, int port) {
        return port(name, targetPort, port, TransportProtocol.TCP);
    }

    public ServiceBuilder port(String name, int targetPort, int port, TransportProtocol protocol) {
        servicePorts.add(new ServicePort(name, targetPort, port, protocol));
        return this;
    }

    public ServiceBuilder ports(int... targetPorts) {
        Arrays.stream(targetPorts).forEach(p -> port("port-" + p, p));
        return this;
    }

    public ServiceBuilder clientIPStickiness() {
        this.sessionAffinity = SessionAffinity.ClientIP;
        return this;
    }

    public ServiceBuilder noStickiness() {
        this.sessionAffinity = SessionAffinity.None;
        return this;
    }

    public ServiceBuilder addContainerSelector(String key, String value) {
        selectors.put(key, value);
        return this;
    }

    public ServiceBuilder headless() {
        this.clusterIP = "None";
        return this;
    }

    public ServiceBuilder nodePort() {
        this.isNodePort = true;
        return this;
    }

    public ServiceBuilder withoutSelectors() {
        selectors.clear();
        return this;
    }

    public ServiceBuilder withPublishNotReadyAddresses() {
        this.isPublishNotReadyAddresses = true;
        return this;
    }

    @Override
    public Service build() {
        ServiceSpecBuilder spec = new ServiceSpecBuilder();

        servicePorts.forEach(sp -> spec.addToPorts(new ServicePortBuilder()
                .withName(sp.getName())
                .withProtocol(sp.getTransportProtocol().uppercase())
                .withPort(sp.getPort())
                .withNewTargetPort(sp.getTargetPort()).build()));

        spec.withSessionAffinity(sessionAffinity.toString());

        spec.withSelector(selectors);

        if (isPublishNotReadyAddresses) {
            spec.withPublishNotReadyAddresses(isPublishNotReadyAddresses);
        }

        if (clusterIP != null) {
            spec.withClusterIP(clusterIP);
        }

        if (this.isNodePort) {
            spec.withType("NodePort");
        }

        return new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withMetadata(metadataBuilder().build())
                .withSpec(spec.build())
                .build();
    }

    @Override
    protected ServiceBuilder getThis() {
        return this;
    }

    @Getter
    private class ServicePort {
        private String name;
        private int targetPort;
        private int port;
        private TransportProtocol transportProtocol;

        public ServicePort(String name, int targetPort, int port, TransportProtocol transportProtocol) {
            this.name = name;
            this.targetPort = targetPort;
            this.port = port;
            this.transportProtocol = transportProtocol;
        }
    }

    private enum SessionAffinity {
        None,
        ClientIP
    }
}
