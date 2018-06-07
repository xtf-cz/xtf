package cz.xtf.openshift.builder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cz.xtf.openshift.builder.pod.ConfigMapVolume;
import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;
import cz.xtf.openshift.builder.pod.SecretVolume;
import org.apache.commons.lang3.StringUtils;

import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.builder.pod.EmptyDirVolume;
import cz.xtf.openshift.builder.pod.HostPathVolume;
import cz.xtf.openshift.builder.pod.NFSVolume;
import cz.xtf.openshift.builder.pod.Volume;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;

public class PodBuilder extends AbstractBuilder<Pod, PodBuilder> {
	private final DeploymentConfigBuilder deploymentBuilder;
	private final Set<Volume> volumes = new HashSet<>();
	private final Set<ContainerBuilder> containerBuilders = new HashSet<>();
	private final Map<String, String> nodeSelectorLabels = new HashMap<>();

	private int gracefulShutdown = -1;
	private String serviceAccount;
	private Long runAsUser;

	public PodBuilder(String name) {
		this(null, name);
	}

	PodBuilder(DeploymentConfigBuilder dcBuilder, String name) {
		super(extractApplicationBuilder(dcBuilder), name);
		this.deploymentBuilder = dcBuilder;
		addLabel("name", name);
	}

	public ContainerBuilder container() {
		return container(getName());
	}

	public Collection<ContainerBuilder> getContainers() {
		return Collections.unmodifiableSet(containerBuilders);
	}

	public ContainerBuilder container(String name) {
		return getContainerBuilder(name);
	}

	public PodBuilder gracefulShutdown(int seconds) {
		gracefulShutdown = seconds;
		return this;
	}

	public PodBuilder addHostPathVolume(String name, String sourceHostDirPath) {
		volumes.add(new HostPathVolume(name, sourceHostDirPath));
		return this;
	}

	public PodBuilder addSecretVolume(String name, String secretName) {
		volumes.add(new SecretVolume(name, secretName));
		return this;
	}

	public PodBuilder addSecretVolume(String name, String secretName, Map<String, String> items) {
		volumes.add(new SecretVolume(name, secretName, items));
		return this;
	}

	public PodBuilder addNFSVolume(String name, String server, String serverPath) {
		volumes.add(new NFSVolume(name, server, serverPath));
		return this;
	}

	public PodBuilder addEmptyDirVolume(String name) {
		volumes.add(new EmptyDirVolume(name));
		return this;
	}

	public PodBuilder addConfigMapVolume(final String name, final String configMapName) {
		volumes.add(new ConfigMapVolume(name, configMapName));
		return this;
	}

	public PodBuilder addPersistenVolumeClaim(String name, String claimName) {
		volumes.add(new PersistentVolumeClaim(name, claimName));
		return this;
	}

	public PodBuilder addServiceAccount(String serviceAccount) {
		this.serviceAccount = serviceAccount;
		return this;
	}

	public PodBuilder addRunAsUserSecurityContext(Long id) {
		this.runAsUser = id;
		return this;
	}

	public PodBuilder nodeSelector(final String key, final String value) {
		nodeSelectorLabels.put(key, value);
		return this;
	}

	@Override
	public Pod build() {
		PodSpecBuilder specBuilder = new PodSpecBuilder();

		specBuilder.withContainers(containerBuilders.stream().map(ContainerBuilder::build).collect(Collectors.toList()));
		specBuilder.withDnsPolicy("ClusterFirst");

		if (!nodeSelectorLabels.isEmpty()) {
			specBuilder.withNodeSelector(nodeSelectorLabels);
		}

		specBuilder.withRestartPolicy("Always");

		if (StringUtils.isNotBlank(serviceAccount)) {
			specBuilder.withServiceAccount(serviceAccount);
		}

		if (gracefulShutdown >= 0) {
			specBuilder.withTerminationGracePeriodSeconds((long) gracefulShutdown);
		}

		specBuilder.withVolumes(volumes.stream().map(Volume::build).collect(Collectors.toList()));

		if (runAsUser != null) {
			specBuilder.withNewSecurityContext()
					.withRunAsUser(runAsUser)
					.endSecurityContext();
		}

		return new io.fabric8.kubernetes.api.model.PodBuilder()
				.withMetadata(metadataBuilder().build())
				.withSpec(specBuilder.build())
				.build();
	}

	public DeploymentConfigBuilder deployment() {
		if (deploymentBuilder == null) {
			throw new IllegalStateException("DeploymentConfigBuilder was not set in constructor");
		}
		return deploymentBuilder;
	}

	@Override
	protected PodBuilder getThis() {
		return this;
	}

	private ContainerBuilder getContainerBuilder(String name) {
		ContainerBuilder result;
		Optional<ContainerBuilder> opt = containerBuilders.stream().filter(bldr -> bldr.getName().equals(name)).findFirst();
		if (opt.isPresent()) {
			//LOGGER
			result = opt.get();
		} else {
			result = new ContainerBuilder(this, name);
			containerBuilders.add(result);
		}

		return result;
	}

	private static ApplicationBuilder extractApplicationBuilder(DeploymentConfigBuilder dcBuilder) {
		if (dcBuilder == null) {
			return null;
		}

		try {
			return dcBuilder.app();
		} catch (IllegalStateException ex) {
			// ok, no application builder was assigned
			return null;
		}
	}

	public String getServiceAccount() {
		return serviceAccount;
	}
}
