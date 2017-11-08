package cz.xtf.openshift.builder.pod;

import cz.xtf.openshift.builder.EnvironmentConfiguration;
import cz.xtf.openshift.builder.PodBuilder;
import cz.xtf.openshift.builder.deployment.AbstractProbe;
import cz.xtf.openshift.builder.deployment.Handler;
import cz.xtf.openshift.builder.limits.MemoryResource;
import cz.xtf.openshift.builder.limits.ResourceLimitBuilder;
import org.apache.commons.lang3.StringUtils;

import cz.xtf.model.TransportProtocol;
import cz.xtf.openshift.builder.deployment.LivenessProbe;
import cz.xtf.openshift.builder.deployment.ReadinessProbe;
import cz.xtf.openshift.builder.limits.CPUResource;
import cz.xtf.openshift.builder.limits.ComputingResource;
import cz.xtf.tuple.Tuple;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerFluent.ResourcesNested;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;

public class ContainerBuilder implements EnvironmentConfiguration, ResourceLimitBuilder {
	private final PodBuilder pod;
	private final String name;

	private final Map<String, String> envVars = new HashMap<>();
	private final Map<String, Tuple.Pair<String, String>> referredEnvVars = new HashMap<>();
	private final Set<ContainerPort> ports = new HashSet<>();
	private final Set<VolumeMount> volumeMounts = new HashSet<>();
	private String imageName;
	private String imageNamespace;
	private boolean privileged = false;
	private AbstractProbe livenessProbe;
	private AbstractProbe readinessProbe;
	private Handler preStopHandler;
	private String[] command;

	private Map<String, ComputingResource> computingResources = new HashMap<>();

	public ContainerBuilder(PodBuilder podBuilder, String name) {
		if (podBuilder == null) {
			throw new IllegalArgumentException("PodBuilder must not be null");
		}
		if (StringUtils.isBlank(name)) {
			throw new IllegalArgumentException("Name must not be null nor empty");
		}
		this.pod = podBuilder;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public ContainerBuilder fromImage(String imageName) {
		this.imageName = imageName;
		return this;
	}

	public ContainerBuilder fromImage(String imageNamespace, String imageName) {
		this.imageNamespace = imageNamespace;
		this.imageName = imageName;
		return this;
	}

	public String getImageName() {
		return imageName;
	}

	public String getImageNamespace() {
		return imageNamespace;
	}

	public ContainerBuilder port(int port) {
		return port(port, null, null);
	}

	public ContainerBuilder port(int port, String name) {
		return port(port, null, name);
	}

	public ContainerBuilder port(int port, TransportProtocol protocol, String name) {
		ports.add(new ContainerPort(port, protocol, name));
		return this;
	}

	public ContainerBuilder envVar(String key, String value) {
		return configEntry(key, value);
	}

	public ContainerBuilder envVars(Map<String, String> vars) {
		return (ContainerBuilder)configEntries(vars);
	}

	public ContainerBuilder cleanEnvVars() {
		envVars.clear();
		return this;
	}

	public Map<String, String> getEnvVars() {
		return Collections.unmodifiableMap(envVars);
	}

	public ContainerBuilder privileged() {
		this.privileged = true;
		return this;
	}

	public ContainerBuilder addVolumeMount(String name, String mountPath, boolean readOnly) {
		this.volumeMounts.add(new VolumeMount(name, mountPath, readOnly));
		return this;
	}

	public LivenessProbe addLivenessProbe() {
		this.livenessProbe = new LivenessProbe();
		return (LivenessProbe) this.livenessProbe;
	}

	public ReadinessProbe addReadinessProbe() {
		this.readinessProbe = new ReadinessProbe();
		return (ReadinessProbe) this.readinessProbe;
	}

	public ContainerBuilder addReadinessProbe(final AbstractProbe readinessProbe) {
		this.readinessProbe = readinessProbe;
		return this;
	}

	public ContainerBuilder addCommand(String... cmd) {
		this.command = cmd;
		return this;
	}

	public PodBuilder pod() {
		return pod;
	}

	public Container build() {
		io.fabric8.kubernetes.api.model.ContainerBuilder builder = new io.fabric8.kubernetes.api.model.ContainerBuilder();

		Stream<EnvVar> definedVars = envVars.entrySet().stream().map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null));
		Stream<EnvVar> referredVars = referredEnvVars.entrySet().stream().map(entry -> new EnvVar(entry.getKey(), null, new EnvVarSource(new ConfigMapKeySelectorBuilder().withKey(entry.getValue().getSecond()).withName(entry.getValue().getFirst()).build(), null, null, null)));
		builder.withEnv(Stream.concat(definedVars, referredVars).collect(Collectors.toList()));
		builder.withImage(imageName);
		builder.withImagePullPolicy("Always");

		if (command != null) {
			builder.withCommand(command);
		}

		if (livenessProbe != null) {
			builder.withLivenessProbe(livenessProbe.build());
		}

		builder.withName(name);

