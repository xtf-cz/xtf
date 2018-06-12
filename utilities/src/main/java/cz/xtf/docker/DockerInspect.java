package cz.xtf.docker;

import com.google.gson.Gson;
import cz.xtf.openshift.OpenShiftUtil;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.wait.Waiters;
import io.fabric8.openshift.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * DockerInspect pulls image to master and parses the output of its inspect command.
 */
@Slf4j
public class DockerInspect {

	/**
	 * Pulls given image to master and runs docker inspect command against it.
	 * Result is stored and parsed by object methods.
	 *
	 * @param image image url to initialize new instance of this object
	 * @return new instance
	 */
	@Deprecated
	public static DockerInspect from(String image) {
		log.warn("DockerInspect.from(String image) is deprecated!");
		log.info("Pulling image '{}' to master", image);
		OpenShiftNode.master().executeCommand("sudo docker pull " + image);

		final String result = OpenShiftNode.master().executeCommand("sudo docker inspect " + image);
		log.debug("Docker inspect result:\n'{}'", result);

		return new DockerInspect(ModelNode.fromJSONString(result).get(0));
	}

	/**
	 * Creates ImageStream in provided OpenShift context and pulls back ImageStreamTag with imageUrl metadata.
	 *
	 * @param imageUrl  image url to initialize new instance of this object
	 * @param openShift context for creating ImageStream and retrieving image metadata
	 * @return new instance
	 */
	public static DockerInspect from(String imageUrl, OpenShiftUtil openShift) {
		ImageRegistry.Image image = ImageRegistry.toImage(imageUrl);

		String tag = image.getImageTag().replaceAll("-.*", "");
		String name = image.getImageRepo();

		TagReference tr = new TagReferenceBuilder().withName(tag).withNewImportPolicy().withInsecure(true).and().withNewFrom().withKind("DockerImage").withName(imageUrl).endFrom().build();
		ImageStream is = new ImageStreamBuilder().withNewMetadata().withName(name).addToAnnotations("openshift.io/image.insecureRepository", "true").and().withNewSpec().withTags(tr).endSpec().build();

		openShift.createImageStream(is);

		Waiters.sleep(TimeUnit.SECONDS, 10, "Giving OpenShift instance time to download image metadata.");
		ImageStreamTag isTag = openShift.client().imageStreamTags().withName(name + ":" + tag).get();

		return new DockerInspect(ModelNode.fromJSONString(new Gson().toJson(isTag.getImage().getDockerImageMetadata().getAdditionalProperties())));
	}

	private final ModelNode dockerInspect;

	private DockerInspect(ModelNode dockerInspect) {
		this.dockerInspect = dockerInspect;
	}

	/**
	 * Returns labels on Config:Labels path.
	 *
	 * @return map of labels
	 */
	public Map<String, String> labels() {
		return dockerInspect.get("Config", "Labels")
				.asPropertyList()
				.stream()
				.collect(
						Collectors.toMap(Property::getName, property -> property.getValue().asString())
				);
	}

	/**
	 * Returns default container command on Config:Cmd path
	 *
	 * @return default command
	 */
	public String command() {
		return dockerInspect.get("Config", "Cmd").get(0).asString();
	}

	/**
	 * Returns image environments of Config:Env path
	 *
	 * @return map of environments
	 */
	public Map<String, String> envs() {
		final Map<String, String> env = new HashMap<>();

		dockerInspect.get("Config", "Env").asList().forEach(
				node -> {
					String[] keyValue = node.asString().split("=", 2);
					env.put(keyValue[0], keyValue[1]);
				}
		);

		return Collections.unmodifiableMap(env);
	}

	/**
	 * Returns integer set of exposed ports by specified protocol (eg. tcp, udp).
	 *
	 * @return port set
	 */
	public Set<Integer> exposedPorts(String protocol) {
		final Set<Integer> result = new HashSet<>();
		final ModelNode exposedPorts = dockerInspect.get("Config", "ExposedPorts");

		if (exposedPorts.getType() != ModelType.UNDEFINED) {
			exposedPorts.keys().forEach(
					portDef -> {
						final String[] split = portDef.split("/");
						if (StringUtils.isBlank(protocol) || split[1].equalsIgnoreCase(protocol)) {
							result.add(Integer.parseInt(split[0]));
						}
					}
			);
		}

		return result;
	}

}
