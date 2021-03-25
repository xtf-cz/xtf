package cz.xtf.core.helm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.core.config.HelmConfig;
import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.openshift.ClusterVersionInfoFactory;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class ConfiguredVersionHelmBinaryPathResolverTest {
    @SystemStub
    private SystemProperties systemProperties;

    private HelmBinaryPathResolver helmBinaryPathResolver = new ConfiguredVersionHelmBinaryPathResolver();

    @BeforeEach
    public void setOCPVersion() {
        systemProperties.set(OpenShiftConfig.OPENSHIFT_VERSION, "4.9");
        XTFConfig.loadConfig();
        ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo(true);
    }

    private void setHelmClientVersion(String helmVersion) {
        systemProperties.set(HelmConfig.HELM_CLIENT_VERSION, helmVersion);
        XTFConfig.loadConfig();
    }

    @Test
    public void resolveCorrectVersion() {
        setHelmClientVersion("3.5.0");
        final String systemType = SystemUtils.IS_OS_MAC ? "darwin" : "linux";
        testDownloadedVersion(
                "https://mirror.openshift.com/pub/openshift-v4/clients/helm/3.5.0/helm-" + systemType + "-amd64.tar.gz",
                "3.5.0");
    }

    @Test
    public void resolveIncorrectVersion() {
        setHelmClientVersion("1.0.0");
        Assertions.assertThrows(IllegalStateException.class, () -> helmBinaryPathResolver.resolve());
    }

    private void testDownloadedVersion(String downloadUrl, String helmVersion) {
        SoftAssertions softAssertions = new SoftAssertions();
        // path is not null
        String resolvedPath = helmBinaryPathResolver.resolve();
        softAssertions.assertThat(resolvedPath).isNotNull();

        // path is correct and binary file exists
        Path helmPath = Paths.get("tmp/helm/helm");
        softAssertions.assertThat(resolvedPath).isEqualTo(helmPath.toAbsolutePath().toString());
        softAssertions.assertThat(Files.exists((helmPath))).isTrue();

        // archive is in cache
        String urlHash = DigestUtils.md5Hex(downloadUrl);
        Path cachedPath = Paths.get(HelmConfig.binaryCachePath(), helmVersion,
                urlHash, "helm.tar.gz");
        softAssertions.assertThat(Files.exists(cachedPath)).isTrue();
        softAssertions.assertAll();
    }

}
