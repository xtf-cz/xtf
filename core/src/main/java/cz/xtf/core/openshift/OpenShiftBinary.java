package cz.xtf.core.openshift;

import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenShiftBinary {
    private final String path;

    @Getter
    private String ocConfigPath;

    public OpenShiftBinary(String path) {
        this.path = path;
    }

    public OpenShiftBinary(String path, String ocConfigPath) {
        this(path);
        this.ocConfigPath = ocConfigPath;
    }

    public void login(String url, String token) {
        this.execute("login", url, "--insecure-skip-tls-verify=true", "--token=" + token);
    }

    public void login(String url, String username, String password) {
        this.execute("login", url, "--insecure-skip-tls-verify=true", "-u", username, "-p", password);
    }

    /**
     * Apply configuration file in the specified namespace.
     * Delegates to `oc apply --filename='sourcepath' --namespace='namespace'`
     * 
     * @param sourcePath path to configration file
     * @param namespace namespace
     */
    public void apply(String namespace, String sourcePath) {
        this.execute("apply", "--namespace=" + namespace, "--filename=" + sourcePath);
    }

    /**
     * Apply configuration file. Delegates to `oc apply --filename='sourcepath`
     * 
     * @param sourcePath path to configration file
     */
    public void apply(String sourcePath) {
        this.execute("apply", "--filename=" + sourcePath);
    }

    /**
     * Apply configuration files in the order they appear in the list
     * 
     * @param sourcePaths list of paths to configuration files
     */
    public void apply(List<String> sourcePaths) {
        for (String sourcePath : sourcePaths) {
            apply(sourcePath);
        }
    }

    /**
     * Apply configuration files in the order they appear in the list, using supplied namespace.
     * 
     * @param namespace namespace in which the configuration files should be applied
     * @param sourcePaths list of paths to configuration files
     */
    public void apply(String namespace, List<String> sourcePaths) {
        for (String sourcePath : sourcePaths) {
            apply(namespace, sourcePath);
        }
    }

    public void project(String projectName) {
        this.execute("project", projectName);
    }

    public void startBuild(String buildConfig, String sourcePath) {
        this.execute("start-build", buildConfig, "--from-dir=" + sourcePath);
    }

    // Common method for any oc command call
    public String execute(String... args) {
        if (ocConfigPath == null) {
            return CLIUtils.executeCommand(ArrayUtils.addAll(new String[] { path }, args));
        } else {
            return CLIUtils.executeCommand(ArrayUtils.addAll(new String[] { path, "--kubeconfig=" + ocConfigPath }, args));
        }
    }
}
