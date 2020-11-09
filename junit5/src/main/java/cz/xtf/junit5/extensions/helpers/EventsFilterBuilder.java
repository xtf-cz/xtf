package cz.xtf.junit5.extensions.helpers;

import java.time.ZonedDateTime;

import io.fabric8.kubernetes.api.model.Event;

public class EventsFilterBuilder extends ResourcesFilterBuilder<Event> {

    @Override
    String getResourceName(Event event) {
        return event.getInvolvedObject().getName();
    }

    @Override
    ZonedDateTime getResourceTime(Event event) {
        String result = event.getLastTimestamp();
        return result == null ? super.getResourceTime(event)
                : ResourcesTimestampHelper.parseZonedDateTime(event.getLastTimestamp());
    }
}
