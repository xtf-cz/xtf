package cz.xtf.builder.builders;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;

public class ServiceAccountBuilder extends AbstractBuilder<ServiceAccount, ServiceAccountBuilder> {
    private final List<String> secrets = new LinkedList<>();

    public ServiceAccountBuilder(String name) {
        this(null, name);
    }

    ServiceAccountBuilder(ApplicationBuilder applicationBuilder, String name) {
        super(applicationBuilder, name);
    }

    public ServiceAccountBuilder addSecret(String name) {
        secrets.add(name);
        return this;
    }

    public ServiceAccountBuilder addSecret(Secret secret) {
        secrets.add(secret.getMetadata().getName());
        return this;
    }

    @Override
    public ServiceAccount build() {
        return new io.fabric8.kubernetes.api.model.ServiceAccountBuilder()
                .withMetadata(metadataBuilder().build())
                .withSecrets(secrets.stream().map(secret -> new ObjectReferenceBuilder()
                        .withName(secret)
                        .build()).collect(Collectors.toList()))
                .build();
    }

    @Override
    protected ServiceAccountBuilder getThis() {
        return this;
    }

}
