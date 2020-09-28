package cz.xtf.core.event;

import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cz.xtf.core.event.helpers.EventHelper;
import io.fabric8.kubernetes.api.model.Event;

public class EventListFilter {

    private Stream<Event> stream;

    public EventListFilter(EventList events) {
        stream = events.stream();
    }

    /**
     * Filter events of types defined in the array (case insensitive).
     * For example: {@code Warning}, {@code Normal}, ...
     */
    public EventListFilter ofEventTypes(String... types) {
        stream = stream.filter(event -> isStrInArrayCaseInsensitive(event.getType(), types));
        return this;
    }

    /**
     * Filter events with involved object kind defined in the array (case insensitive).
     * For example: {@code Warning}, {@code Normal}, ...
     */
    public EventListFilter ofObjKinds(String... kinds) {
        stream = stream.filter(event -> isStrInArrayCaseInsensitive(event.getInvolvedObject().getKind(), kinds));
        return this;
    }

    /**
     * Return true if there is at least one event that match one of the given messages (reg. expressions)
     */
    public boolean atLeastOneRegexMessages(String... messagesRegex) {
        return stream.anyMatch(event -> {
            for (String regex : messagesRegex) {
                if (event.getMessage().matches(regex)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Filter events with messages (reg. expressions) defined in the array.
     */
    public EventListFilter ofMessages(String... messagesRegex) {
        stream = stream.filter(event -> {
            for (String regex : messagesRegex) {
                if (event.getMessage().matches(regex)) {
                    return true;
                }
            }
            return false;
        });
        return this;
    }

    /**
     * Return filtered {@link EventList}
     */
    public EventList collect() {
        return new EventList(stream.collect(Collectors.toList()));
    }

    /**
     * Filter events with involved object name defined in the array of reg. expressions.
     */
    public EventListFilter ofObjNames(String... regexNames) {
        stream = stream.filter(event -> {
            for (String regexName : regexNames) {
                if (event.getInvolvedObject().getName().matches(regexName)) {
                    return true;
                }
            }
            return false;
        });
        return this;
    }

    /**
     * Filter events that are last seen in any if given time windows.
     * A structure of the array should be: [from date], [until date], [from date] [until date],...
     * Event needs to be seen strictly after {@code from date} and before or at the same time as {@code until date}.
     *
     * {@link ZonedDateTime} is used because a OpenShift cluster is distributed and time is provided in
     * {@link java.time.format.DateTimeFormatter#ISO_DATE_TIME}
     * format that consider time zones. Therefore wee need to compare it against {@link ZonedDateTime#now()} (for example)
     *
     * @see ZonedDateTime
     */
    public EventListFilter inOneOfTimeWindows(ZonedDateTime... dates) {
        stream = stream.filter(event -> {
            if (event.getLastTimestamp() != null) {
                ZonedDateTime eventDate = EventHelper.timestampToZonedDateTime(event.getLastTimestamp());
                for (int i = 0; i < dates.length - 1; i += 2) {
                    if (eventDate.isAfter(dates[i])
                            && (eventDate.compareTo(dates[i + 1]) == 0 || eventDate.isBefore(dates[i + 1]))) {
                        return true;
                    }
                }
            }
            return false;
        });
        return this;
    }

    /**
     * Filter events that are last seen strictly after the date.
     */
    public EventListFilter after(ZonedDateTime date) {
        stream = stream.filter(
                e -> e.getLastTimestamp() != null && EventHelper.timestampToZonedDateTime(e.getLastTimestamp()).isAfter(date));
        return this;
    }

    public Stream<Event> getStream() {
        return stream;
    }

    /**
     * Filter events with involved object kind defined in the array (case insensitive).
     */
    public EventListFilter ofReasons(String... reasons) {
        stream = stream.filter(event -> isStrInArrayCaseInsensitive(event.getReason(), reasons));
        return this;
    }

    public long count() {
        return stream.count();
    }

    private boolean isStrInArrayCaseInsensitive(String str, String... array) {
        String field = str.toLowerCase();
        for (String item : array) {
            if (field.equals(item.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
