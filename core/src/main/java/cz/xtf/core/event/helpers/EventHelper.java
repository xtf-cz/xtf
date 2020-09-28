package cz.xtf.core.event.helpers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Event;

public class EventHelper {
    public static ZonedDateTime timestampToZonedDateTime(String timestamp) {
        return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
    }

    public static ZonedDateTime timeOfLastEventBMOrTestNamespaceOrEpoch() {
        ZonedDateTime zonedDateTime = timeOfLastEvent(OpenShifts.master());
        if (zonedDateTime == null) {
            zonedDateTime = timeOfLastEvent(BuildManagers.get().openShift());
        }
        return zonedDateTime != null ? zonedDateTime : ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneOffset.UTC);
    }

    public static ZonedDateTime timeOfLastEvent(OpenShift openShift) {
        List<Event> eventList = openShift.getEventList().stream()
                .filter(event -> event.getLastTimestamp() != null)
                .collect(Collectors.toList());
        if (eventList.isEmpty()) {
            return null;
        }

        eventList.sort((o1, o2) -> {
            ZonedDateTime o1Date = EventHelper.timestampToZonedDateTime(o1.getLastTimestamp());
            ZonedDateTime o2Date = EventHelper.timestampToZonedDateTime(o2.getLastTimestamp());
            return o1Date.compareTo(o2Date);
        });
        return EventHelper.timestampToZonedDateTime(eventList.get(eventList.size() - 1).getLastTimestamp());
    }
}
