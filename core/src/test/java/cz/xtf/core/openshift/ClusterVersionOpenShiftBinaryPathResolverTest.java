package cz.xtf.core.openshift;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.config.XTFConfig;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class ClusterVersionOpenShiftBinaryPathResolverTest {

    @SystemStub
    private SystemProperties systemProperties;

    private OpenShiftBinaryPathResolver resolver = new ClusterVersionOpenShiftBinaryPathResolver();

    private static Stream<Arguments> provideVersions() {
        // tested version String
        // base client URL
        // version or channel URL
        return Stream.of(
                Arguments.arguments("4.10", "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/", "stable-4.10"),
                Arguments.arguments("4.8.14", "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/", "4.8.14"),
                Arguments.arguments("4.14.0-ec.1",
                        "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp-dev-preview/", "4.14.0-ec.1"),
                Arguments.arguments("4.13.0-rc.8",
                        "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/", "4.13.0-rc.8"));
    }

    @ParameterizedTest
    @MethodSource("provideVersions")
    public void resolveTest(String version, String baseClientUrl, String versionOrChannel) {
        final String ocpVersion = version;
        setOCPVersion(ocpVersion);
        final String systemType = SystemUtils.IS_OS_MAC ? "mac" : "linux";
        testDownloadedVersion(baseClientUrl + versionOrChannel
                + "/openshift-client-" + systemType + ".tar.gz", versionOrChannel);
    }

    @Test
    public void resolveNotFoundVersionTest() {
        final String ocpVersion = "1.1.1";
        setOCPVersion(ocpVersion);
        final String systemType = SystemUtils.IS_OS_MAC ? "mac" : "linux";
        testDownloadedVersion(
                "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/stable/openshift-client-" + systemType
                        + ".tar.gz",
                ocpVersion);
    }

    private void testDownloadedVersion(String downloadUrl, String ocpVersion) {
        SoftAssertions softAssertions = new SoftAssertions();
        // path is not null
        String resolvedPath = resolver.resolve();
        softAssertions.assertThat(resolvedPath).isNotNull();

        // path is correct and binary file exists
        Path ocPath = Paths.get("tmp/oc/oc");
        softAssertions.assertThat(resolvedPath).isEqualTo(ocPath.toAbsolutePath().toString());
        softAssertions.assertThat(Files.exists((ocPath))).isTrue();

        // archive is in cache
        String urlHash = DigestUtils.md5Hex(downloadUrl);
        Path cachedPath = Paths.get(OpenShiftConfig.binaryCachePath(), ocpVersion, urlHash, "oc.tar.gz");
        softAssertions.assertThat(Files.exists(cachedPath)).isTrue();
        softAssertions.assertAll();
    }

    private void setOCPVersion(final String ocpVersion) {
        systemProperties.set(OpenShiftConfig.OPENSHIFT_VERSION, ocpVersion);
        XTFConfig.loadConfig();
        ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo(true);
    }
}
