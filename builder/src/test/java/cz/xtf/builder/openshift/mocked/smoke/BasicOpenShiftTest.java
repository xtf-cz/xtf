package cz.xtf.builder.openshift.mocked.smoke;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import cz.xtf.builder.builders.ConfigMapBuilder;
import cz.xtf.builder.builders.DeploymentConfigBuilder;
import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;

public class BasicOpenShiftTest {

    private static final String TEST_RESOURCE_LABEL_NAME_APP = "app";
    private static final String TEST_RESOURCE_LABEL_VALUE_APP = "xtf-core-test-openshift-mocked-smoke";
    private final static Map<String, String> TEST_RESOURCE_LABELS = Collections.singletonMap(
            TEST_RESOURCE_LABEL_NAME_APP, TEST_RESOURCE_LABEL_VALUE_APP);

    private static final String TEST_CONFIGMAP_NAME = "test-configmap";
    private static final String TEST_DEPLOYMENT_NAME = "test-deployment";
    private static final String TEST_DEPLOYMENT_CONFIG_NAME = "test-deployment-config";
    private static final Integer TEST_DEPLOYMENT_CONFIG_REPLICAS = 3;

    private final OpenShiftServer openShiftServer;
    private final OpenShift openShiftClient;

    public BasicOpenShiftTest() {
        this.openShiftServer = new OpenShiftServer(false, true);
        this.openShiftServer.before();
        // we want to test the XTF OpenShift client, but we need to configure it with the mocked server client
        // properties
        OpenShiftClient mockedServerClient = openShiftServer.getOpenshiftClient();
        this.openShiftClient = OpenShift.get(
                mockedServerClient.getMasterUrl().toString(),
                mockedServerClient.getNamespace(),
                mockedServerClient.getConfiguration().getUsername(),
                mockedServerClient.getConfiguration().getPassword());
    }

    @Test
    public void testConfigMapBuilder() {
        // arrange
        final String dataEntryKey = "test.properties";
        final String dataEntryValue = "foo=bar";
        final ConfigMap configMap = new ConfigMapBuilder(TEST_CONFIGMAP_NAME)
                .addLabels(TEST_RESOURCE_LABELS)
                .configEntry(dataEntryKey, dataEntryValue)
                .build();
        // act
        openShiftClient.createResources(configMap);
        // assert
        List<ConfigMap> actualConfigMaps = openShiftClient.getConfigMaps();
        Assert.assertEquals(String.format("ConfigMap resource list has unexpected size: %d.",
                actualConfigMaps.size()), 1, actualConfigMaps.size());
        ConfigMap actualConfigMap = openShiftClient.getConfigMap(TEST_CONFIGMAP_NAME);
        Assert.assertNotNull("ConfigMap resource creation failed.", actualConfigMap);
        Assert.assertEquals("ConfigMap resource has unexpected name.",
                TEST_CONFIGMAP_NAME,
                actualConfigMap.getMetadata().getName());
        Assert.assertEquals(
                String.format("ConfigMap resource has unexpected data size: %d", actualConfigMap.getData().entrySet().size()),
                1,
                actualConfigMap.getData().entrySet().size());
        // safe now
        final Map.Entry<String, String> dataEntry = actualConfigMap.getData().entrySet().stream().findFirst().get();
        Assert.assertEquals(
                String.format("ConfigMap resource has unexpected data entry key: %s", dataEntry.getKey()),
                dataEntryKey,
                dataEntry.getKey());
        Assert.assertEquals(
                String.format("ConfigMap resource has unexpected data entry value: %s", dataEntry.getValue()),
                dataEntryValue,
                dataEntry.getValue());
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
        // act
        openShiftClient.createResources(deploymentConfig);
        // assert
        List<DeploymentConfig> actualResources = openShiftClient.deploymentConfigs()
                .withLabel(TEST_RESOURCE_LABEL_NAME_APP, TEST_RESOURCE_LABEL_VALUE_APP)
                .list()
                .getItems();
        Assert.assertEquals(
                String.format("DeploymentConfig resource list has unexpected size: %d.", actualResources.size()),
                1, actualResources.size());
        DeploymentConfig actualResource = actualResources.get(0);
        Assert.assertEquals(
                String.format("DeploymentConfig resource has unexpected name: %s.", actualResource.getMetadata().getName()),
                TEST_DEPLOYMENT_CONFIG_NAME, actualResource.getMetadata().getName());
        Assert.assertNotNull(String.format("DeploymentConfig resource has null \".spec\"", actualResource.getSpec()));
        Assert.assertEquals(
                String.format("DeploymentConfig resource has unexpected \".spec.replicas\": %s.",
                        actualResource.getSpec().getReplicas()),
                TEST_DEPLOYMENT_CONFIG_REPLICAS,
                actualResource.getSpec().getReplicas());
        Assert.assertNotNull(
                String.format("DeploymentConfig resource has null \".spec.strategy\"", actualResource.getSpec().getStrategy()));
        Assert.assertEquals(
                String.format("DeploymentConfig resource has unexpected \".spec.strategy\": %s.",
                        actualResource.getSpec().getStrategy().getType()),
                "Recreate",
                actualResource.getSpec().getStrategy().getType());
        Assert.assertNotNull(
                String.format("DeploymentConfig resource has null \".spec.template\"", actualResource.getSpec().getTemplate()));
        Assert.assertNotNull(String.format("DeploymentConfig resource has null \".spec.template.spec\"",
                actualResource.getSpec().getTemplate().getSpec()));
        PodSpec podSpec = actualResource.getSpec().getTemplate().getSpec();
        Assert.assertEquals(
                String.format("DeploymentConfig resource has unexpected \".spec.template.spec.containers\": %s.",
                        podSpec.getContainers().size()),
                0, podSpec.getContainers().size());
    }
}
