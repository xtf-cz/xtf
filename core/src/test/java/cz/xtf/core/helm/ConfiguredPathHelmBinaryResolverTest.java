package cz.xtf.core.helm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfiguredPathHelmBinaryResolverTest {

    private HelmBinaryPathResolver configuredPathHelmBinaryResolver = new ConfiguredPathHelmBinaryResolver();

    @Test
    public void resolveTest() {
        Assertions.assertEquals("/tmp/helm", configuredPathHelmBinaryResolver.resolve());
    }

}
