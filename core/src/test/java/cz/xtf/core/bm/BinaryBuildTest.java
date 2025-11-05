package cz.xtf.core.bm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildStatusBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;

/**
 * Tests for BinaryBuild class, specifically testing build status handling.
 */
public class BinaryBuildTest {

    private static final String TEST_BUILD_ID = "test-binary-build";
    private static final String TEST_BUILDER_IMAGE = "registry.access.redhat.com/ubi8/openjdk-11:latest";

    private OpenShiftServer openShiftServer;
    private OpenShift openShift;
    private Path tempFile;
    private BinaryBuildFromFile binaryBuild;

    @BeforeEach
    public void setup() throws IOException {
        // Initialize OpenShift mock server
        this.openShiftServer = new OpenShiftServer(false, true);
        this.openShiftServer.before();

        // Create XTF OpenShift client from mocked server
        OpenShiftClient mockedServerClient = openShiftServer.getOpenshiftClient();
        this.openShift = OpenShift.get(
                mockedServerClient.getMasterUrl().toString(),
                mockedServerClient.getNamespace(),
                mockedServerClient.getConfiguration().getUsername(),
                mockedServerClient.getConfiguration().getPassword());

        // Create a temporary test file for BinaryBuildFromFile
        tempFile = Files.createTempFile("test", ".war");
        Files.write(tempFile, "test content".getBytes());

        // Create BinaryBuild instance
        binaryBuild = new BinaryBuildFromFile(TEST_BUILDER_IMAGE, tempFile, null, TEST_BUILD_ID);
    }

    @AfterEach
    public void cleanup() throws IOException {
        if (openShiftServer != null) {
            openShiftServer.after();
        }
        if (tempFile != null && Files.exists(tempFile)) {
            Files.delete(tempFile);
        }
    }

    @Test
    public void testNeedsUpdate_WhenBuildStatusIsError_ShouldReturnTrue() {
        // Given: BuildConfig and ImageStream exist with a build in "Error" status
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfig(TEST_BUILD_ID, 1);
        Build build = createBuildWithStatus(TEST_BUILD_ID + "-1", "Error");

        openShift.imageStreams().create(imageStream);
        openShift.buildConfigs().create(buildConfig);
        openShift.builds().create(build);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because build is in Error status
        Assertions.assertTrue(needsUpdate,
                "Build with 'Error' status should trigger needsUpdate=true");
    }

    @Test
    public void testNeedsUpdate_WhenBuildStatusIsFailed_ShouldReturnTrue() {
        // Given: BuildConfig and ImageStream exist with a build in "Failed" status
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfig(TEST_BUILD_ID, 1);
        Build build = createBuildWithStatus(TEST_BUILD_ID + "-1", "Failed");

        openShift.imageStreams().create(imageStream);
        openShift.buildConfigs().create(buildConfig);
        openShift.builds().create(build);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because build is in Failed status
        Assertions.assertTrue(needsUpdate,
                "Build with 'Failed' status should trigger needsUpdate=true");
    }

    @Test
    public void testNeedsUpdate_WhenBuildStatusIsComplete_ShouldReturnFalse() {
        // Given: BuildConfig and ImageStream exist with a build in "Complete" status
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfigWithContentHash(TEST_BUILD_ID, 1);
        Build build = createBuildWithStatus(TEST_BUILD_ID + "-1", "Complete");

        openShift.imageStreams().create(imageStream);
        openShift.buildConfigs().create(buildConfig);
        openShift.builds().create(build);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return false because build is successful
        Assertions.assertFalse(needsUpdate,
                "Build with 'Complete' status should trigger needsUpdate=false");
    }

    @Test
    public void testNeedsUpdate_WhenNoBuildConfigExists_ShouldReturnTrue() {
        // Given: No BuildConfig or ImageStream exists

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because resources don't exist
        Assertions.assertTrue(needsUpdate,
                "Missing BuildConfig should trigger needsUpdate=true");
    }

    @Test
    public void testNeedsUpdate_WhenBuildIsNull_ShouldReturnTrue() {
        // Given: BuildConfig exists but no Build
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfig(TEST_BUILD_ID, 1);

        openShift.imageStreams().create(imageStream);
        openShift.buildConfigs().create(buildConfig);
        // Intentionally not creating the Build

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because build doesn't exist
        Assertions.assertTrue(needsUpdate,
                "Missing Build should trigger needsUpdate=true");
    }

    // Helper methods to create test resources

    private ImageStream createImageStream(String name) {
        return new ImageStreamBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .build())
                .build();
    }

    private BuildConfig createBuildConfig(String name, long lastVersion) {
        return new BuildConfigBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .addToLabels("xtf.bm/content-hash", "differenthash")
                        .build())
                .withNewSpec()
                .withNewStrategy()
                .withType("Source")
                .withNewSourceStrategy()
                .withForcePull(true)
                .withNewFrom()
                .withKind("DockerImage")
                .withName(TEST_BUILDER_IMAGE)
                .endFrom()
                .endSourceStrategy()
                .endStrategy()
                .endSpec()
                .withNewStatus()
                .withLastVersion(lastVersion)
                .endStatus()
                .build();
    }

    private BuildConfig createBuildConfigWithContentHash(String name, long lastVersion) {
        // Get the actual content hash from the BinaryBuild
        String contentHash = binaryBuild.getContentHash();

        return new BuildConfigBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .addToLabels("xtf.bm/content-hash", contentHash)
                        .build())
                .withNewSpec()
                .withNewStrategy()
                .withType("Source")
                .withNewSourceStrategy()
                .withForcePull(true)
                .withNewFrom()
                .withKind("DockerImage")
                .withName(TEST_BUILDER_IMAGE)
                .endFrom()
                .endSourceStrategy()
                .endStrategy()
                .endSpec()
                .withNewStatus()
                .withLastVersion(lastVersion)
                .endStatus()
                .build();
    }

    private Build createBuildWithStatus(String name, String phase) {
        return new BuildBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .build())
                .withStatus(new BuildStatusBuilder()
                        .withPhase(phase)
                        .build())
                .build();
    }
}
