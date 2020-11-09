package cz.xtf.junit5.extensions.helpers;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ResourcesFilterBuilder<E extends HasMetadata> implements Cloneable {

    FilterMethod method;
    String[] resourceNames;
    ZonedDateTime excludeUntil;
    ZonedDateTime includeAlwaysAfter;
    ZonedDateTime includeAlwaysUntil;

    public Predicate<E> build() {
        switch (method) {
            case RESOURCE_NAMES:
                return resource -> resourceNameMatches(getResourceName(resource), resourceNames);
            case PREVIOUSLY_SEEN_RESOURCES:
                return resource -> {
                    ZonedDateTime time = getResourceTime(resource);
                    return (includeAlwaysAfter != null && includeAlwaysUntil != null && time.isAfter(includeAlwaysAfter)
                            && !time.isAfter(includeAlwaysUntil))
                            || (excludeUntil != null && time.isAfter(excludeUntil));
                };
            default:
                return resource -> true;
        }
    }

    String getResourceName(E item) {
        return item.getMetadata().getName();
    }

    ZonedDateTime getResourceTime(E item) {
        return ResourcesTimestampHelper.parseZonedDateTime(item.getMetadata().getCreationTimestamp());
    }

    public ZonedDateTime getExcludedUntil() {
        return excludeUntil;
    }

    /**
     * Resources until the time will be excluded. Lower priority than
     * {@link ResourcesFilterBuilder#setIncludedAlwaysWindow(ZonedDateTime, ZonedDateTime)}.
     * It the resource is in the {@code always included window} it will be included
     */
    public ResourcesFilterBuilder setExcludedUntil(ZonedDateTime until) {
        this.excludeUntil = until;
        return this;
    }

    /**
     * Resources in the window will be always included despite {@link ResourcesFilterBuilder#setExcludedUntil(ZonedDateTime)}.
     */
    public ResourcesFilterBuilder setIncludedAlwaysWindow(ZonedDateTime after, ZonedDateTime until) {
        this.includeAlwaysAfter = after;
        this.includeAlwaysUntil = until;
        return this;
    }

    public ZonedDateTime getIncludedAlwaysWindowAfter() {
        return includeAlwaysAfter;
    }

    public ZonedDateTime getIncludedAlwaysWindowUntil() {
        return includeAlwaysUntil;
    }

    public ResourcesFilterBuilder filterByResourceNames() {
        method = FilterMethod.RESOURCE_NAMES;
        return this;
    }

    public ResourcesFilterBuilder filterByLastSeenResources() {
        method = FilterMethod.PREVIOUSLY_SEEN_RESOURCES;
        return this;
    }

    public ResourcesFilterBuilder setResourceNames(String... resourceNames) {
        this.resourceNames = Arrays.copyOf(resourceNames, resourceNames.length);
        return this;
    }

    @Override
    public ResourcesFilterBuilder<E> clone() throws CloneNotSupportedException {
        return (ResourcesFilterBuilder<E>) super.clone();
    }

    boolean resourceNameMatches(String resourceName, String[] resourceNamesLookup) {
        for (String resourceNameLookup : getRegexResourceNames(resourceNamesLookup)) {
            if (resourceName.matches(resourceNameLookup)) {
                return true;
            }
        }
        return false;
    }

    String[] getRegexResourceNames(String[] resourceNames) {
        String[] result = new String[resourceNames.length];
        for (int i = 0; i < resourceNames.length; i++) {
            result[i] = resourceNames[i] + ".*";
        }
        return result;
    }

    enum FilterMethod {
        RESOURCE_NAMES,
        PREVIOUSLY_SEEN_RESOURCES
    }
}
