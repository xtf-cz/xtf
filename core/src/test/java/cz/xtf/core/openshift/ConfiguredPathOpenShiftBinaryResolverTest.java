package cz.xtf.core.openshift;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfiguredPathOpenShiftBinaryResolverTest {

    private OpenShiftBinaryPathResolver resolver = new ConfiguredPathOpenShiftBinaryResolver();

    @Test
    public void resolveTest() {
        Assertions.assertEquals("/tmp/test", resolver.resolve());
    }
}
