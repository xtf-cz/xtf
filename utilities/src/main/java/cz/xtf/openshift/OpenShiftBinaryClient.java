package cz.xtf.openshift;

import cz.xtf.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

import org.jboss.dmr.ModelNode;

import cz.xtf.TestConfiguration;
import cz.xtf.io.IOUtils;
import org.eclipse.jgit.util.StringUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class OpenShiftBinaryClient {
	private static final String CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v3/clients/";

	private static OpenShiftBinaryClient INSTANCE;
	private static String ocBinaryPath;

	private static final File WORKDIR = IOUtils.TMP_DIRECTORY.resolve("oc").toFile();
	private static final File CONFIG_FILE = new File(WORKDIR, "oc.config");

	private OpenShiftBinaryClient() throws IOException, InterruptedException {
		ocBinaryPath = getBinary();
	}

	public static OpenShiftBinaryClient getInstance() {
		if (INSTANCE == null) {
			try {
				INSTANCE = new OpenShiftBinaryClient();
				//call oc login to create ~/.kube/config
				login();
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException("OpenShift Master URL is malformed", ex);
			} catch (IOException ex) {
				throw new IllegalArgumentException("Can't get oc binary", ex);
			} catch (InterruptedException ex) {
				throw new IllegalArgumentException("Init failed", ex);
			}
		}
		return INSTANCE;
	}

	public void project(String projectName) {
		executeCommand("oc project failed", "project", projectName);
	}

	/**
	 * Expose executeCommand with oc binary preset
	 *
	 * @param error error message on failure
	 * @param args command arguments
	 */
	public void executeCommand(String error, String... args) {
		String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			executeLocalCommand(error, ocArgs);
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	/**
	 * Executes oc command and returns Process
	 *
	 * @param args command arguments
	 * @return Process encapsulating started oc
	 */
	public Process executeCommandNoWait(final String error, String... args) {
		String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			return executeLocalCommand(error, false, false, false, ocArgs);
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	public Process executeCommandNoWaitWithOutputAndError(final String error, String... args) {
		String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			return executeLocalCommand(error, false, true, true, ocArgs);
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	/**
	 * Executes oc command and returns a String
	 *
	 * @param args command arguments
	 * @return Process encapsulating started oc
	 */
	public String executeCommandWithReturn(final String error, String... args) {
		String[] ocArgs = (String[]) ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			final Process process = executeLocalCommand(error, false, true, false,
					ocArgs);
			try (final InputStream is = process.getInputStream();
					final StringWriter sw = new StringWriter()) {
				org.apache.commons.io.IOUtils.copy(is, sw);
				return sw.toString();
			}
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	/**
	 * Executes oc command and consume output
	 */
	public void executeCommandAndConsumeOutput(final String error, CommandResultConsumer consumer, String... args) {
		String[] ocArgs = ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			final Process process = executeLocalCommand(error, false, true, false,
					ocArgs);
			try (final InputStream is = process.getInputStream()) {
				consumer.consume(is);
			}
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	/**
	 * Execute command on local FS
	 *
	 * @param error error message on failure
	 * @param wait wait for process completion
	 * @param args command arguments
	 */
	private static Process executeLocalCommand(String error, boolean wait, final boolean needOutput, final boolean needError, String... args) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(args);

		if (!needOutput) {
			pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		}
		if (!needError) {
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		}

		log.debug("executing local command: {}", String.join(" ", args));

		final Process process = pb.start();
		if (wait) {
			if (process.waitFor() != 0) {
				throw new IllegalStateException(error);
			}
		}
		return process;
	}

	public static Process executeLocalCommand(String error, String... args) throws IOException, InterruptedException {
		return executeLocalCommand(error, true, false, false, args);
	}

	private static void login() throws IOException, InterruptedException {
		String masterIp = TestConfiguration.openshiftOnline() ? TestConfiguration.masterUrl() : "https://" + InetAddress.getByName(new URL(TestConfiguration.masterUrl()).getHost()).getHostAddress() + ":8443";
		log.debug("Master IP: {}", masterIp);
		loginStatic(masterIp, TestConfiguration.masterUsername(), TestConfiguration.masterPassword(), TestConfiguration.getMasterToken());
	}

	public void login(String masterUrl, String masterUserName, String masterPassword, String masterToken) throws IOException, InterruptedException {
		loginStatic(masterUrl, masterUserName, masterPassword, masterToken);
	}

	public void loginDefault() throws IOException, InterruptedException {
		login();
	}

	private static void loginStatic(String masterUrl, String masterUserName, String masterPassword, String masterToken) throws IOException, InterruptedException {

		if (masterToken != null) {
			executeLocalCommand("oc login failed!", ocBinaryPath, "login", masterUrl,
					"--token=" + masterToken,
					"--config=" + CONFIG_FILE);
		} else {
			executeLocalCommand("oc login failed!", ocBinaryPath, "login", masterUrl,
					"--username=" + masterUserName,
					"--password=" + masterPassword,
					"--insecure-skip-tls-verify=true",
					"--config=" + CONFIG_FILE);
		}
	}

	/**
	 * Get oc binary from {@link OpenShiftBinaryClient#CLIENTS_URL}
	 *
	 * @return path to executable binary file or default oc command if not found
	 */
	private String getBinary() throws IOException, InterruptedException {
		String systemType = SystemUtils.IS_OS_MAC ? "macosx" : "linux";
		String clientLocation = null;

		int code = -1;
		try {
			String openShiftVersion = getOpenshiftVersion();
			clientLocation = String.format(CLIENTS_URL + "%s/%s/", openShiftVersion, systemType);
			code = HttpClient.get(clientLocation).code();
		} catch (IOException e) {
			log.warn("Failed to retrieve code from binary clients mirror. Falling back to {}.", TestConfiguration.ocBinaryLocation());
		}

		// Fall back to default oc if version is not found
		if(code != 200) {
			String binaryLocation = TestConfiguration.ocBinaryLocation();
			if(!Files.exists(Paths.get(binaryLocation))) throw new IllegalStateException("Unable to find executable oc binary!");

			return TestConfiguration.ocBinaryLocation();
		}

		if (WORKDIR.exists()) {
			FileUtils.deleteDirectory(WORKDIR);
		}
		if (!WORKDIR.mkdirs()) {
			throw new IOException("Cannot mkdirs " + WORKDIR);
		}

		// Download and extract client
		File ocTarFile = new File(WORKDIR, "oc.tar.gz");
		File ocFile = new File(WORKDIR, "oc");

		URL requestUrl = new URL(clientLocation + "oc.tar.gz");
		FileUtils.copyURLToFile(requestUrl, ocTarFile, 20_000, 300_000);

		executeLocalCommand("Error trying to extract client", "tar", "-xf", ocTarFile.getPath(), "-C", WORKDIR.getPath());
		FileUtils.deleteQuietly(ocTarFile);

		return ocFile.getPath();
	}

	private String getOpenshiftVersion() throws IOException {
		String version = TestConfiguration.openshiftVersion();
		if(StringUtils.isEmptyOrNull(version)) {
			String versionJson = HttpClient.get(TestConfiguration.masterUrl() + "/version/openshift").response();
			ModelNode versionNode = ModelNode.fromJSONString(versionJson);
			version = versionNode.get("gitVersion").asString().replaceAll("^v(.*)", "$1");
		}
		return version;
	}

	public Path getOcBinaryPath() {
		return Paths.get(ocBinaryPath);
	}

	public Path getOcConfigPath() {
		return Paths.get(CONFIG_FILE.getAbsolutePath());
	}

	@FunctionalInterface
	public interface CommandResultConsumer {
		void consume(InputStream istream) throws IOException;
	}
}
