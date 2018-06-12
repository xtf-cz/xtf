package cz.xtf.keystore;

import java.nio.file.Path;

public class AddKeyApplication {

	public static void main(String[] args) {
		XTFKeyStore store = XTFKeyStore.getInstance();

		System.out.println("Existing aliases:");
		printAliases(store);

		if (args.length < 1) {
			System.out.println("Usage: java AddKeyApplication <private_key_alias>");
			System.exit(1);
		}

		store.addPrivateKey(args[0], "xtf.ca");

		System.out.println("New aliases list:");
		printAliases(store);

		Path exportPath = store.export();
		System.out.println("The modified keystore is available at " + exportPath.toString());
	}

	private static void printAliases(XTFKeyStore store) {
		for (String alias : store.getAliases()) {
			System.out.print("  ");
			System.out.println(alias);
		}
		System.out.println();
	}
}
