package cz.xtf.openshift.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import cz.xtf.model.TransportProtocol;
import cz.xtf.tuple.Tuple;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;

public class EndpointBuilder extends AbstractBuilder<Endpoints, EndpointBuilder> {
	private final List<String> endpointIPs = new ArrayList<>();
	private final List<Tuple.Pair<Integer, TransportProtocol>> ports = new ArrayList<>();

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

	public EndpointBuilder addPort(int port, TransportProtocol protocol) {
		ports.add(Tuple.pair(port, protocol));
		return this;
	}

	public EndpointBuilder addPort(int port) {
		return addPort(port, TransportProtocol.TCP);
	}

	@Override
	public Endpoints build() {
		if (endpointIPs.isEmpty() || ports.isEmpty()) {
			throw new IllegalStateException("IP list and port list must be non-empty");
		}

		EndpointSubsetBuilder subset = new EndpointSubsetBuilder()
				.withAddresses(endpointIPs.stream().map(ip ->
								new EndpointAddressBuilder()
										.withIp(ip).build()
				).collect(Collectors.toList()))
				.withPorts(ports.stream().map(port ->
								new EndpointPortBuilder()
										.withPort(port.getFirst())
										.withProtocol(port.getSecond().toString())
										.build()
				).collect(Collectors.toList()));

		return new io.fabric8.kubernetes.api.model.EndpointsBuilder()
				.withMetadata(metadataBuilder().build())
				.withSubsets(subset.build()).build();
	}

	@Override
	protected EndpointBuilder getThis() {
		return this;
	}

}
