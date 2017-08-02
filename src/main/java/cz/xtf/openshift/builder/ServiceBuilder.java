package cz.xtf.openshift.builder;

import cz.xtf.model.TransportProtocol;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceBuilder extends AbstractBuilder<Service, ServiceBuilder> {
	private TransportProtocol protocol = TransportProtocol.TCP;
	private int port = 80;
	private int containerPort = 0;
	private SessionAffinity sessionAffinity = SessionAffinity.None;
	private Map<String, String> selectors = new HashMap<>();
	private String clusterIP = null;
	private List<ServicePort> servicePorts = new ArrayList<>();
	private boolean isNodePort = false;

	public ServiceBuilder(String id) {
		this(null, id);
	}

	ServiceBuilder(ApplicationBuilder applicationBuilder, String id) {
		super(applicationBuilder, id);
	}

	public ServiceBuilder useTCP() {
		protocol = TransportProtocol.TCP;
		return this;
	}

	public ServiceBuilder useUDP() {
		protocol = TransportProtocol.UDP;
		return this;
	}

	public ServiceBuilder setPort(int port) {
		this.port = port;
		return this;
	}

	public ServiceBuilder setContainerPort(int containerPort) {
		this.containerPort = containerPort;
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

	public ServiceBuilder ports(List<ServicePort> servicePorts) {
		this.servicePorts.addAll(servicePorts);
		return this;
	}

	public ServiceBuilder ports(ServicePort ...servicePorts) {
		ports(Arrays.asList(servicePorts));
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

	@Override
	public Service build() {
		ServiceSpecBuilder spec = new ServiceSpecBuilder();

		if (servicePorts.isEmpty()) {
			//keep this for backward compatibility with single port usage in testsuite
			if (containerPort == 0) {
				throw new IllegalStateException("containerPort must be set for service");
			}
			spec.withPorts(new ServicePortBuilder()
				.withProtocol(protocol.uppercase())
				.withPort(port)
				.withNewTargetPort(containerPort).build());
		} else {
			spec.withPorts(servicePorts);
		}

		spec.withSessionAffinity(sessionAffinity.toString());

		spec.withSelector(selectors);

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

	private enum SessionAffinity {
		None, ClientIP
	}
}
