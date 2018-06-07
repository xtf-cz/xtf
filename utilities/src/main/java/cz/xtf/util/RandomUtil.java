package cz.xtf.util;

import java.util.Random;

public class RandomUtil {
	public static String generateUniqueId(final String name, final String separator) {
		return name + separator + Integer.toString(Math.abs(new Random().nextInt()), 36);
	}

	public static String generateUniqueId(final String name) {
		return generateUniqueId(name, "-");
	}

	public static String generateUniqueId() {
		return generateUniqueId("", "");
	}
}
