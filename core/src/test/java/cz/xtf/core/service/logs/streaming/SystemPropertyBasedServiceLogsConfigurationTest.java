package cz.xtf.core.service.logs.streaming;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test cases to verify the SLS configuration and usage through system property, i.e. {@code xtf.log.streaming.config}
 */
public class SystemPropertyBasedServiceLogsConfigurationTest {

    static class TestClassA {
        ; // we don't need anything in here
    }

    static class TestClassAChild {
        ; // we don't need anything in here
    }

    static class TestClassB {
        ; // we don't need anything in here
    }

    static class TestClassBBKing {
        ; // we don't need anything in here
    }

    public static void verifyOneConfigurationIsCreatedForAGivenTarget(
            SystemPropertyBasedServiceLogsConfigurations configurations,
            String targetValue) {
        final Integer expectedTargeting = 1;
        List<ServiceLogsSettings> configurationsTargeting = configurations.list().stream()
                .filter(c -> targetValue.equals(c.getTarget()))
                .collect(Collectors.toList());
        Assertions.assertEquals(expectedTargeting, configurationsTargeting.size(),
                String.format(
                        "The number of SLS configurations loaded at runtime (%d) and targeting \"%s\" does not equal the expected number of property based SLS configurations (%d)",
                        configurationsTargeting.size(), targetValue, expectedTargeting));
    }

    public static void verifyExactNumberOfConfigurationsCreated(SystemPropertyBasedServiceLogsConfigurations configurations,
            int expected) {
        final Integer actual = configurations.list().size();
        Assertions.assertEquals(expected, actual,
                String.format(
                        "The number of SLS configurations loaded at runtime (%d) does not equal the expected number of property based SLS configurations (%d)",
                        actual, expected));
    }

    /**
     * Check that the correct number of configurations are created, and also that the expected configurations exists,
     * based on the system property value.
     */
    @Test
    public void testCorrectSlsConfigurationNumberIsCreated() {
        final SystemPropertyBasedServiceLogsConfigurations configurations = new SystemPropertyBasedServiceLogsConfigurations(
                "target=.*TestClassA,target=.*TestClassB.*;filter=.*");
        // let's check we have the exact number of SLS configs that we expect from the property we provided
        verifyExactNumberOfConfigurationsCreated(configurations, 2);
        // TestClassA - check by filtering all SLS configurations
        verifyOneConfigurationIsCreatedForAGivenTarget(configurations, ".*TestClassA");
        // TestClassA - check by retrieving the one based on per-class criteria
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchExists(
                configurations.forClass(TestClassA.class), TestClassA.class);
        // TestClassAChild - check by retrieving the one based on per-class criteria (negative)
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchDoesNotExist(
                configurations.forClass(TestClassAChild.class), TestClassAChild.class);
        // TestClassB - check by filtering all SLS configurations
        verifyOneConfigurationIsCreatedForAGivenTarget(configurations, ".*TestClassB.*");
        // TestClassB - check by retrieving the one based on per-class criteria
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchExists(
                configurations.forClass(TestClassB.class), TestClassB.class);
        // TestClassBBKing - check by retrieving the one based on per-class criteria
        ServiceLogsConfigurationTestHelper.verifyPerClassConfigurationSearchExists(
                configurations.forClass(TestClassBBKing.class), TestClassBBKing.class);
    }

