package cz.xtf.openshift;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.docker.OpenShiftNode;
import cz.xtf.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;

public class OpenShiftBinaryClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenShiftBinaryClient.class);

	private static OpenShiftBinaryClient INSTANCE;
	private final OpenShiftContext context;
	private static String ocBinaryPath;

	private static final File WORKDIR = IOUtils.TMP_DIRECTORY.resolve("oc").toFile();
	private static final File CONFIG_FILE = new File(WORKDIR, "oc.config");

	private OpenShiftBinaryClient(OpenShiftContext context) throws IOException, InterruptedException {
		this.context = context;
		ocBinaryPath = getBinary();
	}

	public static OpenShiftBinaryClient getInstance() {
		if (INSTANCE == null) {
			try {
				INSTANCE = new OpenShiftBinaryClient(OpenShiftContext.getContext());
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
	 * @param error
	 * @param args
	 */
	public void executeCommand(String error, String... args) {
		String[] ocArgs = (String[]) ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			executeLocalCommand(error, ocArgs);
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	/**
	 * Executes oc command and returns Process
	 *
	 * @param args
	 * @return Process encapsulating started oc
	 */
	public Process executeCommandNoWait(final String error, String... args) {
		String[] ocArgs = (String[]) ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			return executeLocalCommand(error, false, false, false, ocArgs);
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	public Process executeCommandNoWaitWithOutputAndError(final String error, String... args) {
		String[] ocArgs = (String[]) ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
		try {
			return executeLocalCommand(error, false, true, true, ocArgs);
		} catch (IOException | InterruptedException ex) {
			throw new IllegalArgumentException(error, ex);
		}
	}

	/**
	 * Executes oc command and returns a String
	 *
	 * @param args
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
		String[] ocArgs = (String[]) ArrayUtils.addAll(new String[] {ocBinaryPath, "--config=" + CONFIG_FILE.getAbsolutePath()}, args);
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
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static Process executeLocalCommand(String error, boolean wait, final boolean needOutput, final boolean needError, String... args) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(args);

		if (!needOutput) {
			pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		}
		if (!needError) {
			pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		}

		LOGGER.debug("executing local command: {}", String.join(" ", args));

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
		LOGGER.debug("Master IP: {}", masterIp);
		loginStatic(masterIp, TestConfiguration.masterUsername(), TestConfiguration.masterPassword(), TestConfiguration.getMasterToken());
	}

	public void login(String masterUrl, String masterUserName, String masterPassword, String masterToken) throws IOException, InterruptedException {
		loginStatic(masterUrl, masterUserName, masterPassword, masterToken);
	}

	public void loginDefault() throws IOException, InterruptedException {
		login();
	}

	private static void loginStatic(String masterUrl, String masterUserName, String masterPassword, String masterToken) throws IOException, InterruptedException {

		if (TestConfiguration.openshiftOnline()) {
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
	 * Get oc binary from OSE master server
	 *
	 *  @return path to executable binary file
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private String getBinary() throws IOException, InterruptedException {
		if (SystemUtils.IS_OS_MAC || TestConfiguration.openshiftOnline()) {

			File defaultOcFile = new File(TestConfiguration.ocBinaryLocation());
			if (!defaultOcFile.exists()) {
				LOGGER.error("Hipsterish OS detected, appropriate binary should be downloaded manually and placed to "
					+ TestConfiguration.ocBinaryLocation());
				throw new IllegalArgumentException("OC binary not found for current OS");
			}
			return defaultOcFile.getAbsolutePath();
		}
		final File ocFile = new File(WORKDIR, "oc");

		if (ocFile.exists()) {
			LOGGER.debug("Client file already present");
			final String[] parts = OpenShiftNode.master().executeCommand("wc -c /usr/bin/oc").split(" ");
			if (parts.length == 2 && Integer.parseInt(parts[0]) == ocFile.length()) {
				LOGGER.debug("Local and remote client have the sime size, using the local one");
				return ocFile.getPath();
			}
		}

		if (WORKDIR.exists()) {
			FileUtils.deleteDirectory(WORKDIR);
		}
		if (!WORKDIR.mkdirs()) {
			throw new IOException("Cannot mkdirs " + WORKDIR);
		}

		OpenShiftNode.master().executeCommand("cat /usr/bin/oc", istream -> {
			FileUtils.copyInputStreamToFile(istream, ocFile);
		});
		executeLocalCommand("Error trying to chmod local oc copy", "chmod", "+x", ocFile.getPath());
		return ocFile.getPath();
	}

	@FunctionalInterface
	public interface CommandResultConsumer {
		void consume(InputStream istream) throws IOException;
	}
}
