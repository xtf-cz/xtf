package cz.xtf.core.openshift;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.http.Https;
import cz.xtf.core.namespace.NamespaceManager;
import io.fabric8.kubernetes.client.Config;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenShifts {
    private static final String OCP3_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v3/clients";
    private static final String OCP4_CLIENTS_URL = "https://mirror.openshift.com/pub/openshift-v4";

    public static OpenShift admin() {
        return OpenShifts.admin(NamespaceManager.getNamespace());
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

        return OpenShifts.master(NamespaceManager.getNamespace());

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
        return OpenShiftBinaryManagerFactory.INSTANCE.getOpenShiftBinaryManager().getBinaryPath();
    }

    public static OpenShiftBinary masterBinary() {
        return masterBinary(NamespaceManager.getNamespace());
    }

    public static OpenShiftBinary masterBinary(String namespace) {
        return OpenShiftBinaryManagerFactory.INSTANCE.getOpenShiftBinaryManager().masterBinary(namespace);
    }

    public static OpenShiftBinary adminBinary() {
        return adminBinary(NamespaceManager.getNamespace());
    }

    public static OpenShiftBinary adminBinary(String namespace) {
        return OpenShiftBinaryManagerFactory.INSTANCE.getOpenShiftBinaryManager().adminBinary(namespace);
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

    /**
     * Save oc binary in a folder to use as cache to avoid to download it again.
     * The folder path depends on the OCP version and the download url.
     * The file can be accessed using {@link #getOcFromCache(String, String, File)}.
     * It works only if {@link OpenShiftConfig#isBinaryCacheEnabled()}.
     *
     * @param version String, OCP cluster version.
     * @param ocUrl String, download URL.
     * @param ocTarFile String, workdir file.
     * @throws IOException
     * @deprecated this should have never been made public, can be removed in future versions. It is not used internally by XTF
     */
    @Deprecated
    public static void saveOcOnCache(String version, String ocUrl, File ocTarFile) throws IOException {
        if (OpenShiftConfig.isBinaryCacheEnabled()) {
            File cacheRootFile = new File(OpenShiftConfig.binaryCachePath());
            if (!cacheRootFile.exists() && !cacheRootFile.mkdirs()) {
                throw new IllegalStateException("Cannot mkdirs " + cacheRootFile);
            }
            Path cachePath = getOcCachePath(version, ocUrl);
            Files.createDirectories(cachePath);
            FileUtils.copyFile(ocTarFile, new File(cachePath.toFile(), ocTarFile.getName()));
        }
    }

    /**
     * Retrieve the file from the folder populated by {@link #saveOcOnCache(String, String, File)}.
     *
     * @param version String, OCP cluster version.
     * @param ocUrl String, download URL.
     * @param ocTarFile String, workdir file.
     * @return File, reference to the file, if the cache is not populated, the file is not null, but it doesn't exist.
     * @throws IOException
     * @deprecated this should have never been made public, can be removed in future versions. It is not used internally by XTF
     */
    @Deprecated
    public static File getOcFromCache(String version, String ocUrl, File ocTarFile) throws IOException {
        return new File(getOcCachePath(version, ocUrl).toFile(), ocTarFile.getName());
    }

    /**
     * * @deprecated this should have never been made public, can be removed in future versions. It is not used internally by
     * XTF
     */
    @Deprecated
    private static Path getOcCachePath(String version, String ocUrl) {
        return Paths.get(OpenShiftConfig.binaryCachePath(), version, DigestUtils.md5Hex(ocUrl));
    }

    /**
     * Returns {@link OpenShiftConfig#version()}. If not available then access OpenShift endpoint for a version. Be aware
     * that this operation requires admin role for OpenShift 4 unlike to OpenShift 3.
     *
     * @return Openshift cluster version if configured or detected from cluster, null otherwise
     */
    public static String getVersion() {
        return ClusterVersionInfoFactory.INSTANCE.getClusterVersionInfo().getOpenshiftVersion();
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
                if (getVersion() != null && getVersion().startsWith("3")) {
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
}
