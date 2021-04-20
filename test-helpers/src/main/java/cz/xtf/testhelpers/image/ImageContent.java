package cz.xtf.testhelpers.image;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.PodShell;
import cz.xtf.core.openshift.helpers.ResourceParsers;
import cz.xtf.core.waiting.SimpleWaiter;
import io.fabric8.kubernetes.api.model.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

public class ImageContent {
    public static final String RED_HAT_RELEASE_KEY_2 = "199e2f91fd431d51";
    public static final String RED_HAT_RELEASE_KEY_2_RPM = "gpg-pubkey-fd431d51-4ae0493b";
    public static final String RED_HAT_AUXILIARY_KEY_RPM = "gpg-pubkey-2fa658e0-45700c69";
    public static final String RED_HAT_AUXILIARY_KEY_2_RPM = "gpg-pubkey-d4082792-5b32db75";
    public static final String[] DEFAULT_JAVA_UTILITIES = new String[] { "jjs", "keytool", "orbd", "rmid", "rmiregistry",
            "servertool", "tnameserv", "unpack200", "javac", "appletviewer", "extcheck", "idlj", "jar", "jarsigner",
            "javadoc", "javah", "javap", "jcmd", "jconsole", "jdb", "jdeps", "jhat", "jinfo", "jmap", "jps",
            "jrunscript", "jsadebugd", "jstack", "jstat", "jstat", "jstatd", "native2ascii", "rmic", "schemagen",
            "serialver", "wsgen", "wsimport", "xjc", "pack200" };

    public static ImageContent prepare(OpenShift openShift, String imageUrl) {
        return ImageContent.prepare(openShift, imageUrl, "test-pod", null);
    }

    public static ImageContent prepare(OpenShift openShift, String imageUrl, Map<String, String> envs) {
        return ImageContent.prepare(openShift, imageUrl, "test-pod", null, envs);
    }

    public static ImageContent prepare(OpenShift openShift, String imageUrl, List<String> command) {
        return ImageContent.prepare(openShift, imageUrl, "test-pod", command, Collections.emptyMap());
    }

    public static ImageContent prepare(OpenShift openShift, String imageUrl, String name, List<String> command) {
        return ImageContent.prepare(openShift, imageUrl, name, command, Collections.emptyMap());
    }

    public static ImageContent prepare(OpenShift openShift, String imageUrl, String name, List<String> command,
            Map<String, String> envs) {
        final Pod pod = ImageContent.getPod(imageUrl, name, command, envs);

        openShift.createPod(pod);

        BooleanSupplier bs = () -> {
            Pod p = openShift.getPod(name);
            return p != null && ResourceParsers.isPodRunning(p) && ResourceParsers.isPodReady(p);
        };

        new SimpleWaiter(bs, "Waiting for '" + name + "' pod to be running and ready").timeout(WaitingConfig.timeout())
                .waitFor();

        return ImageContent.prepare(openShift, pod);
    }

    public static ImageContent prepare(OpenShift openShift, Pod pod) {
        return new ImageContent(new PodShell(openShift, pod));
    }

    private static Pod getPod(String imageUrl, String name, List<String> command, Map<String, String> envs) {
        Container container = new ContainerBuilder().withName(name).withImage(imageUrl).build();
        if (command != null)
            container.setCommand(command);
        container.setEnv(
                envs.entrySet().stream().map(e -> new EnvVar(e.getKey(), e.getValue(), null)).collect(Collectors.toList()));

        PodSpec podSpec = new PodSpec();
        podSpec.setContainers(Collections.singletonList(container));

        Pod pod = new Pod();
        pod.setMetadata(new ObjectMetaBuilder().withName(name).build());
        pod.setSpec(podSpec);

        return pod;
    }

    private PodShell shell;

    private boolean md5sumScriptInstalled = false;
    private boolean mavenScriptInstalled = false;

    private ImageContent(PodShell shell) {
        this.shell = shell;
    }

    public PodShell shell() {
        return shell;
    }

