package cz.xtf.jms;

import java.util.ArrayList;

//TODO:refactor to better name or integrate to {@link JmsClient}
public class Helper {

	public static String createDataSize(int msgSize) {
		// Java chars are 2 bytes
		msgSize = msgSize * 1024;
		msgSize = msgSize / 2;
		StringBuilder sb = new StringBuilder(msgSize);
		for (int i = 0; i < msgSize; i++) {
			sb.append('m');
		}
		return sb.toString();
	}

	public static ArrayList<String> createObjectMessage(ArrayList<String> list) {
		list.add("Hello");
		list.add("Fuse");
		return list;
	}


	public static ArrayList<String> createObjectMessage() {
		ArrayList<String> list = new ArrayList<>();
		list.add("Hello");
		list.add("Fuse");
		return list;
	}

}
