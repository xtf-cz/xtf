package cz.xtf.configuration;

import cz.xtf.TestConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class TestConfigurationTest {

	@Test
	public void canReadDefaultValueOfPropertyFromXTFConfiguration() {
		Assert.assertEquals("/usr/bin/oc", TestConfiguration.ocBinaryLocation());
	}

	@Test
	public void canReadDefaultValueByTestConfigurationFromXTFConfiguration() {
		Assert.assertEquals(8080, TestConfiguration.vertxProxyPort());
	}

	@Test
	public void canReadEnvironmentVariable(){
		Assert.assertEquals("TRAVIS_CI", TestConfiguration.ciUsername());
	}
}
