package cz.xtf.core.bm;

import cz.xtf.core.bm.helpers.ContentHash;
import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShiftBinary;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Collections;

public class BinaryBuild implements ManagedBuild {
	private static final String CONTENT_HASH_LABEL_KEY = "xtf.bm/content-hash";

	private static String generateBuildID(String builderImage, Path path) {
		return path.getFileName().toString() + "-" + Image.from(builderImage).getUser().replaceAll("-openshift", "");
	}

	@Getter
	private final String id;
	private final String builderImage;
	private final String contentHash;
	private final Path path;

	private final ImageStream is;
	private final BuildConfig bc;

	public BinaryBuild(String builderImage, Path path) {
		this(builderImage, path, ContentHash.get(path));
	}

	public BinaryBuild(String builderImage, Path path, String contentHash) {
		this(builderImage, path, contentHash, BinaryBuild.generateBuildID(builderImage, path));
	}

	public BinaryBuild(String builderImage, Path path, String contentHash, String id) {
		this.builderImage = builderImage;
		this.path = path;
		this.contentHash = contentHash;
		this.id = id;

		this.is = this.createIsDefinition();
		this.bc = this.createBcDefinition();
	}

	@Override
	public void build(OpenShift openShift) {
		final String url = openShift.getConfiguration().getMasterUrl();
		final String token = openShift.getConfiguration().getOauthToken();
		final String username = openShift.getConfiguration().getUsername();
		final String password = openShift.getConfiguration().getPassword();
		final String namespace = openShift.getConfiguration().getNamespace();

		openShift.createImageStream(is);
		openShift.createBuildConfig(bc);

		OpenShiftBinary openShiftBinary = new OpenShiftBinary(OpenShifts.getBinaryPath());
		if(token != null) {
			openShiftBinary.login(url, token);
		} else {
			openShiftBinary.login(url, username, password);
		}
		openShiftBinary.project(namespace);
		openShiftBinary.startBuild(id, path.toAbsolutePath().toString());
	}

	@Override
	public void update(OpenShift openShift) {
		this.delete(openShift);
		this.build(openShift);
	}

	@Override
	public void delete(OpenShift openShift) {
		openShift.deleteImageStream(is);
		openShift.deleteBuildConfig(bc);
	}

	@Override
	public boolean isPresent(OpenShift openShift) {
		boolean isPresence = openShift.getImageStream(id) != null;
		boolean bcPresence = openShift.getBuildConfig(id) != null;

		return isPresence || bcPresence;
	}

	@Override
	public boolean needsUpdate(OpenShift openShift) {
		BuildConfig activeBc = openShift.getBuildConfig(id);
		ImageStream activeIs = openShift.getImageStream(id);

		// Check resources presence
		boolean needsUpdate = activeBc == null | activeIs == null;

		// Check image match
		if (!needsUpdate) {
			String activeBuilderImage = bc.getSpec().getStrategy().getSourceStrategy().getFrom().getName();
			needsUpdate = !builderImage.equals(activeBuilderImage);
		}

		// Check source match
		if (!needsUpdate) {
			String activeContentHash = bc.getMetadata().getLabels().get(CONTENT_HASH_LABEL_KEY);
			needsUpdate = !contentHash.equals(activeContentHash);
		}

		return needsUpdate;
	}

	@Override
	public Waiter hasCompleted(OpenShift openShift) {
		return openShift.waiters().hasBuildCompleted(id);
	}

	private ImageStream createIsDefinition() {
		ObjectMeta metadata = new ObjectMetaBuilder().withName(id).build();
		return new ImageStreamBuilder().withMetadata(metadata).build();
	}

	private BuildConfig createBcDefinition() {
		ObjectMeta metadata = new ObjectMetaBuilder().withName(id).withLabels(Collections.singletonMap(CONTENT_HASH_LABEL_KEY, contentHash)).build();
		BuildConfigSpec spec = new BuildConfigSpecBuilder()
				.withNewOutput().withNewTo().withKind("ImageStreamTag").withName(id + ":latest").endTo().endOutput()
				.withNewSource().withType("Binary").endSource()
				.withNewStrategy().withType("Source").withNewSourceStrategy().withForcePull(true).withNewFrom().withKind("DockerImage").withName(builderImage).endFrom().endSourceStrategy().endStrategy().build();
		return new BuildConfigBuilder().withMetadata(metadata).withSpec(spec).build();
	}
}