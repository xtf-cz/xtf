package cz.xtf.core.openshift;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import org.jboss.dmr.ModelNode;

import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.http.Https;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenShifts {
	private static final String CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v3/clients/";

	private static OpenShift adminUtil;
	private static OpenShift masterUtil;

	private static String openShiftBinaryPath;

	public static OpenShift admin() {
		if(adminUtil == null) {
			adminUtil = OpenShifts.admin(OpenShiftConfig.namespace());
		}
		return adminUtil;
	}

	public static OpenShift admin(String namespace) {
		if(OpenShiftConfig.adminToken() == null) {
			return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword());
		} else {
			return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.adminToken());
		}
	}

	public static OpenShift master() {
		if(masterUtil == null) {
			masterUtil = OpenShifts.master(OpenShiftConfig.namespace());
		}
		return masterUtil;
	}

	public static OpenShift master(String namespace) {
		if(OpenShiftConfig.token() == null) {
			return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword());
		} else {
			return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.token());
		}
	}

	public static String getBinaryPath() {
		if(openShiftBinaryPath == null) {
			if (OpenShiftConfig.binaryPath() != null) {
				openShiftBinaryPath = OpenShiftConfig.binaryPath();
			} else if (OpenShiftConfig.version() != null) {
				openShiftBinaryPath = OpenShifts.downloadOpenShiftBinary(OpenShiftConfig.version());
			} else {
				openShiftBinaryPath = OpenShifts.downloadOpenShiftBinary(OpenShifts.getVersion());
			}
		}
		return openShiftBinaryPath;
	}

	public static OpenShiftBinary masterBinary() {
		return masterBinary(OpenShiftConfig.namespace());
	}

	public static OpenShiftBinary masterBinary(String namespace) {
		String ocConfigPath = createUniqueOcConfigFolder().resolve("oc.config").toAbsolutePath().toString();

		OpenShiftBinary openShiftBinary = new OpenShiftBinary(OpenShifts.getBinaryPath(), ocConfigPath);

		if (OpenShiftConfig.token() == null) {
			openShiftBinary.login(OpenShiftConfig.url(), OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword());
		} else {
			openShiftBinary.login(OpenShiftConfig.url(), OpenShiftConfig.token());
		}

		openShiftBinary.project(namespace);

		return openShiftBinary;
	}

	public static String getVersion() {
		String content = Https.httpsGetContent(OpenShiftConfig.url() + "/version/openshift");
		return ModelNode.fromJSONString(content).get("gitVersion").asString().replaceAll("^v(.*)", "$1");
	}

	public static String getMasterToken() {
		if(OpenShiftConfig.token() != null) {
			return OpenShiftConfig.token();
		} else {
			HttpsURLConnection connection = null;
			try {
				connection = Https.getHttpsConnection(new URL(OpenShiftConfig.url() + "/oauth/authorize?response_type=token&client_id=openshift-challenging-client"));
				String encoded = Base64.getEncoder().encodeToString((OpenShiftConfig.masterUsername() + ":" + OpenShiftConfig.masterPassword()).getBytes(StandardCharsets.UTF_8));
				connection.setRequestProperty("Authorization", "Basic " + encoded);
				connection.setInstanceFollowRedirects(false);

				connection.connect();
				Map<String, List<String>> headers = connection.getHeaderFields();
				connection.disconnect();

				List<String> location = headers.get("Location");
				if (location != null) {
					Optional<String> acces_token = location.stream().filter(s -> s.contains("access_token")).findFirst();
					return acces_token.map(s -> StringUtils.substringBetween(s, "#access_token=", "&")).orElse(null);
				}
			} catch (IOException ex) {
				log.error("Unable to retrieve token from Location header: {} ", ex.getMessage());
			} finally {
				if (connection != null) connection.disconnect();
			}
			return null;
		}
	}

	private static String downloadOpenShiftBinary(String version) {
		String systemType = SystemUtils.IS_OS_MAC ? "macosx" : "linux";
		String clientLocation = String.format(CLIENTS_URL + "%s/%s/", version, systemType);

		int code = Https.httpsGetCode(clientLocation);

		if(code != 200) {
			throw new IllegalStateException("Client binary for version " + version + " isn't available at " + clientLocation);
		}

		File workdir = ocBinaryFolder();

		// Download and extract client
		File ocTarFile = new File(workdir, "oc.tar.gz");
		File ocFile = new File(workdir, "oc");

		try {
			URL requestUrl = new URL(clientLocation + "oc.tar.gz");
			FileUtils.copyURLToFile(requestUrl, ocTarFile, 20_000, 300_000);

			executeCommand("tar", "-xf", ocTarFile.getPath(), "-C", workdir.getPath());
			FileUtils.deleteQuietly(ocTarFile);

			return ocFile.getAbsolutePath();
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException("Failed to download and extract oc binary from " + clientLocation + "oc.tar.gz", e);
		}
	}

	private static void executeCommand(String... args) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(args);

		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		int result = pb.start().waitFor();

		if(result != 0) {
			throw new IOException("Failed to execute: " + Arrays.toString(args));
		}
	}

	private static Path createUniqueOcConfigFolder() {
		try {
			return Files.createTempDirectory(ocBinaryFolder().toPath(), "config");
		} catch (IOException e) {
			throw new IllegalStateException("Temporary folder for oc config couldn't be created", e);
		}
	}

	private static File ocBinaryFolder() {
		File workdir = new File(Paths.get("tmp/oc").toAbsolutePath().toString());
		if (workdir.exists()) {
			return workdir;
		}
		if (!workdir.mkdirs()) {
			throw new IllegalStateException("Cannot mkdirs " + workdir);
		}
		return workdir;
	}
}