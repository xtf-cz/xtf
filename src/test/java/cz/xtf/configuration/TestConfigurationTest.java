package cz.xtf.configuration;

import cz.xtf.TestConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class TestConfigurationTest {

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Before
	public void setEnvironmentVariables(){
		environmentVariables.set("IMAGE_JDV_CLIENT", "IMAGE_JDV_CLIENT");
		environmentVariables.set("MASTER_USERNAME", "MASTER_USERNAME");
	}

	@Test
	public void canReadEnvironmentVariableFromTestConfiguration() {

		Assert.assertEquals("Fix TestConfiguration constructor", "IMAGE_JDV_CLIENT", TestConfiguration.imageJdvClient());
	}


	@Test
	public void canReadDefaultValueOfPropertyFromXTFConfiguration() {
		Assert.assertEquals("/usr/bin/oc", TestConfiguration.ocBinaryLocation());
	}

	@Test
	public void canReadDefaultValueByTestConfigurationFromXTFConfiguration() {
		Assert.assertEquals(8080, TestConfiguration.vertxProxyPort());
	}
}
