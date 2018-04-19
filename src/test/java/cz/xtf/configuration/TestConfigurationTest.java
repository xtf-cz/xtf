package cz.xtf.configuration;

import cz.xtf.TestConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class TestConfigurationTest {

	@Test
	public void canReadDefaultValueOfPropertyFromXTFConfiguration() {
		Assert.assertEquals("/usr/bin/oc", TestConfiguration.ocBinaryLocation());
	}
}
