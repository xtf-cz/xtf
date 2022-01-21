package cz.xtf.core.service.logs.streaming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test cases that verify the SLS configuration and usage through annotation, i.e.
 * {@link ServiceLogsStreaming}
 */
public class AnnotationBasedServiceLogsConfigurationTest {

    private static final AnnotationBasedServiceLogsConfigurations CONFIGURATIONS = new AnnotationBasedServiceLogsConfigurations();

    @ServiceLogsStreaming(filter = ".*all-colors", output = "/home/myuser/sls.log")
    static class ColorsTestClass {
        ; // we don't need anything in here
    }

    @ServiceLogsStreaming
    static class AnimalsTestClass {
        ; // we don't need anything in here
    }

    @ServiceLogsStreaming
    static class CarsTestClass {
        ; // we don't need anything in here
    }

    /**
     * Check that the expected configuration exists, based on the test class {@link ServiceLogsStreaming} annotation.
     */
    @Test
    public void testCorrectSlsConfigurationsAreCreated() {
        // ColorsTestClass - check by retrieving the one based on per-class criteria
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchExists(
                CONFIGURATIONS.forClass(ColorsTestClass.class), ColorsTestClass.class);
        // AnnotationBasedServiceLogsConfigurationTest - check by retrieving the one based on per-class criteria (negative)
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchDoesNotExist(
                CONFIGURATIONS.forClass(AnnotationBasedServiceLogsConfigurationTest.class),
                AnnotationBasedServiceLogsConfigurationTest.class);
        // AnimalsTestClass - check by retrieving the one based on per-class criteria
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchExists(
                CONFIGURATIONS.forClass(AnimalsTestClass.class), AnimalsTestClass.class);
        // CarsTestClass - check by retrieving the one based on per-class criteria
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchExists(
                CONFIGURATIONS.forClass(CarsTestClass.class), CarsTestClass.class);
    }

    /**
     * Check that SLS configuration attributes are properly mapped when using the {@link ServiceLogsStreaming}
     * annotation as a configuration source.
     */
    @Test
    public void testSlsConfigurationAttributesAreProperlyMapped() {
        // TestClassA
        ServiceLogsSettings configuration = CONFIGURATIONS.forClass(ColorsTestClass.class);
        Assertions.assertEquals(ColorsTestClass.class.getName(), configuration.getTarget(),
                String.format(
                        "SLS configuration \"target\" attribute (%s) hasn't the expected value (%s)",
                        configuration.getTarget(), ColorsTestClass.class.getName()));
        Assertions.assertEquals(".*all-colors", configuration.getFilter(),
                String.format(
                        "SLS configuration \"filter\" attribute (%s) hasn't the expected value (%s)",
                        configuration.getFilter(), ".*all-colors"));
        Assertions.assertEquals("/home/myuser/sls.log", configuration.getOutputPath(),
                String.format(
                        "SLS configuration \"output\" attribute (%s) hasn't the expected value (%s)",
                        configuration.getOutputPath(), "/home/myuser/sls.log"));
    }
}
