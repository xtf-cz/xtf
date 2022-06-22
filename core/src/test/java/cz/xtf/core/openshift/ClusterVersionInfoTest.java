package cz.xtf.core.openshift;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.config.XTFConfig;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class ClusterVersionInfoTest {

    private ClusterVersionInfo clusterVersionInfo;

    @SystemStub
    private SystemProperties systemProperties;

    @BeforeEach
    public void setOcpVersion() {
        systemProperties.set(OpenShiftConfig.OPENSHIFT_VERSION, "4.8.14");
        XTFConfig.loadConfig();
        clusterVersionInfo = ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo(true);
    }

    @Test
    public void getOpenshiftVersionTest() {
        Assertions.assertEquals("4.8.14", clusterVersionInfo.getOpenshiftVersion());
    }

    @Test
    public void getMajorMinorOpenshiftVersionTest() {
        Assertions.assertEquals("4.8", clusterVersionInfo.getMajorMinorOpenshiftVersion());
    }

    @Test
    public void isMajorMinorOnlyTest() {
        Assertions.assertFalse(clusterVersionInfo.isMajorMinorOnly());
    }

    @Test
    public void isMajorMinorMicroTest() {
        Assertions.assertTrue(clusterVersionInfo.isMajorMinorMicro());
    }

    @Test
    public void isNightlyTest() {
        Assertions.assertFalse(clusterVersionInfo.isNightly());
    }
}
