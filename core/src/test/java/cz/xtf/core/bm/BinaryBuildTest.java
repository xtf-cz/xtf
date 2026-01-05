package cz.xtf.core.bm;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigList;
import io.fabric8.openshift.api.model.BuildStatusBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.ImageStreamList;
import io.fabric8.openshift.client.dsl.BuildConfigResource;

/**
 * Tests for BinaryBuild class, specifically testing build status handling.
 */
@ExtendWith(MockitoExtension.class)
public class BinaryBuildTest {

    private static final String TEST_BUILD_ID = "test-binary-build";
    private static final String TEST_BUILDER_IMAGE = "registry.access.redhat.com/ubi8/openjdk-11:latest";

    @Mock
    private OpenShift openShift;

    @Mock
    private MixedOperation<BuildConfig, BuildConfigList, BuildConfigResource<BuildConfig, Void, Build>> buildConfigOp;

    @Mock
    private MixedOperation<ImageStream, ImageStreamList, Resource<ImageStream>> imageStreamOp;

    private Path tempFile;
    private BinaryBuildFromFile binaryBuild;

    @BeforeEach
    public void setup() throws IOException {
        // Setup fluent API chains
        when(openShift.buildConfigs()).thenReturn(buildConfigOp);
        when(openShift.imageStreams()).thenReturn(imageStreamOp);

        // Create a temporary test file for BinaryBuildFromFile
        tempFile = Files.createTempFile("test", ".war");
        Files.write(tempFile, "test content".getBytes());

        // Create BinaryBuild instance
        binaryBuild = new BinaryBuildFromFile(TEST_BUILDER_IMAGE, tempFile, null, TEST_BUILD_ID);
    }

    @AfterEach
    public void cleanup() throws IOException {
        if (tempFile != null && Files.exists(tempFile)) {
            Files.delete(tempFile);
        }
    }

    @Test
    public void testNeedsUpdate_WhenBuildStatusIsError_ShouldReturnTrue() {
        // Given: BuildConfig and ImageStream exist with a build in "Error" status
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfigWithContentHash(TEST_BUILD_ID, 1);
        Build build = createBuildWithStatus(TEST_BUILD_ID + "-1", "Error");

        setupResourceMocks(buildConfig, imageStream);

        // lenient() needed because getBuild() is only called if previous checks pass (conditional execution),
        // and Mockito's strict stubbing sees this as potentially unused stubbing
        lenient().when(openShift.getBuild(TEST_BUILD_ID + "-1")).thenReturn(build);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because build is in Error status
        Assertions.assertTrue(needsUpdate,
                "Build with 'Error' status should trigger needsUpdate=true");

        verify(openShift).getBuild(TEST_BUILD_ID + "-1");
    }

    @Test
    public void testNeedsUpdate_WhenBuildStatusIsFailed_ShouldReturnTrue() {
        // Given: BuildConfig and ImageStream exist with a build in "Failed" status
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfigWithContentHash(TEST_BUILD_ID, 1);
        Build build = createBuildWithStatus(TEST_BUILD_ID + "-1", "Failed");

        setupResourceMocks(buildConfig, imageStream);

        // lenient() needed because getBuild() is only called if previous checks pass (conditional execution),
        // and Mockito's strict stubbing sees this as potentially unused stubbing
        lenient().when(openShift.getBuild(TEST_BUILD_ID + "-1")).thenReturn(build);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because build is in Failed status
        Assertions.assertTrue(needsUpdate,
                "Build with 'Failed' status should trigger needsUpdate=true");

        verify(openShift).getBuild(TEST_BUILD_ID + "-1");
    }

    @Test
    public void testNeedsUpdate_WhenBuildStatusIsComplete_ShouldReturnFalse() {
        // Given: BuildConfig and ImageStream exist with a build in "Complete" status
        ImageStream imageStream = createImageStream(TEST_BUILD_ID);
        BuildConfig buildConfig = createBuildConfigWithContentHash(TEST_BUILD_ID, 1);
        Build build = createBuildWithStatus(TEST_BUILD_ID + "-1", "Complete");

        setupResourceMocks(buildConfig, imageStream);

        // lenient() needed because getBuild() is only called if previous checks pass (conditional execution),
        // and Mockito's strict stubbing sees this as potentially unused stubbing
        lenient().when(openShift.getBuild(TEST_BUILD_ID + "-1")).thenReturn(build);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return false because build is successful
        Assertions.assertFalse(needsUpdate,
                "Build with 'Complete' status should trigger needsUpdate=false");

        verify(openShift).getBuild(TEST_BUILD_ID + "-1");
    }

    @Test
    public void testNeedsUpdate_WhenNoBuildConfigExists_ShouldReturnTrue() {
        // Given: No BuildConfig or ImageStream exists
        setupResourceMocks(null, null);

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
        BuildConfig buildConfig = createBuildConfigWithContentHash(TEST_BUILD_ID, 1);

        setupResourceMocks(buildConfig, imageStream);
        // Intentionally not creating the Build
        lenient().when(openShift.getBuild(TEST_BUILD_ID + "-1")).thenReturn(null);

        // When: Checking if build needs update
        boolean needsUpdate = binaryBuild.needsUpdate(openShift);

        // Then: Should return true because build doesn't exist
        Assertions.assertTrue(needsUpdate,
                "Missing Build should trigger needsUpdate=true");

        verify(openShift).getBuild(TEST_BUILD_ID + "-1");
    }

    // Helper methods to create test resources

    private ImageStream createImageStream(String name) {
        return new ImageStreamBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .build())
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

    private void setupResourceMocks(BuildConfig buildConfig, ImageStream imageStream) {
        // Mock the fluent API chains properly
        @SuppressWarnings("unchecked")
        BuildConfigResource<BuildConfig, Void, Build> buildConfigResource = mock(BuildConfigResource.class);
        @SuppressWarnings("unchecked")
        Resource<ImageStream> imageStreamResource = mock(Resource.class);

        // Tell mocks what to return - chain each method call
        when(buildConfigOp.withName(TEST_BUILD_ID)).thenReturn(buildConfigResource);
        when(buildConfigResource.get()).thenReturn(buildConfig);

        when(imageStreamOp.withName(TEST_BUILD_ID)).thenReturn(imageStreamResource);
        when(imageStreamResource.get()).thenReturn(imageStream);
    }
}
