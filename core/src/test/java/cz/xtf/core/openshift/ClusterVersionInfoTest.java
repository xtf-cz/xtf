package cz.xtf.core.openshift;

import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
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
public class ClusterVersionInfoTest {

    private ClusterVersionInfo clusterVersionInfo;

    private static Stream<Arguments> provideVersions() {
        // tested version String
        // full OpenShift version
        // major.minor OpenShift version
        // is major.minor only
        // is major.minor.micro
        // is developer preview
        // is release candidate
        return Stream.of(
                Arguments.arguments("4.8", "4.8", "4.8", true, false, false, false),
                Arguments.arguments("4.8.14", "4.8.14", "4.8", false, true, false, false),
                Arguments.arguments("4.11.0-0.nightly-2022-05-25-193227", "4.11.0-0.nightly-2022-05-25-193227", "4.11", false,
                        true, true, false),
                Arguments.arguments("4.12.0-ec.5", "4.12.0-ec.5", "4.12", false, true, true, false),
                Arguments.arguments("4.13.0-rc.8", "4.13.0-rc.8", "4.13", false, true, false, true));
    }

    @SystemStub
    private SystemProperties systemProperties;

    @ParameterizedTest
    @MethodSource("provideVersions")
    public void test(String version, String fullVersion, String majorMinorVersion, boolean isMajorMinorOnly,
            boolean isMajorMinorMicro, boolean isDeveloperPreview, boolean isReleaseCandidate) {
        clusterVersionInfo = initClusterVersionInfo(version);

        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(clusterVersionInfo.getOpenshiftVersion()).isEqualTo(fullVersion);
        assertions.assertThat(clusterVersionInfo.getMajorMinorOpenshiftVersion()).isEqualTo(majorMinorVersion);
        assertions.assertThat(clusterVersionInfo.isMajorMinorOnly()).isEqualTo(isMajorMinorOnly);
        assertions.assertThat(clusterVersionInfo.isMajorMinorMicro()).isEqualTo(isMajorMinorMicro);
        assertions.assertThat(clusterVersionInfo.isDeveloperPreview()).isEqualTo(isDeveloperPreview);
        assertions.assertThat(clusterVersionInfo.isReleaseCandidate()).isEqualTo(isReleaseCandidate);

        assertions.assertAll();
    }

    private ClusterVersionInfo initClusterVersionInfo(String version) {
        systemProperties.set(OpenShiftConfig.OPENSHIFT_VERSION, version);
        XTFConfig.loadConfig();
        return ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo(true);
    }
}
