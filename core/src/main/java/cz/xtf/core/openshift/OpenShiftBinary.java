package cz.xtf.core.openshift;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
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
            return executeCommand(ArrayUtils.addAll(new String[] { path }, args));
        } else {
            return executeCommand(ArrayUtils.addAll(new String[] { path, "--kubeconfig=" + ocConfigPath }, args));
        }
    }

    // Internal
    private String executeCommand(String... args) {
        ProcessBuilder pb = new ProcessBuilder(args);

        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);

        try {
            Process p = pb.start();

            ExecutorService es = Executors.newFixedThreadPool(2);

            Future<String> out = es.submit(() -> {
                try (InputStream is = p.getInputStream(); StringWriter sw = new StringWriter()) {
                    IOUtils.copy(is, sw);
                    return sw.toString();
                }
            });

            Future<String> err = es.submit(() -> {
                try (InputStream is = p.getErrorStream(); StringWriter sw = new StringWriter()) {
                    IOUtils.copy(is, sw);
                    return sw.toString();
                }
            });

            int result = p.waitFor();

            if (result == 0) {
                return out.get();
            } else {
                log.error("Failed while executing (code {}): {}", result, Arrays.toString(args));
                log.error(err.get());
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("Failed while executing: " + Arrays.toString(args), e);
        }

        return null;
    }
}
