package cz.xtf.core.waiting.failfast;

import java.time.ZonedDateTime;
import java.util.Arrays;

import cz.xtf.core.event.EventList;
import cz.xtf.core.event.EventListFilter;
import cz.xtf.core.openshift.OpenShift;

/**
 * Builder for creating fail fast checks for event
 * You can filter events by name, obj kind, obj name, time,...
 * <p>
 * Builds {@link WatchedResourcesSupplier} and provides reason when a check fails - list of filtered events and filter itself
 */
public class EventFailFastCheckBuilder {

    private final FailFastBuilder failFastBuilder;
    private String[] names = null;
    private ZonedDateTime after;
    private String[] reasons;
    private String[] messages;
    private String[] types;
    private String[] kinds;

    EventFailFastCheckBuilder(FailFastBuilder failFastBuilder) {
        this.failFastBuilder = failFastBuilder;
    }

    /**
     * Regexes to match event involved object name.
     *
     * @param name event names (regexes)
     * @return this
     */
    public EventFailFastCheckBuilder ofNames(String... name) {
        this.names = name;
        return this;
    }

    /**
     * Array of demanded reasons of events (case in-sensitive). One of them must be equal.
     *
     * @param reasons event reasons
     * @return this
     */
    public EventFailFastCheckBuilder ofReasons(String... reasons) {
        this.reasons = reasons;
        return this;
    }

    /**
     * Array of demanded types of events (case in-sensitive). One of them must be equal.
     * For example: {@code Warning}, {@code Normal}, ...
     *
     * @param types event types
     * @return this
     */
    public EventFailFastCheckBuilder ofTypes(String... types) {
        this.types = types;
        return this;
    }

    /**
     * Array of demanded object kinds of events (case in-sensitive). One of them must be equal.
     * For example: {@code persistentvolume}, {@code pod}, ...
     *
     * @param kinds event kinds
     * @return this
     */
    public EventFailFastCheckBuilder ofKinds(String... kinds) {
        this.kinds = kinds;
        return this;
    }

    /**
     * Regexes for demanded messages. One of them must match.
     *
     * @param messages event messages
     * @return this
     */
    public EventFailFastCheckBuilder ofMessages(String... messages) {
        this.messages = messages;
        return this;
    }

    /**
     * If at least one event exist (after filtration), final function returns true.
     * 
     * @return this
     */
    public FailFastBuilder atLeastOneExists() {
        // function is invoked every time...everytime we get events and filter them
        failFastBuilder.addFailFastCheck(new WatchedResourcesSupplier<>(
                () -> getFilterEventList().collect(),
                eventList -> !eventList.isEmpty(),
                eventList -> failFastReason(eventList, "at least one exists")));

        return failFastBuilder;
    }

    private String failFastReason(EventList eventList, String condition) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Following events match condition: <").append(condition).append(">\n");
        eventList.forEach(e -> stringBuilder
                .append("\t")
                .append(e.getLastTimestamp())
                .append("\t")
                .append(e.getInvolvedObject().getKind())
                .append("/")
                .append(e.getInvolvedObject().getName())
                .append("\t")
                .append(e.getMessage())
                .append("\n"));
        stringBuilder.append("Filter:");
        if (kinds != null) {
            stringBuilder.append("\t obj kinds: ").append(Arrays.toString(kinds)).append("\n");
        }
        if (names != null) {
            stringBuilder.append("\t obj names: ").append(Arrays.toString(names)).append("\n");
        }
        if (reasons != null) {
            stringBuilder.append("\t event reasons: ").append(Arrays.toString(reasons)).append("\n");
        }
        if (messages != null) {
            stringBuilder.append("\t messages: ").append(Arrays.toString(messages)).append("\n");
        }
        if (types != null) {
            stringBuilder.append("\t event types: ").append(Arrays.toString(types)).append("\n");
        }
        if (after != null) {
            stringBuilder.append("\t after: ").append(after.toString()).append("\n");
        }
        return stringBuilder.toString();

    }

    /**
     * Consider event after certain time.
     *
     * @param after event time
     * @return this
     */
    public EventFailFastCheckBuilder after(ZonedDateTime after) {
        this.after = after;
        return this;
    }

    private EventListFilter getFilterEventList() {
        EventListFilter filter = getEventsForAllNamespaces().filter();
        if (names != null) {
            filter.ofObjNames(names);
        }
        if (after != null) {
            filter.inOneOfTimeWindows(after, ZonedDateTime.now());
        }
        if (reasons != null) {
            filter.ofReasons(reasons);
        }
        if (messages != null) {
            filter.ofMessages(messages);
        }
        if (types != null) {
            filter.ofEventTypes(types);
        }
        if (kinds != null) {
            filter.ofObjKinds(kinds);
        }
        return filter;
    }

    private EventList getEventsForAllNamespaces() {
        EventList events = null;

        for (OpenShift openShift : failFastBuilder.getOpenshifts()) {
            if (events == null) {
                events = openShift.getEventList();
            } else {
                events.addAll(openShift.getEventList());
            }
        }
        return events;
    }
}
