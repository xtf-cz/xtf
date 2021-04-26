package cz.xtf.core.openshift;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jboss.dmr.ModelNode;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.http.Https;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.openshift.api.model.Route;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenShifts {
    private static final String OCP3_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v3/clients/";
    private static final String OCP4_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v4/clients/oc/";

    private static OpenShift adminUtil;
    private static OpenShift masterUtil;

    private static String openShiftBinaryPath;

    public static OpenShift admin() {
        if (adminUtil == null) {
            adminUtil = OpenShifts.admin(OpenShiftConfig.namespace());
        }
        return adminUtil;
    }

    public static OpenShift admin(String namespace) {
        if (StringUtils.isNotEmpty(OpenShiftConfig.adminToken())) {
            return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.adminToken());
        }

        if (StringUtils.isNotEmpty(OpenShiftConfig.adminUsername())) {
            return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.adminUsername(),
                    OpenShiftConfig.adminPassword());
        }

        if (StringUtils.isNotEmpty(OpenShiftConfig.adminKubeconfig())) {
            return OpenShift.get(Paths.get(OpenShiftConfig.adminKubeconfig()), namespace);
        }

        return OpenShift.get(namespace);
    }

    public static OpenShift master() {
        if (masterUtil == null) {
            masterUtil = OpenShifts.master(OpenShiftConfig.namespace());
        }
        return masterUtil;
    }

    public static OpenShift master(String namespace) {
        if (StringUtils.isNotEmpty(OpenShiftConfig.masterToken())) {
            return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.masterToken());
        }

        if (StringUtils.isNotEmpty(OpenShiftConfig.masterUsername())) {
            return OpenShift.get(OpenShiftConfig.url(), namespace, OpenShiftConfig.masterUsername(),
                    OpenShiftConfig.masterPassword());
        }

        if (StringUtils.isNotEmpty(OpenShiftConfig.masterKubeconfig())) {
            return OpenShift.get(Paths.get(OpenShiftConfig.masterKubeconfig()), namespace);
        }

        return OpenShift.get(namespace);
    }

    public static String getBinaryPath() {
        if (openShiftBinaryPath == null) {
            if (OpenShiftConfig.binaryPath() != null) {
                openShiftBinaryPath = OpenShiftConfig.binaryPath();
            } else if (StringUtils.isNotEmpty(OpenShiftConfig.version())) {
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
        return getBinary(OpenShiftConfig.masterToken(), OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword(),
                OpenShiftConfig.masterKubeconfig(), namespace);
    }

    public static OpenShiftBinary adminBinary() {
        return adminBinary(OpenShiftConfig.namespace());
    }

    public static OpenShiftBinary adminBinary(String namespace) {
        return getBinary(OpenShiftConfig.adminToken(), OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword(),
                OpenShiftConfig.adminKubeconfig(), namespace);
    }

    private static OpenShiftBinary getBinary(String token, String username, String password, String kubeconfig,
            String namespace) {
        String ocConfigPath = createUniqueOcConfigFolder().resolve("oc.config").toAbsolutePath().toString();
        OpenShiftBinary openShiftBinary;

        if (StringUtils.isNotEmpty(token) || StringUtils.isNotEmpty(username)) {
            // If we are using a token or username/password, we start with a nonexisting kubeconfig and do an "oc login"
            openShiftBinary = new OpenShiftBinary(OpenShifts.getBinaryPath(), ocConfigPath);
            if (StringUtils.isNotEmpty(token)) {
                openShiftBinary.login(OpenShiftConfig.url(), token);
            } else {
                openShiftBinary.login(OpenShiftConfig.url(), username, password);
            }
        } else {
            // If we are using an existing kubeconfig (or a default kubeconfig), we copy the original kubeconfig
            if (StringUtils.isNotEmpty(kubeconfig)) {
                // We copy the specified kubeconfig
                try {
                    Files.copy(Paths.get(kubeconfig), Paths.get(ocConfigPath), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // We copy the default ~/.kube/config
                File defaultKubeConfig = Paths.get(getHomeDir(), ".kube", "config").toFile();
                if (defaultKubeConfig.isFile()) {
                    try {
                        Files.copy(defaultKubeConfig.toPath(), Paths.get(ocConfigPath), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException(defaultKubeConfig.getAbsolutePath()
                            + " does not exist and no other OpenShift master option specified");
                }
            }
            openShiftBinary = new OpenShiftBinary(OpenShifts.getBinaryPath(), ocConfigPath);
        }

        if (StringUtils.isNotEmpty(namespace)) {
            openShiftBinary.project(namespace);
        }

        return openShiftBinary;
    }

    private static String getHomeDir() {
        String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            File f = new File(home);
            if (f.exists() && f.isDirectory()) {
                return home;
            }
        }
        return System.getProperty("user.home", ".");
    }

    private static String downloadOpenShiftBinary(String version) {
        String systemType = SystemUtils.IS_OS_MAC ? "macosx" : "linux";
        String clientLocation = null;
        String ocFileName = "oc.tar.gz";

        if (version.startsWith("3")) {
            clientLocation = String.format(OCP3_CLIENTS_URL + "%s/%s/", version, systemType);
        } else {
            if (StringUtils.isNotEmpty(OpenShiftConfig.version())) {
                clientLocation = String.format(OCP4_CLIENTS_URL + "%s/%s/", version, systemType);
            } else {
                if (SystemUtils.IS_OS_MAC) {
                    systemType = "mac";
                    ocFileName = "oc.zip";
                } else {
                    systemType = "linux";
                    ocFileName = "oc.tar";
                }
                final Optional<Route> downloadsRouteOptional = Optional
                        .ofNullable(admin("openshift-console").getRoute("downloads"));
                final Route downloads = downloadsRouteOptional
                        .orElseThrow(() -> new IllegalStateException("We are not able to find download link for OC binary."));
                clientLocation = String.format("https://" + downloads.getSpec().getHost() + "/amd64/%s/", systemType);
                return downloadOpenShiftBinaryInternal(version, ocFileName, clientLocation, true);
            }
        }
        return downloadOpenShiftBinaryInternal(version, ocFileName, clientLocation, false);
    }

    private static String downloadOpenShiftBinaryInternal(final String version, final String ocFileName,
            final String clientLocation, final boolean trustAll) {
        int code = Https.httpsGetCode(clientLocation);

        if (code != 200) {
            throw new IllegalStateException("Client binary for version " + version + " isn't available at " + clientLocation);
        }

        File workdir = ocBinaryFolder();

        // Download and extract client
        File ocTarFile = new File(workdir, "oc.tar.gz");
        File ocFile = new File(workdir, "oc");

        final String ocUrl = clientLocation + ocFileName;
        try {
            URL requestUrl = new URL(ocUrl);

            if (trustAll) {
                Https.copyHttpsURLToFile(requestUrl, ocTarFile, 20_000, 300_000);
            } else {
                FileUtils.copyURLToFile(requestUrl, ocTarFile, 20_000, 300_000);
            }

            executeCommand("tar", "-xf", ocTarFile.getPath(), "-C", workdir.getPath());
            FileUtils.deleteQuietly(ocTarFile);

            return ocFile.getAbsolutePath();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to download and extract oc binary from " + ocUrl, e);
        }
    }

    /**
     * Returns {@link OpenShiftConfig#version()}. If not available then access OpenShift endpoint for a version. Be aware
     * that this operation requires admin role for OpenShift 4 unlike to OpenShift 3.
     *
     * @return Openshift cluster version
     */
    public static String getVersion() {
        if (StringUtils.isNotEmpty(OpenShiftConfig.version())) {
            return OpenShiftConfig.version();
        }
        final String ocp3UrlVersion = OpenShiftConfig.url() + "/version/openshift";
        if (Https.getCode(ocp3UrlVersion) == 200) { // for OCP 3
            String content = Https.httpsGetContent(ocp3UrlVersion);
            return ModelNode.fromJSONString(content).get("gitVersion").asString().replaceAll("^v(.*)", "$1");
        } else { // for OCP version > 3
            final CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("config.openshift.io")
                    .withPlural("clusterversions")
                    .withScope("NonNamespaced")
                    .withVersion("v1")
                    .build();
            return toString(toMap(toMap(admin().customResource(crdContext).get("version"), "status"), "desired"), "version");
        }
    }

    private static String toString(Object map, String key) {
        return (String) ((Map) map).get(key);
    }

    private static Map toMap(Object map, String key) {
        return (Map) ((Map) map).get(key);
    }

    public static String getMasterToken() {
        return getToken(OpenShiftConfig.masterToken(), OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword(),
                OpenShiftConfig.masterKubeconfig());
    }

    public static String getAdminToken() {
        return getToken(OpenShiftConfig.adminToken(), OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword(),
                OpenShiftConfig.adminKubeconfig());
    }

    private static String getToken(String token, String username, String password, String kubeconfig) {
        if (StringUtils.isNotEmpty(token)) {
            return token;
        }

        // Attempt to get the token via HTTP basic auth:
        if (StringUtils.isNotEmpty(username)) {
            HttpsURLConnection connection = null;
            try {
                if (OpenShiftConfig.version().startsWith("3")) {
                    connection = Https.getHttpsConnection(new URL(
                            OpenShiftConfig.url()
                                    + "/oauth/authorize?response_type=token&client_id=openshift-challenging-client"));
                } else {
                    connection = Https.getHttpsConnection(new URL("https://oauth-openshift.apps." +
                            StringUtils.substringBetween(OpenShiftConfig.url(), "api.", ":")
                            + "/oauth/authorize?response_type=token&client_id=openshift-challenging-client"));
                }
                String encoded = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
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
                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        if (StringUtils.isNotEmpty(kubeconfig)) {
            try {
                Config config = Config.fromKubeconfig(null,
                        new String(Files.readAllBytes(Paths.get(kubeconfig)), StandardCharsets.UTF_8), kubeconfig);
                return config.getOauthToken();
            } catch (IOException e) {
                log.error("Unable to retrieve token from kubeconfig: {} ", kubeconfig, e);
            }
            return null;
        }

        File defaultKubeConfig = Paths.get(getHomeDir(), ".kube", "config").toFile();
        try {
            Config config = Config.fromKubeconfig(null,
                    new String(Files.readAllBytes(defaultKubeConfig.toPath()), StandardCharsets.UTF_8),
                    defaultKubeConfig.getAbsolutePath());
            return config.getOauthToken();
        } catch (IOException e) {
            log.error("Unable to retrieve token from default kubeconfig: {} ", defaultKubeConfig, e);
        }
        return null;
    }

    private static void executeCommand(String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        int result = pb.start().waitFor();

        if (result != 0) {
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
