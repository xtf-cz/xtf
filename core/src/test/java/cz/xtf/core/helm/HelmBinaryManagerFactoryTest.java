package cz.xtf.core.helm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HelmBinaryManagerFactoryTest {

    @Test
    public void getHelmBinaryManagerTest() {
        HelmBinaryManager helmBinaryManager = HelmBinaryManagerFactory.INSTANCE.getHelmBinaryManager();
        Assertions.assertNotNull(helmBinaryManager);
        Assertions.assertEquals("/tmp/helm", helmBinaryManager.getHelmBinaryPath());
    }

}
