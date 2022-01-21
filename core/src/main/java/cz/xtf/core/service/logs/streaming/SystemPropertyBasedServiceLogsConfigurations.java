package cz.xtf.core.service.logs.streaming;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements {@link ServiceLogsSettings} in order to provide the concrete logic to handle Service Logs
 * Streaming configuration based on the {@code xtf.log.streaming.config} system property.
 */
public class SystemPropertyBasedServiceLogsConfigurations {

    /*
     * The property format should match the following format:
     *
     * ^(name=value(;name=value)*)(,(name=value(;name=value)*))*$
     *
     * i.e. a comma (,) separated list pof configuration items, each of which is a semi-colon (;) separated list of name
     * and value pairs, separated by an equal character (=)
     */
    private static final String CONFIGURATION_PROPERTY_ITEMS_SEPARATOR = ",";
    private static final String CONFIGURATION_PROPERTY_ATTRIBUTE_SEPARATOR = ";";
    private static final String CONFIGURATION_PROPERTY_NAME_VALUE_SEPARATOR = "=";
    // attribute names regex
    private static final String ALLOWED_ATTRIBUTE_NAME_REGEX = String.format("%s|%s|%s",
            ServiceLogsSettings.ATTRIBUTE_NAME_TARGET,
            ServiceLogsSettings.ATTRIBUTE_NAME_FILTER,
            ServiceLogsSettings.ATTRIBUTE_NAME_OUTPUT);
    // attributes values regex (which must allow for adding basically any char plus special ones, as for providing
    // regex'es
    private static final String ALLOWED_ATTRIBUTE_VALUE_REGEX = "[\\\\|\\w|^|$|.|+|?|*|\\[|\\]|(|)|{|}|&|\\-|>|<|/|:]";
    // full attribute (name=value) regex
    private static final String ALLOWED_ATTRIBUTE_REGEX = "(?:" + ALLOWED_ATTRIBUTE_NAME_REGEX + ")"
            + CONFIGURATION_PROPERTY_NAME_VALUE_SEPARATOR
            + ALLOWED_ATTRIBUTE_VALUE_REGEX + "+";
    // a configuration item regex, i.e. 1..N attributes, separated by semi-colon (;)
    private static final String ALLOWED_ITEM_REGEX = "("
            + ALLOWED_ATTRIBUTE_REGEX
            + "(?:" + CONFIGURATION_PROPERTY_ATTRIBUTE_SEPARATOR + ALLOWED_ATTRIBUTE_REGEX + ")*"
            + ")";

    private final String serviceLogsStreamingConfigProperty;
    private final Map<String, ServiceLogsSettings> configurations;

    public SystemPropertyBasedServiceLogsConfigurations(String serviceLogsStreamingConfigProperty) {
        if ((serviceLogsStreamingConfigProperty == null) || serviceLogsStreamingConfigProperty.isEmpty()) {
            throw new IllegalArgumentException(
                    "A valid configuration must be provided by setting \"xtf.log.streaming.config\" value, in order to initialize a \"SystemPropertyBasedServiceLogsConfigurations\" instance");
        }
        this.serviceLogsStreamingConfigProperty = serviceLogsStreamingConfigProperty;
        configurations = loadConfigurations();
    }

    private Map<String, ServiceLogsSettings> loadConfigurations() {
        return Stream.of(serviceLogsStreamingConfigProperty.split(CONFIGURATION_PROPERTY_ITEMS_SEPARATOR))
                .map(configurationItem -> getSettingsFromItemConfiguration(configurationItem))
                .collect(Collectors.toMap(serviceLogsSettings -> serviceLogsSettings.getTarget(),
                        serviceLogsSettings -> serviceLogsSettings));
    }

    private static ServiceLogsSettings getSettingsFromItemConfiguration(String configurationItem) {
        // validate the item config
        if (!configurationItem.matches(ALLOWED_ITEM_REGEX)) {
            throw new IllegalArgumentException(
                    String.format(
                            "The value of the \"xtf.log.streaming.config\" property items must match the following format: %s. Was: %s"
                                    + ALLOWED_ITEM_REGEX,
                            configurationItem));
        }
        // get all attributes for an item
        final String[] configurationAttributes = configurationItem.split(CONFIGURATION_PROPERTY_ATTRIBUTE_SEPARATOR);
        // prepare a builder for this item configuration
        final ServiceLogsSettings.Builder builder = new ServiceLogsSettings.Builder();

        Arrays.stream(configurationAttributes).map(a -> a.split(CONFIGURATION_PROPERTY_NAME_VALUE_SEPARATOR))
                .forEach(nameValuePair -> {
                    final String attributeName = nameValuePair[0];
                    final String attributeValue = nameValuePair[1];
                    toBuilder(builder, attributeName, attributeValue);
                });
        return builder.build();
    }

    private static ServiceLogsSettings.Builder toBuilder(ServiceLogsSettings.Builder theBuilder, String attributeName,
            String attributeValue) {
        switch (attributeName) {
            case ServiceLogsSettings.ATTRIBUTE_NAME_TARGET:
                theBuilder.withTarget(attributeValue);
                break;
            case ServiceLogsSettings.ATTRIBUTE_NAME_FILTER:
                theBuilder.withFilter(attributeValue);
                break;
            case ServiceLogsSettings.ATTRIBUTE_NAME_OUTPUT:
                theBuilder.withOutputPath(attributeValue);
                break;
            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Unconventional configuration attribute name: %s. Allowed configuration attributes names: (%s)",
                                attributeName, ALLOWED_ATTRIBUTE_NAME_REGEX));
        }
        return theBuilder;
    }

    //	just for test purposes, at the moment
    Collection<ServiceLogsSettings> list() {
        return configurations.values();
    }

    public ServiceLogsSettings forClass(Class<?> testClazz) {
        Optional<Map.Entry<String, ServiceLogsSettings>> selectedConfigurationSearch = configurations.entrySet().stream()
                .filter(c -> testClazz.getName().equals(c.getKey()))
                .findFirst();
        if (!selectedConfigurationSearch.isPresent()) {
            selectedConfigurationSearch = configurations.entrySet().stream()
                    .filter(c -> testClazz.getName().matches(c.getKey()))
                    .findFirst();
        }
        return selectedConfigurationSearch.isPresent() ? selectedConfigurationSearch.get().getValue() : null;
    }
}
