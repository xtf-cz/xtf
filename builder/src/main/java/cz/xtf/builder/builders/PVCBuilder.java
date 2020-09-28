package cz.xtf.builder.builders;

import java.util.Collections;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;

public class PVCBuilder extends AbstractBuilder<PersistentVolumeClaim, PVCBuilder> {

    private AccessMode accessMode;
    private String storageRequest;

    public PVCBuilder(String name) {
        this(null, name);
    }

    PVCBuilder(ApplicationBuilder applicationBuilder, String name) {
        super(applicationBuilder, name);
    }

    public PVCBuilder accessRWO() {
        this.accessMode = AccessMode.ReadWriteOnce;
        return this;
    }

    public PVCBuilder accessRWX() {
        this.accessMode = AccessMode.ReadWriteMany;
        return this;
    }

    public PVCBuilder accessROX() {
        this.accessMode = AccessMode.ReadOnlyMany;
        return this;
    }

    public PVCBuilder storageSize(String storageRequest) {
        this.storageRequest = storageRequest;
        return this;
    }

    @Override
    public PersistentVolumeClaim build() {
        ObjectMetaBuilder meta = new ObjectMetaBuilder()
                .withName(getName());

        return new PersistentVolumeClaimBuilder()
                .withMetadata(metadataBuilder().build())

                // spec
                .withNewSpec()
                .withAccessModes(accessMode.toString())
                .withNewResources()
                .withRequests(Collections.singletonMap("storage", new Quantity(storageRequest)))
                .endResources()
                .endSpec()

                // build
                .build();
    }

    @Override
    protected PVCBuilder getThis() {
        return this;
    }

    private enum AccessMode {
        ReadWriteOnce,
        ReadWriteMany,
        ReadOnlyMany
    }
}
