package cz.xtf.products;

import cz.xtf.openshift.builder.pod.ContainerBuilder;

import java.util.function.Function;

public enum ProductDetails {
	EAP_BASED((builder) -> {
		builder
				.addLivenessProbe().createExecProbe("/bin/bash", "-c", "/opt/eap/bin/livenessProbe.sh");
		builder
				.addReadinessProbe().createExecProbe("/bin/bash", "-c", "/opt/eap/bin/readinessProbe.sh");
		return builder;
	}, "/opt/eap/jboss-modules.jar"), 
	JWS((builder) -> {
		builder
				.envVar("JWS_ADMIN_USERNAME", "admin")
				.envVar("JWS_ADMIN_PASSWORD", "jwsxtf123")
				.addReadinessProbe()
				.createExecProbe("/bin/bash", "-c", "curl --noproxy '*' -s -u ${JWS_ADMIN_USERNAME}:${JWS_ADMIN_PASSWORD} 'http://localhost:8080/manager/jmxproxy/?get=Catalina%3Atype%3DServer&att=stateName' |grep -iq 'stateName *= *STARTED'");
		return builder;
	}, "org.apache.catalina.startup.Bootstrap"), 
	AMQ((builder) -> {
		builder
				.addReadinessProbe().createExecProbe("/bin/bash", "-c", "/opt/amq/bin/readinessProbe.sh");
		return builder;
	}, "/opt/amq/bin/activemq.jar"), 
	JDG((builder) -> {
		builder
				.addLivenessProbe().createExecProbe("/bin/bash", "-c", "/opt/datagrid/bin/livenessProbe.sh");
		builder
				.addReadinessProbe().createExecProbe("/bin/bash", "-c", "/opt/datagrid/bin/readinessProbe.sh");
		return builder;
	}, "/opt/eap/jboss-modules.jar"),
	MSA_SPRINGBOOT((builder) -> {
		builder
				.addLivenessProbe().createHttpProbe("/health", "8080").setInitialDelay(60);
		builder
				.addReadinessProbe().createHttpProbe("/health", "8080").setInitialDelaySeconds(10);
		return builder;
	}, "org.apache.catalina.startup.Bootstrap");

	private final Function<ContainerBuilder, ContainerBuilder> probes;
	private final String mainClass;

	private ProductDetails(final Function<ContainerBuilder, ContainerBuilder> probes, final String mainClass) {
		this.probes = probes;
		this.mainClass = mainClass;
	}

	public ContainerBuilder probes(final ContainerBuilder builder) {
		return probes.apply(builder);
	}

	public String getMainClass() {
		return mainClass;
	}
}
