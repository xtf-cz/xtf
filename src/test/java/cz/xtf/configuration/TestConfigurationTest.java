package cz.xtf.configuration;

import cz.xtf.TestConfiguration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class TestConfigurationTest {

	private static final String IMAGE_JDV_CLIENT = "IMAGE_JDV_CLIENT";

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	public void canReadEnvironmentVariableFromTestConfiguration(){
		environmentVariables.set(IMAGE_JDV_CLIENT,IMAGE_JDV_CLIENT);
		Assert.assertEquals("Fix TestConfiguration constructor",IMAGE_JDV_CLIENT, TestConfiguration.imageJdvClient());
	}
}