    /**
     * Check that SLS configuration attributes are properly mapped when using the system property as a configuration
     * source.
     */
    @Test
    public void testSlsConfigurationAttributesAreProperlyMapped() {
        final SystemPropertyBasedServiceLogsConfigurations configurations = new SystemPropertyBasedServiceLogsConfigurations(
                "target=.*TestClassA,target=.*TestClassB.*;filter=.*");
        // TestClassA
        ServiceLogsSettings configurationForTestClass = configurations.forClass(TestClassA.class);
        Assertions.assertEquals(".*TestClassA", configurationForTestClass.getTarget(),
                String.format(
                        "SLS configuration \"target\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getTarget(), "TestClassA"));
        Assertions.assertEquals(ServiceLogsSettings.UNASSIGNED, configurationForTestClass.getFilter(),
                String.format(
                        "SLS configuration \"filter\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getFilter(), ServiceLogsSettings.UNASSIGNED));
        Assertions.assertEquals(ServiceLogsSettings.UNASSIGNED, configurationForTestClass.getOutputPath(),
                String.format(
                        "SLS configuration \"output\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getOutputPath(), ServiceLogsSettings.UNASSIGNED));
        // TestClassB
        configurationForTestClass = configurations.forClass(TestClassB.class);
        Assertions.assertEquals(".*TestClassB.*", configurationForTestClass.getTarget(),
                String.format(
                        "SLS configuration \"target\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getTarget(), "TestClassB.*"));
        Assertions.assertEquals(".*", configurationForTestClass.getFilter(),
                String.format(
                        "SLS configuration \"filter\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getFilter(), ".*"));
        Assertions.assertEquals(ServiceLogsSettings.UNASSIGNED, configurationForTestClass.getOutputPath(),
                String.format(
                        "SLS configuration \"output\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getOutputPath(), ServiceLogsSettings.UNASSIGNED));
    }

    /**
     * Check that a SLS configuration which has a non-valid attribute name is rejected
     */
    @Test
    public void testWrongAttributeNameSlsConfigurationIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SystemPropertyBasedServiceLogsConfigurations(
                "BROKENtarget=.*TestClassA,target=.*TestClassB.*;filter=.*"));
    }

    /**
     * Check that a SLS configuration which has a non-valid attributes format is rejected
     */
    @Test
    public void testWrongAttributeNameValueSeparatorSlsConfigurationIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SystemPropertyBasedServiceLogsConfigurations(
                "target:.*TestClassA"));
    }

    /**
     * Check that a SLS configuration which has a non-valid comma-separated format is rejected
     */
    @Test
    public void testBrokenItemListSlsConfigurationIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new SystemPropertyBasedServiceLogsConfigurations(
                ",target=.*TestClassB.*;filter=.*"));
    }

    /**
     * Check that a SLS configuration which has a {@code output} attribute set to a *Nix file path is accepted
     */
    @Test
    public void testNixPathsInAttributesAreAccepted() {
        final String filePath = "/home/myuser/sls.log";
        final String property = "target=.*TestClassB.*;output=" + filePath;
        final SystemPropertyBasedServiceLogsConfigurations configurations = new SystemPropertyBasedServiceLogsConfigurations(
                property);
        ServiceLogsSettings configurationForTestClass = configurations.forClass(TestClassB.class);
        Assertions.assertEquals(filePath, configurationForTestClass.getOutputPath(),
                String.format(
                        "SLS configuration \"output\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getOutputPath(), filePath));
    }

    /**
     * Check that a SLS configuration which has a {@code output} attribute set to a Windows-like file path is
     * accepted
     */
    @Test
    public void testWinPathsInAttributesAreAccepted() {
        final String filePath = "C:\\home\\myuser\\sls.log";
        final String property = "target=.*TestClassB.*;output=" + filePath;
        final SystemPropertyBasedServiceLogsConfigurations configurations = new SystemPropertyBasedServiceLogsConfigurations(
                property);
        ServiceLogsSettings configurationForTestClass = configurations.forClass(TestClassB.class);
        Assertions.assertEquals(filePath, configurationForTestClass.getOutputPath(),
                String.format(
                        "SLS configuration \"output\" attribute (%s) hasn't the expected value (%s)",
                        configurationForTestClass.getOutputPath(), filePath));
    }

    /**
     * Check that a SLS configuration which has a {@code target} attribute set to a regex is
     * accepted
     */
    @Test
    public void testRegExInAttributesIsAccepted() {
        final String regexText = "^\\s(?:[a|b]+)(\\d+)1{1}.*$)";
        final String property = "target=" + regexText;
        final SystemPropertyBasedServiceLogsConfigurations configurations = new SystemPropertyBasedServiceLogsConfigurations(
                property);
        Optional<ServiceLogsSettings> configurationSearch = configurations.list().stream().findAny();
        Assertions.assertTrue(configurationSearch.isPresent(),
                String.format(
                        "No SLS configuration has been created for the given property value (%s)", property));
        ServiceLogsSettings configuration = configurationSearch.get();
        Assertions.assertEquals(regexText, configuration.getTarget(),
                String.format(
                        "SLS configuration \"target\" attribute (%s) hasn't the expected value (%s)",
                        configuration.getTarget(), regexText));
    }
}
