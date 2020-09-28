package cz.xtf.core.bm;

import lombok.Getter;

@Getter
public class ManagedBuildReference {
    private final String streamName;
    private final String tagName;
    private final String namespace;
    private final String isTagReference;

    ManagedBuildReference(String streamName, String tagName, String namespace) {
        this.streamName = streamName;
        this.tagName = tagName;
        this.namespace = namespace;
        this.isTagReference = streamName + ":" + tagName;
    }
}
