package cz.xtf.core.openshift;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.core.config.OpenShiftConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class OpenShiftBinaryManager {

    private final String openShiftBinaryPath;

    OpenShiftBinaryManager(final String openShiftBinaryPath) {
        this.openShiftBinaryPath = openShiftBinaryPath;
    }

    String getBinaryPath() {
        return openShiftBinaryPath;
    }

    OpenShiftBinary masterBinary(String namespace) {
        Objects.requireNonNull(namespace);

        return getBinary(OpenShiftConfig.masterToken(), OpenShiftConfig.masterUsername(), OpenShiftConfig.masterPassword(),
                OpenShiftConfig.masterKubeconfig(), namespace);
    }

    OpenShiftBinary adminBinary(String namespace) {
        Objects.requireNonNull(namespace);

        return getBinary(OpenShiftConfig.adminToken(), OpenShiftConfig.adminUsername(), OpenShiftConfig.adminPassword(),
                OpenShiftConfig.adminKubeconfig(), namespace);
    }

    private OpenShiftBinary getBinary(String token, String username, String password, String kubeconfig,
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

    private Path createUniqueOcConfigFolder() {
        try {
            return Files.createTempDirectory(getProjectOcConfigDir(), "config");
        } catch (IOException e) {
            throw new IllegalStateException("Temporary folder for oc config couldn't be created", e);
        }
    }

    // TODO: this code is duplicated from OpenShifts.getHomeDir
    // it should be revised together with token management
    // https://github.com/xtf-cz/xtf/issues/464
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

    private Path getProjectOcConfigDir() {
        return Paths.get("tmp/oc/");
    }
}
