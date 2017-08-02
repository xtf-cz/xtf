package cz.xtf.jacoco;

import org.apache.commons.codec.binary.Base64;

import org.jboss.dmr.ModelNode;

import cz.xtf.TestConfiguration;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;

public class JacocoUtil {

	private static final String podJacocoJavaOptions = "-javaagent:/etc/secrets/jacoco/jacocoagent.jar=destfile=/tmp/jacoco.exec,jmx=true,dumponexit=false";

	/**
	 * Generate a jacoco arg line to put jacoco.exec into a tmp file in tmp/jacoco/prefix.xxxx.jacoco.exec
	 * @param name
	 * @return
	 */
	public static String generateJacocoConfiguration(final String name) {
		Paths.get("tmp", "jacoco").toFile().mkdirs();
		try {
			Path tmp = Files.createTempFile(Paths.get("tmp", "jacoco"), name + ".", ".jacoco.exec");
			return "-javaagent:" + TestConfiguration.jacocoPath() + "/lib/jacocoagent.jar=destfile=" + tmp.toAbsolutePath();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Secret createOrGetJacocoSecret() {
		OpenshiftUtil openshift = OpenshiftUtil.getInstance();

		// Create the jacoco secret with the jacoco agent if it doesn't exist yet
		Secret jacocoSecret = openshift.withDefaultUser(c -> c.secrets().list().getItems().stream().filter(s -> "jacoco".equals(s.getMetadata().getName())).findAny())
				.orElseGet(() -> {
					SecretBuilder sb = new SecretBuilder();
					sb.withNewMetadata().withName("jacoco").endMetadata();

					try {
						sb.addToData("jacocoagent.jar", Base64.encodeBase64String(Files.readAllBytes(Paths.get(TestConfiguration.jacocoPath(), "lib", "jacocoagent.jar"))));
						Secret ret = sb.build();

						openshift.createSecret(ret);

						return ret;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});

		return jacocoSecret;
	}

	public static void addJacocoToDeploymentConfig(DeploymentConfigBuilder builder, String javaOptionsEnv) {

		Secret jacocoSecret = createOrGetJacocoSecret();

		builder.podTemplate().addLabel("jacoco", "true");
		builder.podTemplate().container().addVolumeMount("jacoco", "/etc/secrets/jacoco", true);
		builder.podTemplate().addSecretVolume("jacoco", jacocoSecret.getMetadata().getName());
		builder.podTemplate().container().envVar(javaOptionsEnv, podJacocoJavaOptions);
	}

	public static void addJacocoToDeploymentConfig(ModelNode deploymentConfig, String javaOptionsEnv) {
		Secret jacocoSecret = createOrGetJacocoSecret();

		// add jacoco label
		deploymentConfig.get("spec").get("template").get("metadata").get("labels").get("jacoco").set("true");

		// add volume
		ModelNode volume = deploymentConfig.get("spec").get("template").get("spec").get("volumes").addEmptyObject();
		volume.get("name").set("jacoco");
		volume.get("secret").get("secretName").set(jacocoSecret.getMetadata().getName());

		Consumer<ModelNode> addJacocoToContainer = container -> {
			// adding env
			ModelNode env = container.get("env");
			ModelNode envItem = env.addEmptyObject();

			envItem.get("name").set(javaOptionsEnv);
			envItem.get("value").set(podJacocoJavaOptions);

			// adding volume mount
			ModelNode volumeMount = container.get("volumeMounts").addEmptyObject();
			volumeMount.get("name").set("jacoco");
			volumeMount.get("readOnly").set(true);
			volumeMount.get("mountPath").set("/etc/secrets/jacoco");
		};

		final List<ModelNode> containers = deploymentConfig.get("spec").get("template").get("spec").get("containers").asList();

		if (containers.size() == 0) {
			ModelNode container = deploymentConfig.get("spec").get("template").get("spec").get("containers").addEmptyObject();
			addJacocoToContainer.accept(container);
		}
		else {
			for (ModelNode container : containers) {
				addJacocoToContainer.accept(container);
			}
		}
	}
}
