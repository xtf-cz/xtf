package cz.xtf.builder.builders;

import java.util.Map;

public interface EnvironmentConfiguration {
    public EnvironmentConfiguration configEntry(final String key, final String value);

    public default EnvironmentConfiguration configEntries(final Map<String, String> values) {
        values.entrySet().forEach(x -> configEntry(x.getKey(), x.getValue()));
        return this;
    }

    public Map<String, String> getConfigEntries();
}