		builder.withPorts(ports.stream().map(port -> {
			ContainerPortBuilder portBuilder = new ContainerPortBuilder();
			portBuilder.withContainerPort(port.getContainerPort());
			if (port.getProtocol() != null) {
				portBuilder.withProtocol(port.getProtocol().uppercase());
			}
			if (port.getName() != null) {
				portBuilder.withName(port.getName());
			}

			return portBuilder.build();
		}).collect(Collectors.toList()));

		if (preStopHandler != null) {
			builder.withNewLifecycle()
					.withPreStop(preStopHandler.build())
					.endLifecycle();
		}

		if (privileged) {
			builder.withNewSecurityContext().withPrivileged(true).endSecurityContext();
		}

		if (readinessProbe != null) {
			builder.withReadinessProbe(readinessProbe.build());
		}

		builder.withVolumeMounts(volumeMounts.stream().map(item -> new VolumeMountBuilder()
				.withName(item.getName())
				.withMountPath(item.getMountPath())
				.withReadOnly(item.isReadOnly())
				.build()).collect(Collectors.toList()));

		final List<ComputingResource> requests = computingResources.values().stream().filter(x -> x.getRequests() != null).collect(Collectors.toList());
		final List<ComputingResource> limits = computingResources.values().stream().filter(x -> x.getLimits() != null).collect(Collectors.toList());
		if (!requests.isEmpty() || !limits.isEmpty())
		{
			ResourcesNested<io.fabric8.kubernetes.api.model.ContainerBuilder> resources = builder.withNewResources();
			if (!requests.isEmpty()) {
				resources.withRequests(
						requests.stream().collect(Collectors.toMap(
								ComputingResource::resourceIdentifier, x -> new Quantity(x.getRequests())
						)));
			}
			if (!limits.isEmpty()) {
				resources.withLimits(
						limits.stream().collect(Collectors.toMap(
								ComputingResource::resourceIdentifier, x -> new Quantity(x.getLimits())
						)));
			}
			resources.endResources();
		}
		// args
		// capabilities
		// command
		// lifecycle
		// resources
		// securityContext
		// terminationMessagePath
		// workingDir
		return builder.build();
	}

	@Override
	public ComputingResource addCPUResource() {
		final ComputingResource r = new CPUResource();
		computingResources.put(r.resourceIdentifier(), r);
		return r;
	}

	@Override
	public ComputingResource addMemoryResource() {
		final ComputingResource r = new MemoryResource();
		computingResources.put(r.resourceIdentifier(), r);
		return r;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o.getClass() == getClass())) return false;

		ContainerBuilder that = (ContainerBuilder) o;

		return name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public void addPreStopHandler(final Handler handler) {
		preStopHandler = handler;
	}

	private static class VolumeMount {
		private String mountPath;
		private String name;
		private boolean readOnly;

		public VolumeMount(String name, String mountPath, boolean readOnly) {
			this.mountPath = mountPath;
			this.name = name;
			this.readOnly = readOnly;
		}

		public String getMountPath() {
			return mountPath;
		}

		public String getName() {
			return name;
		}

		public boolean isReadOnly() {
			return readOnly;
		}
	}

	private static class ContainerPort {
		private final int containerPort;
		private final TransportProtocol protocol;
		private String name = null;

		public ContainerPort(int containerPort, TransportProtocol protocol, String name) {
			if (containerPort < 1 || containerPort > 65538) {
				throw new IllegalArgumentException("Wrong port number");
			}
			this.containerPort = containerPort;
			this.protocol = protocol;
			this.name = name;
		}

		public int getContainerPort() {
			return containerPort;
		}

		public TransportProtocol getProtocol() {
			return protocol;
		}

		public String getName() {
			return name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof ContainerPort)) return false;

			ContainerPort that = (ContainerPort) o;

			if (containerPort != that.containerPort) return false;
			return protocol == that.protocol;

		}

		@Override
		public int hashCode() {
			int result = containerPort;
			result = 31 * result + (protocol != null ? protocol.hashCode() : 0);
			return result;
		}
	}

	@Override
	public ContainerBuilder configEntry(String key, String value) {
		envVars.put(key, value);
		return this;
	}

	@Override
	public Map<String, String> getConfigEntries() {
		return envVars;
	}

	public ContainerBuilder configFromConfigMap(final String configMapName, final Function<String, String> nameMapping, Collection<String> configMapKeys) {
		configMapKeys.stream().forEach(x -> referredEnvVars.put(nameMapping.apply(x), Tuple.pair(configMapName, x)));
		return this;
	}

	public ContainerBuilder configFromConfigMap(final String configMapName, final Function<String, String> nameMapping, String... configMapKeys) {
		return configFromConfigMap(configMapName, nameMapping, Arrays.asList(configMapKeys));
	}

	public ContainerBuilder configFromConfigMap(final String configMapName, Collection<String> configMapKeys) {
		configFromConfigMap(configMapName, Function.identity(), configMapKeys);
		return this;
	}

	public ContainerBuilder configFromConfigMap(final String configMapName, String... configMapKeys) {
		return configFromConfigMap(configMapName, Arrays.asList(configMapKeys));
	}
}