package cz.xtf.openshift.logs;

public class LogCleaner {
	public static String cleanLine(String line) {
		if (line.startsWith("\u001b")) {
			return line + "\u001b[0m";
		}

		return line;
	}
}
