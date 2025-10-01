package cz.xtf.builder.builders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.builder.builders.pod.ConfigMapVolume;
import cz.xtf.builder.builders.pod.ContainerBuilder;
import cz.xtf.builder.builders.pod.EmptyDirVolume;
import cz.xtf.builder.builders.pod.HostPathVolume;
import cz.xtf.builder.builders.pod.NFSVolume;
import cz.xtf.builder.builders.pod.PersistentVolumeClaim;
import cz.xtf.builder.builders.pod.SecretVolume;
import cz.xtf.builder.builders.pod.Volume;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;

public class PodBuilder extends AbstractBuilder<Pod, PodBuilder> {
    private final DeploymentConfigBuilder deploymentBuilder;
    private final Set<Volume> volumes = new HashSet<>();
    private final List<ContainerBuilder> containerBuilders = new ArrayList<>();
    private final List<ContainerBuilder> initContainerBuilders = new ArrayList<>();
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

    /**
     * @deprecated use container(String name) instead
     */
    @Deprecated
    public ContainerBuilder container() {
        return container(getName());
    }

    /**
     * Creates or retrieves a container with the specified name.
     */
    public ContainerBuilder container(String name) {
        return getOrCreateContainerBuilder(name);
    }

    /**
     * Creates or retrieves an init container with the specified name.
     */
    public ContainerBuilder initContainer(String name) {
        return getOrCreateInitContainerBuilder(name);
    }

    public Collection<ContainerBuilder> getContainers() {
        return Collections.unmodifiableList(containerBuilders);
    }

    public Collection<ContainerBuilder> getInitContainers() {
        return Collections.unmodifiableList(initContainerBuilders);
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

    public PodBuilder addConfigMapVolume(final String name, final String configMapName, final String defaultMode) {
        volumes.add(new ConfigMapVolume(name, configMapName, defaultMode));
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
        specBuilder
                .withInitContainers(initContainerBuilders.stream().map(ContainerBuilder::build).collect(Collectors.toList()));
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

    private ContainerBuilder getOrCreateContainerBuilder(String name) {
        return findOrCreateContainerBuilder(name, containerBuilders);
    }

    private ContainerBuilder getOrCreateInitContainerBuilder(String name) {
        return findOrCreateContainerBuilder(name, initContainerBuilders);
    }

    /**
     * Helper method to find an existing container builder by name or create a new one.
     *
     * @param name the name of the container builder to find or create
     * @param containerList the list of container builders to search in
     * @return the found or newly created container builder
     */
    private ContainerBuilder findOrCreateContainerBuilder(String name, List<ContainerBuilder> containerList) {
        Optional<ContainerBuilder> opt = containerList.stream()
                .filter(bldr -> bldr.getName().equals(name))
                .findFirst();

        if (opt.isPresent()) {
            return opt.get();
        } else {
            ContainerBuilder result = new ContainerBuilder(this, name);
            containerList.add(result);
            return result;
        }
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