    public Map<String, String> runtimeEnvVars() {
        return shell.execute("env").getOutputAsMap("=");
    }

    public List<String> listDirContent(String path) {
        return listDirContent(path, false);
    }

    public List<String> listDirContent(String path, boolean hidden) {
        String flag = hidden ? "-a1 " : "-1 ";
        return shell.executeWithBash("ls " + flag + path).getOutputAsList();
    }

    public List<String> listZipFilesInDir(String path) {
        return shell.executeWithBash("ls -R1 " + path + " | grep '\\.zip$'").getOutputAsList();
    }

    public List<String> listFilesMd5sumInDir(String path) {
        final String md5sumScriptPath = "/tmp/recursive-md5sum.sh";

        if (!md5sumScriptInstalled) {
            shell.executeWithBash("echo \"cd \\$1\" >> " + md5sumScriptPath);
            shell.executeWithBash("echo \"for i in \\$(find . -type f)\" >> " + md5sumScriptPath);
            shell.executeWithBash("echo \"do\" >> " + md5sumScriptPath);
            shell.executeWithBash("echo \"  md5sum \\$i\" >> " + md5sumScriptPath);
            shell.executeWithBash("echo \"done\" >> " + md5sumScriptPath);

            shell.executeWithBash("chmod 777 " + md5sumScriptPath);

            md5sumScriptInstalled = true;
        }

        return shell.executeWithBash(md5sumScriptPath + " " + path).getOutputAsList();
    }

    public String javaVersion() {
        return shell.execute("java", "-version").getError().replaceAll("\n", "")
                .replaceAll("openjdk version \"([0-9]+\\.[0-9]+\\.[0-9]+).*", "$1");
    }

    /**
     * Will return maven version in format X.Y.Z.
     * In RHEL7, maven needs to be added to path via enable script. This is a default behaviour of this method.
     * Behaviour can be changed by changing XTFConfig properties.
     * <ul>
     * <li><i>xtf.maven.activation_script</i> - set to path to maven enable script. Default:
     * <code>/opt/rh/rh-maven35/enable</code>
     * </ul>
     *
     * @return version of Maven in the image.
     */
    public String mavenVersion() {
        // Construct a temporary script and run it. Maven version is parsed from the output.
        final String mavenScriptPath = "/tmp/maven-version.sh";

        if (!mavenScriptInstalled) {
            String mavenActivationScript = getMavenActivationScript(mavenScriptPath);
            shell.executeWithBash(mavenActivationScript);

            shell.executeWithBash("echo mvn --version >> " + mavenScriptPath);
            shell.executeWithBash("chmod 777 " + mavenScriptPath);

            mavenScriptInstalled = true;
        }

        return shell.executeWithBash(mavenScriptPath).getOutput().replaceAll("\n", "")
                .replaceAll(".*Apache Maven ([0-9]+\\.[0-9]+\\.[0-9]+) .*", "$1");
    }

    /**
     * Return script for adding maven activation command to maven script.
     */
    private String getMavenActivationScript(String mavenScriptPath) {
        String mavenEnableScript = XTFConfig.get("xtf.maven.activation_script", "/opt/rh/rh-maven35/enable");
        return "echo . " + mavenEnableScript + " >> " + mavenScriptPath;
    }

    public List<RpmPackage> rpms() {
        return Stream.of(shell.executeWithBash("rpm -qa --info").getOutput().split("(?=Name {8}: )")).map(packageInfo -> {
            Map<String, String> map = new HashMap<>();

            for (String infoLine : packageInfo.split("\n")) {
                String[] splitted = infoLine.split(":", 2);

                if (splitted[0].trim().equals("Description"))
                    break;

                map.put(splitted[0].trim(), splitted[1].trim());
            }
            return new RpmPackage(map.get("Name"), map.get("Version"), map.get("Release"), map.get("Signature"));
        }).collect(Collectors.toList());
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static class RpmPackage {
        private String name;
        private String version;
        private String release;
        private String signature;
    }
}
