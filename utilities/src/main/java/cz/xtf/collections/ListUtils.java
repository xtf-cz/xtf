package cz.xtf.collections;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ListUtils {
	public static <T> T randomElement(List<T> list) {
		return list.get(ThreadLocalRandom.current().nextInt(list.size()));
	}
}
