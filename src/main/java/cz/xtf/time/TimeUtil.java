package cz.xtf.time;

import java.lang.management.ManagementFactory;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public final class TimeUtil {
	private static final DateFormat OPENSHIFT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private static final long JVM_UTC_START_TIME = initJvmUtcStartTime();
	
	private TimeUtil() {
	}

	public static String millisToString(long milliseconds) {
		long hours = milliseconds / 3_600_000L;
		long minutes = (milliseconds % 3_600_000L) / 60_000L;
		long seconds = (milliseconds % 60_000L) / 1_000L;
		long milis = (milliseconds % 1_000L);

		return String.format("%s:%02d:%02d.%03d", hours, minutes, seconds, milis);
	}

	public static long parseOpenShiftTimestamp(String timestamp) throws ParseException {
		return OPENSHIFT_DATE_FORMAT.parse(timestamp).getTime();
	}
	
	public static long getJvmUtcStartTime() {
		return JVM_UTC_START_TIME;
	}
	
	private static long initJvmUtcStartTime() {
		long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
		int offset = TimeZone.getDefault().getOffset(new Date().getTime());
		
		return jvmStartTime - offset;
	}
}
