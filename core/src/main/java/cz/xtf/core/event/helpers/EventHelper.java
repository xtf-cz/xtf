package cz.xtf.core.event.helpers;


import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.event.EventList;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class EventHelper {
	public static ZonedDateTime timestampToZonedDateTime(String timestamp) {
		return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
	}

	public static ZonedDateTime timeOfLastEventBMOrTestNamespaceOrEpoch() {
		ZonedDateTime zonedDateTime = timeOfLastEvent(OpenShifts.master());
		if (zonedDateTime == null) {
			zonedDateTime = timeOfLastEvent(OpenShifts.master(BuildManagerConfig.namespace()));
		}
		return zonedDateTime != null ? zonedDateTime : ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneOffset.UTC);
	}

	public static ZonedDateTime timeOfLastEvent(OpenShift openShift) {
		EventList eventList = openShift.getEventList();
		if (eventList.isEmpty()) {
			return null;
		}

		eventList.sort((o1, o2) -> {
			if (o1.getLastTimestamp() == null && o2.getLastTimestamp() == null) {
				return 0;
			} else if (o1.getLastTimestamp() == null) {
				return 1;
			} else if (o2.getLastTimestamp() == null) {
				return -1;
			} else {
				ZonedDateTime o1Date = EventHelper.timestampToZonedDateTime(o1.getLastTimestamp());
				ZonedDateTime o2Date = EventHelper.timestampToZonedDateTime(o2.getLastTimestamp());
				return o1Date.compareTo(o2Date);
			}
		});
		return EventHelper.timestampToZonedDateTime(eventList.get(eventList.size() - 1).getLastTimestamp());
	}
}
