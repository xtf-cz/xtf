package cz.xtf.core.openshift;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.codec.digest.DigestUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import cz.xtf.core.config.OpenShiftConfig;

public class ClusterVersionOpenShiftBinaryPathResolverTest {

    private OpenShiftBinaryPathResolver resolver = new ClusterVersionOpenShiftBinaryPathResolver();

    @Test
    public void resolveTest() {
        String path = resolver.resolve();

        SoftAssertions softAssertions = new SoftAssertions();

        // path is not null
        softAssertions.assertThat(path).isNotNull();

        // path is correct and binary file exists
        Path ocPath = Paths.get("tmp/oc/oc");
        softAssertions.assertThat(path).isEqualTo(ocPath.toAbsolutePath().toString());
        softAssertions.assertThat(Files.exists((ocPath))).isTrue();

        // archive is in cache
        String urlHash = DigestUtils.md5Hex(
                "https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/4.8.14/openshift-client-linux.tar.gz");
        Path cachedPath = Paths.get(OpenShiftConfig.binaryCachePath(), "4.8.14", urlHash, "oc.tar.gz");
        softAssertions.assertThat(Files.exists(cachedPath)).isTrue();

        softAssertions.assertAll();
    }
}
