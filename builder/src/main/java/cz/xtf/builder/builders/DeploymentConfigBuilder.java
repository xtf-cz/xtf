package cz.xtf.builder.builders;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicy;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicyBuilder;

public class DeploymentConfigBuilder extends AbstractBuilder<DeploymentConfig, DeploymentConfigBuilder> {
    public static final String SYNCHRONOUS_LABEL = "synchronousId";
    private int replicas = 1;
    private String strategy = "Recreate";
    private PodBuilder podBuilder;
    private boolean imageChangeTrigger = false;
    private boolean configurationChangeTrigger = false;
    private boolean manualTrigger = false;
    private int synchronousDeployment = -1;

    public DeploymentConfigBuilder(String name) {
        this(null, name);
    }

    DeploymentConfigBuilder(ApplicationBuilder applicationBuilder, String name) {
        super(applicationBuilder, name);
        podBuilder = new PodBuilder(this, name);
    }

    public PodBuilder podTemplate() {
        return podBuilder;
    }

    public DeploymentConfigBuilder setReplicas(int replicas) {
        this.replicas = replicas;
        return this;
    }

    public DeploymentConfigBuilder setRollingStrategy() {
        this.strategy = "Rolling";
        return this;
    }

    public DeploymentConfigBuilder setRecreateStrategy() {
        this.strategy = "Recreate";
        return this;
    }

    public DeploymentConfigBuilder onImageChange() {
        imageChangeTrigger = true;
        return this;
    }

    public DeploymentConfigBuilder onConfigurationChange() {
        configurationChangeTrigger = true;
        return this;
    }

    public DeploymentConfigBuilder onManualDeployment() {
        manualTrigger = true;
        return this;
    }

    @Override
    public DeploymentConfig build() {
        List<DeploymentTriggerPolicy> triggers = new LinkedList<>();
        if (imageChangeTrigger) {
            podBuilder.getContainers().stream().forEach(container -> {
                ObjectReferenceBuilder imageRef = new ObjectReferenceBuilder()
                        .withKind("ImageStreamTag")
                        .withName(container.getImageName() + ":latest");
                if (container.getImageNamespace() != null) {
                    imageRef.withNamespace(container.getImageNamespace());
                }

                triggers.add(new DeploymentTriggerPolicyBuilder()
                        .withType("ImageChange")
                        .withNewImageChangeParams()
                        .withAutomatic(true)
                        .withContainerNames(container.getName())
                        .withFrom(imageRef.build())
                        .endImageChangeParams()
                        .build());
            });
        }
        if (configurationChangeTrigger) {
            triggers.add(new DeploymentTriggerPolicyBuilder()
                    .withType("ConfigChange")
                    .build());
        }
        if (manualTrigger) {
            triggers.add(new DeploymentTriggerPolicyBuilder()
                    .withType("Manual")
                    .build());
        }
        if (synchronousDeployment >= 0) {
            final String synchronousId = Integer.toString(synchronousDeployment);
            addLabel(SYNCHRONOUS_LABEL, synchronousId);
            podBuilder.addLabel(SYNCHRONOUS_LABEL, synchronousId);
        }
        Pod pod = podBuilder.build();

        DeploymentConfigSpecBuilder spec = new DeploymentConfigSpecBuilder()
                .withTriggers(triggers)
                .withReplicas(replicas)
                .withSelector(Collections.singletonMap("name", podBuilder.getName()))
                .withNewStrategy().withType(strategy).endStrategy()
                .withNewTemplate()
                .withMetadata(pod.getMetadata())
                .withSpec(pod.getSpec())
                .endTemplate();

        return new io.fabric8.openshift.api.model.DeploymentConfigBuilder()
                .withMetadata(metadataBuilder().build())
                .withSpec(spec.build())
                .build();
    }

    public void synchronousDeployment() {
        if (synchronousDeployment < 0) {
            synchronousDeployment = 0;
        }
    }

    public void synchronousDeployment(final int sequenceNumber) {
        assert sequenceNumber >= 0 : "Negative sequence number given";
        synchronousDeployment = sequenceNumber;
    }

    @Override
    protected DeploymentConfigBuilder getThis() {
        return this;
    }

    public DeploymentConfigBuilder resetTriggers() {
        imageChangeTrigger = false;
        manualTrigger = false;
        configurationChangeTrigger = false;
        return this;
    }
}
