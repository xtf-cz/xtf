package cz.xtf.core.image;

import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.TagReference;
import io.fabric8.openshift.api.model.TagReferenceBuilder;
import lombok.Getter;

@Getter
public class Image {

	public static Image from(String imageUrl) {
		final String[] slashTokens = imageUrl.split("/");
		final String repoTag;

		final String registry;
		final String user;
		final String repo;
		final String tag;

		switch (slashTokens.length) {
			case 1:registry = ""; user= ""; repoTag = slashTokens[0];break;
			case 2:registry = ""; user = slashTokens[0]; repoTag = slashTokens[1];break;
			case 3:registry = slashTokens[0]; user = slashTokens[1]; repoTag = slashTokens[2]; break;
			default: throw new IllegalArgumentException("image '" + imageUrl + "' should have one or two '/' characters");
		}

		final String[] tokens = repoTag.split(":");
		switch (tokens.length) {
			case 1:
				repo = tokens[0];
				tag = "";
				break;
			case 2:
				repo = tokens[0];
				tag = tokens[1];
				break;
			default: throw new IllegalArgumentException("repoTag '" + repoTag + "' should have zero or two ':' characters");
		}
		return new Image(registry, user, repo, tag);
	}

	private final String url;
	private final String registry;
	private final String user;
	private final String repo;
	private final String tag;

	public Image(String registry, String user, String repo, String tag) {
		this.url = String.format("%s/%s/%s:%s", registry, user, repo, tag);
		this.registry = registry;
		this.user = user;
		this.repo = repo;
		this.tag = tag;
	}

	public String getMajorTag() {
		return tag.replaceAll("-.*", "");
	}

	public ImageStream getImageStream() {
		TagReference tr = new TagReferenceBuilder().withName(getMajorTag()).withNewImportPolicy().withInsecure(true).and().withNewFrom().withKind("DockerImage").withName(url).endFrom().build();
		return new ImageStreamBuilder().withNewMetadata().withName(repo).addToAnnotations("openshift.io/image.insecureRepository", "true").and().withNewSpec().withTags(tr).endSpec().build();
	}
}
