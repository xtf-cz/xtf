package cz.xtf.core.openshift;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.codec.digest.DigestUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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

    @Test
    public void resolveTest() {
        final String ocpVersion = "4.8.14";
        setOCPVersion(ocpVersion);
        testDownloadedVersion("https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/" + ocpVersion
                + "/openshift-client-linux.tar.gz", ocpVersion);
    }

    @Test
    public void resolveNotFoundVersionTest() {
        final String ocpVersion = "1.1.1";
        setOCPVersion(ocpVersion);
        testDownloadedVersion(
                "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/stable/openshift-client-linux.tar.gz",
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
