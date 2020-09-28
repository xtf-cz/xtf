package cz.xtf.builder.builders;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamSpecBuilder;
import io.fabric8.openshift.api.model.TagImportPolicy;
import io.fabric8.openshift.api.model.TagReference;
import io.fabric8.openshift.api.model.TagReferenceBuilder;

public class ImageStreamBuilder extends AbstractBuilder<ImageStream, ImageStreamBuilder> {
    private static final boolean SCHEDULED = false;
    private final List<TagReference> tagReferences = new LinkedList<>();
    private String imageUrl;
    private boolean insecure = false;

    public ImageStreamBuilder(String name) {
        this(null, name);
    }

    ImageStreamBuilder(ApplicationBuilder applicationBuilder, String name) {
        super(applicationBuilder, name);
    }

    public ImageStreamBuilder fromExternalImage(String imageUrl) {
        String[] parts = imageUrl.split("/");

        String registry = "";
        String repo = "";
        String nameWithTag = "";
        switch (parts.length) {
            case 3:
                registry = parts[0];
                repo = parts[1];
                nameWithTag = parts[2];
                break;
            case 2:
                repo = parts[0];
                nameWithTag = parts[1];
                break;
            case 1:
                nameWithTag = parts[0];
                break;
        }

        String[] nameParts = nameWithTag.split(":");

        String name = nameParts[0];
        String tag = "";
        if (nameParts.length > 1) {
            tag = nameParts[1];
        }

        String remoteUri = (StringUtils.isNotBlank(registry) ? registry + "/" : "") +
                (StringUtils.isNotBlank(repo) ? repo + "/" : "") +
                name;

        remoteRepo(remoteUri);
        if (StringUtils.isNotBlank(tag)) {
            addTag(tag, imageUrl);
        }

        return this;
    }

    public ImageStreamBuilder remoteRepo(String imageUrl) {
        return remoteRepo(imageUrl, true);
    }

    public ImageStreamBuilder remoteRepo(String imageUrl, boolean insecure) {
        this.imageUrl = imageUrl;
        if (insecure) {
            return insecure();
        } else {
            return this;
        }
    }

    public ImageStreamBuilder insecure() {
        insecure = true;
        addAnnotation("openshift.io/image.insecureRepository", "true");
        return this;
    }

    public ImageStreamBuilder addTag(String tag) {
        return addTag(tag, null);
    }

    public ImageStreamBuilder addTag(String tag, String sourceUrl) {
        return addTag(tag, sourceUrl, insecure);
    }

    public ImageStreamBuilder addTag(String tag, String sourceUrl, boolean insecure) {
        return addTag(tag, sourceUrl, insecure, TagReferencePolicyType.SOURCE);
    }

    public ImageStreamBuilder addTag(String tag, String sourceUrl, TagReferencePolicyType referencePolicyType) {
        return addTag(tag, sourceUrl, insecure, referencePolicyType);
    }

    public ImageStreamBuilder addTag(String tag, String sourceUrl, boolean insecure,
            TagReferencePolicyType referencePolicyType) {
        TagReferenceBuilder trb = new TagReferenceBuilder()
                .withName(tag);

        if (StringUtils.isNotBlank(sourceUrl)) {
            trb.withImportPolicy(new TagImportPolicy(insecure, SCHEDULED));

            trb.withNewFrom()
                    .withKind("DockerImage")
                    .withName(sourceUrl)
                    .endFrom();
            trb.withNewReferencePolicy(referencePolicyType.toString());
        }

        tagReferences.add(trb.build());
        return this;
    }

    @Override
    public ImageStream build() {
        ImageStreamSpecBuilder builder = new ImageStreamSpecBuilder();

        if (StringUtils.isNotBlank(imageUrl)) {
            builder.withDockerImageRepository(imageUrl);
        }

        builder.withTags(tagReferences);

        return new io.fabric8.openshift.api.model.ImageStreamBuilder()
                .withMetadata(metadataBuilder().build())
                .withSpec(builder.build())
                .build();
    }

    @Override
    protected ImageStreamBuilder getThis() {
        return this;
    }

    public enum TagReferencePolicyType {
        LOCAL,
        SOURCE;

        @Override
        public String toString() {
            return name().charAt(0) + (name().length() > 1 ? name().substring(1).toLowerCase() : "");
        }
    }
}
