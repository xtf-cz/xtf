package cz.xtf.core.event.helpers;


import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class EventHelper {
	public static ZonedDateTime timestampToZonedDateTime(String timestamp) {
		return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
	}
}
