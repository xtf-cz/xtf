package cz.xtf.core.openshift;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

@Getter
public class PodShellOutput {
    private final String output;
    private final String error;

    PodShellOutput(String output, String error) {
        this.output = output;
        this.error = error;
    }

    public List<String> getOutputAsList() {
        return getOutputAsList("\n");
    }

    public List<String> getOutputAsList(String delimiter) {
        return Arrays.asList(StringUtils.split(getOutput(), delimiter));
    }

    public Map<String, String> getOutputAsMap(String keyValueDelimiter) {
        return getOutputAsMap(keyValueDelimiter, "\n");
    }

    public Map<String, String> getOutputAsMap(String keyValueDelimiter, String entryDelimiter) {
        Map<String, String> map = new HashMap<>();

        getOutputAsList(entryDelimiter).forEach(entry -> {
            String[] parsedEntry = StringUtils.split(entry, keyValueDelimiter, 2);
            map.put(parsedEntry[0], parsedEntry.length > 1 ? parsedEntry[1] : null);
        });

        return map;
    }
}
