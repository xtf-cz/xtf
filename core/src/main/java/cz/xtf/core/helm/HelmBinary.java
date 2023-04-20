package cz.xtf.core.helm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.CLIUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HelmBinary {

    private final String path;
    private final String helmConfigPath;
    private final String kubeToken;
    private final String namespace;

    public HelmBinary(String path, String kubeToken, String namespace) {
        this.path = path;
        this.kubeToken = kubeToken;
        Path helmConfigFile = Paths.get(path).getParent().resolve(".config");
        try {
            helmConfigFile = Files.createDirectories(helmConfigFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.helmConfigPath = helmConfigFile.toAbsolutePath().toString();
        this.namespace = namespace;
    }

    public HelmBinary(String path, String helmConfigPath, String kubeUsername, String kubeToken, String namespace) {
        this.path = path;
        this.helmConfigPath = helmConfigPath;
        this.kubeToken = kubeToken;
        this.namespace = namespace;
    }

    public String execute(String... args) {
        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put("HELM_CONFIG_HOME", helmConfigPath);
        environmentVariables.put("HELM_KUBEAPISERVER", OpenShiftConfig.url());
        environmentVariables.put("HELM_KUBETOKEN", kubeToken);
        environmentVariables.put("HELM_NAMESPACE", namespace);
        environmentVariables.put("HELM_KUBEINSECURE_SKIP_TLS_VERIFY", "true");
        return CLIUtils.executeCommand(environmentVariables, ArrayUtils.addAll(new String[] { path }, args));
    }

}
