package cz.xtf.builder.builders.pod;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

/**
 * Tests that each Volume subclass (new code using explicit source builders) produces the same
 * Kubernetes Volume object as the old code (fluent nested builders like withNewConfigMap()...endConfigMap()).
 */
public class VolumeBuilderTest {

    @Test
    public void testConfigMapVolume() {
        // old code: builder.withNewConfigMap().withName(configMapName).endConfigMap()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-cm")
                .withNewConfigMap()
                .withName("my-config")
                .endConfigMap()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new ConfigMapVolume("vol-cm", "my-config").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testConfigMapVolumeWithDefaultMode() {
        // 0755 octal = 493 decimal
        // old code: builder.withNewConfigMap().withName(configMapName).withDefaultMode(493).endConfigMap()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-cm2")
                .withNewConfigMap()
                .withName("my-config")
                .withDefaultMode(493)
                .endConfigMap()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new ConfigMapVolume("vol-cm2", "my-config", "0755").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testEmptyDirVolume() {
        // old code: builder.withNewEmptyDir().endEmptyDir()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-empty")
                .withNewEmptyDir()
                .endEmptyDir()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new EmptyDirVolume("vol-empty").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testHostPathVolume() {
        // old code: builder.withNewHostPath().withPath(path).endHostPath()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-host")
                .withNewHostPath()
                .withPath("/data/logs")
                .endHostPath()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new HostPathVolume("vol-host", "/data/logs").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testNFSVolume() {
        // old code: builder.withNewNfs().withServer(server).withPath(path).endNfs()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-nfs")
                .withNewNfs()
                .withServer("nfs.example.com")
                .withPath("/exports/data")
                .endNfs()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new NFSVolume("vol-nfs", "nfs.example.com", "/exports/data").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testPersistentVolumeClaim() {
        // old code: builder.withNewPersistentVolumeClaim().withClaimName(name).endPersistentVolumeClaim()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-pvc")
                .withNewPersistentVolumeClaim()
                .withClaimName("my-claim")
                .endPersistentVolumeClaim()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new PersistentVolumeClaim("vol-pvc", "my-claim").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testSecretVolume() {
        // old code: builder.withNewSecret().withSecretName(name).endSecret()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-secret")
                .withNewSecret()
                .withSecretName("my-secret")
                .endSecret()
                .build();

        // new code
        io.fabric8.kubernetes.api.model.Volume actual = new SecretVolume("vol-secret", "my-secret").build();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testSecretVolumeWithItems() {
        // old code: builder.withNewSecret().withSecretName(name).addNewItem().withKey(k).withPath(v).endItem()...endSecret()
        io.fabric8.kubernetes.api.model.Volume expected = new VolumeBuilder()
                .withName("vol-secret2")
                .withNewSecret()
                .withSecretName("my-secret")
                .addNewItem().withKey("username").withPath("config/user.txt").endItem()
                .addNewItem().withKey("password").withPath("config/pass.txt").endItem()
                .endSecret()
                .build();

        // new code
        Map<String, String> items = new LinkedHashMap<>();
        items.put("username", "config/user.txt");
        items.put("password", "config/pass.txt");
        io.fabric8.kubernetes.api.model.Volume actual = new SecretVolume("vol-secret2", "my-secret", items).build();

        Assertions.assertEquals(expected, actual);
    }
}
