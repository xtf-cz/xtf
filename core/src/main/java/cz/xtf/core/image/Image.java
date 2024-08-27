package cz.xtf.core.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cz.xtf.core.config.XTFConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.TagReference;
import io.fabric8.openshift.api.model.TagReferenceBuilder;
import lombok.Getter;

@Getter
public class Image {

    public static Image get(String id) {
        String imageUrl = XTFConfig.get("xtf." + id + ".image");
        return imageUrl == null ? null : Image.from(imageUrl);
    }

    public static Image resolve(String id) {
        // first try to find the image with subid to get any more specific image
        String subid = XTFConfig.get("xtf." + id + ".subid");
        Image image = Image.get(id + "." + subid);
        if (image != null) {
            String customReg = XTFConfig.get("xtf." + id + ".reg");
            String customRegId = XTFConfig.get("xtf." + id + ".regid");
            String customUser = XTFConfig.get("xtf." + id + ".user");
            String customTag = XTFConfig.get("xtf." + id + ".tag");

            String reg = customRegId != null ? XTFConfig.get("xtf.registry." + customRegId) : customReg;
            reg = reg != null ? reg : image.getRegistry();
            String user = customUser != null ? customUser : image.getUser();
            String tag = customTag != null ? customTag : image.getTag();

            return new Image(reg, user, image.getRepo(), tag);
        }
        // then try with id
        image = Image.get(id);
        if (image != null) {
            return image;
        }
        // in case no image is found throw exception
        throw new UnknownImageException("Unable to get image using " + id + " or " + subid);
    }

    public static Image from(String imageUrl) {
        final String[] slashTokens = imageUrl.split("/");
        final String repoTag;

        final String registry;
        final String user;
        final String repo;
        final String tag;

        switch (slashTokens.length) {
            case 1:
                registry = "";
                user = "";
                repoTag = slashTokens[0];
                break;
            case 2:
                registry = "";
                user = slashTokens[0];
                repoTag = slashTokens[1];
                break;
            case 3:
                registry = slashTokens[0];
                user = slashTokens[1];
                repoTag = slashTokens[2];
                break;
            default:
                registry = slashTokens[0];
                user = slashTokens[1];
                repoTag = String.join("/", Arrays.copyOfRange(slashTokens, 2, slashTokens.length));
                break;
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
            default:
                throw new IllegalArgumentException("repoTag '" + repoTag + "' should have zero or two ':' characters");
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
        return getImageStream(repo);
    }

    public ImageStream getImageStream(String name) {
        return getImageStream(name, getMajorTag());
    }

    public ImageStream getImageStream(String name, String... tags) {
        List<TagReference> tagRefs = new ArrayList<>(tags.length);
        for (String tag : tags) {
            tagRefs.add(new TagReferenceBuilder().withName(tag).withNewImportPolicy().withInsecure(true).and().withNewFrom()
                    .withKind("DockerImage").withName(url).endFrom().build());
        }
        return new ImageStreamBuilder().withNewMetadata().withName(name)
                .addToAnnotations("openshift.io/image.insecureRepository", "true").and().withNewSpec().withTags(tagRefs)
                .endSpec().build();
    }

    public boolean isVersionAtLeast(String version) {
        String majorTag = this.getMajorTag();
        if (getMajorTag().matches("[0-9]+\\.[0-9]+")) {
            int imageMajor = Integer.valueOf(majorTag.split("\\.")[0]);
            int imageMinor = Integer.valueOf(majorTag.split("\\.")[1]);

            int targetMajor = Integer.valueOf(version.split("\\.")[0]);
            int targetMinor = Integer.valueOf(version.split("\\.")[1]);

            return (imageMajor >= targetMajor && imageMinor >= targetMinor);
        } else {
            return true;
        }
    }
}
