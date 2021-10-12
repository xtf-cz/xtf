package cz.xtf.core.openshift;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OpenShiftBinaryManagerFactoryTest {

    @Test
    public void getOpenShiftBinaryManagerTest() {
        OpenShiftBinaryManager openShiftBinaryManager = OpenShiftBinaryManagerFactory.INSTANCE.getOpenShiftBinaryManager();
        Assertions.assertNotNull(openShiftBinaryManager);
        Assertions.assertEquals("/tmp/test", openShiftBinaryManager.getBinaryPath());
    }
}
