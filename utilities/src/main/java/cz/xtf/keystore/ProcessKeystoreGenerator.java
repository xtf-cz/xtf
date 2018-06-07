package cz.xtf.keystore;

import cz.xtf.TestConfiguration;
import org.apache.commons.io.FileUtils;

import cz.xtf.docker.OpenShiftNode;
import cz.xtf.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.Getter;

/**
 * Class for generating keystores signed by one common generated ca
 */
public class ProcessKeystoreGenerator {

	@Getter
	private static Path caDir;

	@Getter
	private static Path truststore;

	static {
		try {
			IOUtils.TMP_DIRECTORY.toFile().mkdirs();
			caDir = Files.createTempDirectory(IOUtils.TMP_DIRECTORY, "ca");

			// Generate key and cert
			processCall(caDir, "openssl", "req", "-new", "-newkey", "rsa:4096", "-x509", "-keyout", "ca-key.pem", "-out", "ca-certificate.pem", "-days", "365", "-passout", "pass:password", "-subj", "/C=CZ/ST=CZ/L=Brno/O=QE/CN=xtf.ca");

			// Generate truststore with ca cert
			processCall(caDir, "keytool", "-import", "-noprompt", "-keystore", "truststore", "-file", "ca-certificate.pem", "-alias", "xtf.ca", "-storepass", "password");

			// Import openshift router cert to truststore
			if(!TestConfiguration.openshiftOnline()) {
				String routerCrt = OpenShiftNode.master().executeCommand("sudo cat /etc/origin/master/ca.crt");
				FileUtils.writeStringToFile(caDir.resolve("openshift-router.pem").toFile(), routerCrt);
				processCall(caDir, "keytool", "-import", "-noprompt", "-keystore", "truststore", "-file", "openshift-router.pem", "-alias", "openshift-router", "-storepass", "password");
			}

			truststore = caDir.resolve("truststore");
		} catch (IOException e) {
			throw new IllegalStateException("Failed to initialize ca", e);
		}
	}

	public static Path generateKeystore(String hostname) {
		return generateKeystore(hostname, hostname, false);
	}
	
	public static Path generateKeystore(String hostname, String keyAlias) {
		return generateKeystore(hostname, keyAlias, false); 
	}

	public static Path generateKeystore(String hostname, String keyAlias, boolean deleteCaFromKeyStore) {
		String keystore = hostname + ".keystore";

		if (caDir.resolve(keystore).toFile().exists()) {
			return caDir.resolve(keystore);
		}

		processCall(caDir, "keytool", "-genkeypair", "-keyalg", "RSA", "-noprompt", "-alias", keyAlias, "-dname", "CN=" + hostname + ", OU=TF, O=XTF, L=Brno, S=CZ, C=CZ", "-keystore", keystore, "-storepass", "password", "-keypass",
				"password");

		processCall(caDir, "keytool", "-keystore", keystore, "-certreq", "-alias", keyAlias, "--keyalg", "rsa", "-file", hostname + ".csr", "-storepass", "password");

		processCall(caDir, "openssl", "x509", "-req", "-CA", "ca-certificate.pem", "-CAkey", "ca-key.pem", "-in", hostname + ".csr", "-out", hostname + ".cer", "-days", "365", "-CAcreateserial", "-passin", "pass:password");

		processCall(caDir, "keytool", "-import", "-noprompt", "-keystore", keystore, "-file", "ca-certificate.pem", "-alias", "xtf.ca", "-storepass", "password");

		processCall(caDir, "keytool", "-import", "-keystore", keystore, "-file", hostname + ".cer", "-alias", keyAlias, "-storepass", "password");
		
		if (deleteCaFromKeyStore) {
			processCall(caDir, "keytool", "-delete", "-noprompt", "-alias", "xtf.ca", "-keystore", keystore, "-storepass", "password");
		}

		return caDir.resolve(keystore);
	}

	public static CertPaths generateCerts(String hostname) {
		String keystore = hostname + ".keystore";

		generateKeystore(hostname);

		// export cert as CN.keystore.pem
		processCall(caDir, "keytool", "-exportcert", "-rfc", "-keystore", keystore, "-alias", hostname, "-storepass", "password", "-file", keystore + ".pem");

		// export key as CN.keystore.p12
		processCall(caDir, "keytool", "-importkeystore", "-noprompt", "-srckeystore", keystore, "-destkeystore", keystore + ".p12", "-deststoretype", "PKCS12", "-srcalias", hostname, "-deststorepass", "password", "-destkeypass", "password",
				"-srcstorepass", "password");

		// convert to CN.keystore.keywithattrs.pem
		processCall(caDir, "openssl", "pkcs12", "-in", keystore + ".p12", "-nodes", "-nocerts", "-out", keystore + ".keywithattrs.pem", "-passin", "pass:password");

		// Remove the Bag attributes to CN.keystore.key.pem
		processCall(caDir, "openssl", "rsa", "-in", keystore + ".keywithattrs.pem", "-out", keystore + ".key.pem");

		return new CertPaths(
				caDir.resolve("ca-certificate.pem"),
				caDir.resolve("truststore"),
				caDir.resolve(keystore),
				caDir.resolve(keystore + ".key.pem"),
				caDir.resolve(keystore + ".pem"));
	}

	private static void processCall(Path cwd, String... args) {
		ProcessBuilder pb = new ProcessBuilder(args);

		pb.directory(cwd.toFile());
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		int result = -1;

		try {
			result = pb.start().waitFor();
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException("Failed executing " + String.join(" ", args));
		}

		if (result != 0) {
			throw new IllegalStateException("Failed executing " + String.join(" ", args));
		}
	}

	public static class CertPaths {

		public CertPaths(Path caPem, Path truststore, Path keystore, Path keyPem, Path certPem) {
			this.caPem = caPem;
			this.truststore = truststore;
			this.keystore = keystore;
			this.keyPem = keyPem;
			this.certPem = certPem;
		}

		public Path caPem;
		public Path truststore;
		public Path keystore;
		public Path keyPem;
		public Path certPem;
	}
}
