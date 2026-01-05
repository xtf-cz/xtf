package cz.xtf.core.bm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.XTFConfig;
import io.fabric8.openshift.api.model.BuildConfig;

public class BinaryBuildMemoryTest {

    private static final String TEST_BUILD_ID = "test-binary-build";
    private static final String TEST_BUILDER_IMAGE = "registry.access.redhat.com/ubi8/openjdk-11:latest";

    @BeforeEach
    public void cleanSystemProperties() {
        // Safety net: ensure clean state before each test
        // Each test also handles its own cleanup in finally blocks
        System.clearProperty(BuildManagerConfig.MEMORY_REQUEST);
        System.clearProperty(BuildManagerConfig.MEMORY_LIMIT);
        XTFConfig.loadConfig();
    }

    @Test
    public void testBuildConfig_WhenMemoryLimitsSet_ShouldContainResourceRequirements() throws IOException {
        // Given: System properties configured for memory request and limit
        String memoryRequest = "512Mi";
        String memoryLimit = "2Gi";

        try {
            // Set properties inside try block to ensure cleanup via finally
            System.setProperty(BuildManagerConfig.MEMORY_REQUEST, memoryRequest);
            System.setProperty(BuildManagerConfig.MEMORY_LIMIT, memoryLimit);
            XTFConfig.loadConfig();

            Path tempFileWithLimits = Files.createTempFile("test-with-limits", ".war");
            Files.write(tempFileWithLimits, "test content".getBytes());

            BinaryBuildFromFile buildWithLimits = new BinaryBuildFromFile(
                    TEST_BUILDER_IMAGE,
                    tempFileWithLimits,
                    null,
                    TEST_BUILD_ID + "-with-limits");

            // When: Getting the BuildConfig
            BuildConfig buildConfig = buildWithLimits.bc;

            // Then: BuildConfig should contain resource requirements
            Assertions.assertNotNull(buildConfig.getSpec().getResources(),
                    "BuildConfig should have resources set when memory limits are configured");

            Assertions.assertNotNull(buildConfig.getSpec().getResources().getRequests(),
                    "BuildConfig should have resource requests");
            Assertions.assertEquals(memoryRequest,
                    buildConfig.getSpec().getResources().getRequests().get("memory").toString(),
                    "Memory request should match configured value");

            Assertions.assertNotNull(buildConfig.getSpec().getResources().getLimits(),
                    "BuildConfig should have resource limits");
            Assertions.assertEquals(memoryLimit,
                    buildConfig.getSpec().getResources().getLimits().get("memory").toString(),
                    "Memory limit should match configured value");

            Files.delete(tempFileWithLimits);
        } finally {
            // Cleanup always runs, even if property setting or test fails
            System.clearProperty(BuildManagerConfig.MEMORY_REQUEST);
            System.clearProperty(BuildManagerConfig.MEMORY_LIMIT);
            XTFConfig.loadConfig();
        }
    }

    @Test
    public void testBuildConfig_WhenOnlyMemoryRequestSet_ShouldContainOnlyRequest() throws IOException {
        // Given: System property configured for memory request only
        String memoryRequest = "256Mi";

        try {
            // Set properties inside try block to ensure cleanup via finally
            System.setProperty(BuildManagerConfig.MEMORY_REQUEST, memoryRequest);
            XTFConfig.loadConfig();

            Path tempFileWithRequest = Files.createTempFile("test-with-request", ".war");
            Files.write(tempFileWithRequest, "test content".getBytes());

            BinaryBuildFromFile buildWithRequest = new BinaryBuildFromFile(
                    TEST_BUILDER_IMAGE,
                    tempFileWithRequest,
                    null,
                    TEST_BUILD_ID + "-with-request");

            // When: Getting the BuildConfig
            BuildConfig buildConfig = buildWithRequest.bc;

            // Then: BuildConfig should contain only resource requests
            Assertions.assertNotNull(buildConfig.getSpec().getResources(),
                    "BuildConfig should have resources set when memory request is configured");

            Assertions.assertNotNull(buildConfig.getSpec().getResources().getRequests(),
                    "BuildConfig should have resource requests");
            Assertions.assertEquals(memoryRequest,
                    buildConfig.getSpec().getResources().getRequests().get("memory").toString(),
                    "Memory request should match configured value");

            Assertions.assertTrue(buildConfig.getSpec().getResources().getLimits() == null
                    || buildConfig.getSpec().getResources().getLimits().isEmpty(),
                    "BuildConfig should not have limits when only request is set");

            Files.delete(tempFileWithRequest);
        } finally {
            // Cleanup always runs, even if property setting or test fails
            System.clearProperty(BuildManagerConfig.MEMORY_REQUEST);
            XTFConfig.loadConfig();
        }
    }

    @Test
    public void testBuildConfig_WhenOnlyMemoryLimitSet_ShouldContainOnlyLimit() throws IOException {
        // Given: System property configured for memory limit only
        String memoryLimit = "1Gi";

        try {
            // Set properties inside try block to ensure cleanup via finally
            System.setProperty(BuildManagerConfig.MEMORY_LIMIT, memoryLimit);
            XTFConfig.loadConfig();

            Path tempFileWithLimit = Files.createTempFile("test-with-limit", ".war");
            Files.write(tempFileWithLimit, "test content".getBytes());

            BinaryBuildFromFile buildWithLimit = new BinaryBuildFromFile(
                    TEST_BUILDER_IMAGE,
                    tempFileWithLimit,
                    null,
                    TEST_BUILD_ID + "-with-limit");

            // When: Getting the BuildConfig
            BuildConfig buildConfig = buildWithLimit.bc;

            // Then: BuildConfig should contain only resource limits
            Assertions.assertNotNull(buildConfig.getSpec().getResources(),
                    "BuildConfig should have resources set when memory limit is configured");

            Assertions.assertTrue(buildConfig.getSpec().getResources().getRequests() == null
                    || buildConfig.getSpec().getResources().getRequests().isEmpty(),
                    "BuildConfig should not have requests when only limit is set");

            Assertions.assertNotNull(buildConfig.getSpec().getResources().getLimits(),
                    "BuildConfig should have resource limits");
            Assertions.assertEquals(memoryLimit,
                    buildConfig.getSpec().getResources().getLimits().get("memory").toString(),
                    "Memory limit should match configured value");

            Files.delete(tempFileWithLimit);
        } finally {
            // Cleanup always runs, even if property setting or test fails
            System.clearProperty(BuildManagerConfig.MEMORY_LIMIT);
            XTFConfig.loadConfig();
        }
    }

    @Test
    public void testBuildConfig_WhenMemoryLimitsNotSet_ShouldNotContainResources() throws IOException {
        // Given: BinaryBuild without memory configuration
        // Create a temporary test file for BinaryBuildFromFile
        Path tempFile = Files.createTempFile("test", ".war");
        Files.write(tempFile, "test content".getBytes());

        try {
            // Create BinaryBuild instance
            BinaryBuildFromFile binaryBuild = new BinaryBuildFromFile(TEST_BUILDER_IMAGE, tempFile, null, TEST_BUILD_ID);

            // When: Getting the BuildConfig
            BuildConfig buildConfig = binaryBuild.bc;

            // Then: BuildConfig should not contain resource requirements
            Assertions.assertNull(buildConfig.getSpec().getResources(),
                    "BuildConfig should not have resources when memory limits are not configured");
        } finally {
            Files.delete(tempFile);
        }
    }
}
