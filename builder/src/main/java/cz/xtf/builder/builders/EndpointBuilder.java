package cz.xtf.builder.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import cz.xtf.builder.builders.route.TransportProtocol;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class EndpointBuilder extends AbstractBuilder<Endpoints, EndpointBuilder> {
    private final List<String> endpointIPs = new ArrayList<>();
    private final List<Port> ports = new ArrayList<>();

    public EndpointBuilder(String id) {
        this(null, id);
    }

    EndpointBuilder(ApplicationBuilder applicationBuilder, String id) {
        super(applicationBuilder, id);
    }

    public EndpointBuilder addIP(String ip) {
        endpointIPs.add(ip);
        return this;
    }

    public EndpointBuilder addPort(int port) {
        return addPort(port, TransportProtocol.TCP);
    }

    public EndpointBuilder addPort(int port, TransportProtocol protocol) {
        ports.add(new Port(port, protocol));
        return this;
    }

    @Override
    public Endpoints build() {
        if (endpointIPs.isEmpty() || ports.isEmpty()) {
            throw new IllegalStateException("IP list and port list must be non-empty");
        }

        EndpointSubsetBuilder subset = new EndpointSubsetBuilder()
                .withAddresses(endpointIPs.stream().map(ip -> new EndpointAddressBuilder()
                        .withIp(ip).build()).collect(Collectors.toList()))
                .withPorts(ports.stream().map(port -> new EndpointPortBuilder()
                        .withPort(port.getPort())
                        .withProtocol(port.getTransportProtocol().toString())
                        .build()).collect(Collectors.toList()));

        return new io.fabric8.kubernetes.api.model.EndpointsBuilder()
                .withMetadata(metadataBuilder().build())
                .withSubsets(subset.build()).build();
    }

    @Override
    protected EndpointBuilder getThis() {
        return this;
    }

    @Getter
    @AllArgsConstructor
    private class Port {
        private int port;
        private TransportProtocol transportProtocol;
    }
}
