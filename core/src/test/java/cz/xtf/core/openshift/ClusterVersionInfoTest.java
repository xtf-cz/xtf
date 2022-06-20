package cz.xtf.core.openshift;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClusterVersionInfoTest {

    private ClusterVersionInfo clusterVersionInfo = new ClusterVersionInfo();

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
