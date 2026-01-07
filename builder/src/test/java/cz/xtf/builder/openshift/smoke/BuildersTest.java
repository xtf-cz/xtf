package cz.xtf.builder.openshift.smoke;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cz.xtf.builder.builders.ConfigMapBuilder;
import cz.xtf.builder.builders.DeploymentConfigBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.openshift.api.model.DeploymentConfig;

/**
 * Unit tests for XTF builder classes.
 * Tests verify that builders correctly construct Kubernetes/OpenShift resource objects.
 *
 * Replaces BasicOpenShiftTest which used OpenShiftServer mock (removed in Fabric8 7.4.0).
 */
public class BuildersTest {

    private static final String TEST_RESOURCE_LABEL_NAME_APP = "app";
    private static final String TEST_RESOURCE_LABEL_VALUE_APP = "xtf-core-test-openshift-mocked-smoke";
    private final static Map<String, String> TEST_RESOURCE_LABELS = Collections.singletonMap(
            TEST_RESOURCE_LABEL_NAME_APP, TEST_RESOURCE_LABEL_VALUE_APP);

    private static final String TEST_CONFIGMAP_NAME = "test-configmap";
    private static final String TEST_DEPLOYMENT_CONFIG_NAME = "test-deployment-config";
    private static final Integer TEST_DEPLOYMENT_CONFIG_REPLICAS = 3;

    @Test
    public void testConfigMapBuilder() {
        // arrange
        final String dataEntryKey = "test.properties";
        final String dataEntryValue = "foo=bar";
        final ConfigMap configMap = new ConfigMapBuilder(TEST_CONFIGMAP_NAME)
                .addLabels(TEST_RESOURCE_LABELS)
                .configEntry(dataEntryKey, dataEntryValue)
                .build();

        // assert - verify the built ConfigMap structure
        Assertions.assertNotNull(configMap, "ConfigMap resource creation failed.");
        Assertions.assertEquals(TEST_CONFIGMAP_NAME,
                configMap.getMetadata().getName(),
                "ConfigMap resource has unexpected name.");
        Assertions.assertEquals(1,
                configMap.getData().entrySet().size(),
                String.format("ConfigMap resource has unexpected data size: %d",
                        configMap.getData().entrySet().size()));

        // safe now
        final Map.Entry<String, String> dataEntry = configMap.getData().entrySet().stream().findFirst().get();
        Assertions.assertEquals(dataEntryKey,
                dataEntry.getKey(),
                String.format("ConfigMap resource has unexpected data entry key: %s", dataEntry.getKey()));
        Assertions.assertEquals(dataEntryValue,
                dataEntry.getValue(),
                String.format("ConfigMap resource has unexpected data entry value: %s", dataEntry.getValue()));
    }

    @Test
    public void testDeploymentConfigBuilder() {
        // arrange
        final DeploymentConfig deploymentConfig = new DeploymentConfigBuilder(TEST_DEPLOYMENT_CONFIG_NAME)
                .addLabels(TEST_RESOURCE_LABELS)
                .setReplicas(TEST_DEPLOYMENT_CONFIG_REPLICAS)
                .onImageChange()
                .setRecreateStrategy()
                .build();

        // assert - verify the built DeploymentConfig structure
        Assertions.assertNotNull(deploymentConfig, "DeploymentConfig resource creation failed.");
        Assertions.assertEquals(TEST_DEPLOYMENT_CONFIG_NAME,
                deploymentConfig.getMetadata().getName(),
                String.format("DeploymentConfig resource has unexpected name: %s.",
                        deploymentConfig.getMetadata().getName()));
        Assertions.assertEquals(TEST_RESOURCE_LABELS,
                deploymentConfig.getMetadata().getLabels(),
                "DeploymentConfig resource has unexpected labels.");
        Assertions.assertNotNull(deploymentConfig.getSpec(),
                String.format("DeploymentConfig resource has null \".spec\"", deploymentConfig.getSpec()));
        Assertions.assertEquals(TEST_DEPLOYMENT_CONFIG_REPLICAS,
                deploymentConfig.getSpec().getReplicas(),
                String.format("DeploymentConfig resource has unexpected \".spec.replicas\": %s.",
                        deploymentConfig.getSpec().getReplicas()));
        Assertions.assertNotNull(deploymentConfig.getSpec().getStrategy(),
                String.format("DeploymentConfig resource has null \".spec.strategy\"",
                        deploymentConfig.getSpec().getStrategy()));
        Assertions.assertEquals("Recreate",
                deploymentConfig.getSpec().getStrategy().getType(),
                String.format("DeploymentConfig resource has unexpected \".spec.strategy\": %s.",
                        deploymentConfig.getSpec().getStrategy().getType()));
        Assertions.assertNotNull(deploymentConfig.getSpec().getTemplate(),
                String.format("DeploymentConfig resource has null \".spec.template\"",
                        deploymentConfig.getSpec().getTemplate()));
        Assertions.assertNotNull(deploymentConfig.getSpec().getTemplate().getSpec(),
                String.format("DeploymentConfig resource has null \".spec.template.spec\"",
                        deploymentConfig.getSpec().getTemplate().getSpec()));
        PodSpec podSpec = deploymentConfig.getSpec().getTemplate().getSpec();
        Assertions.assertEquals(0,
                podSpec.getContainers().size(),
                String.format("DeploymentConfig resource has unexpected \".spec.template.spec.containers\": %s.",
                        podSpec.getContainers().size()));
    }
}
