package cz.xtf;

import org.apache.commons.lang3.StringUtils;

import org.jboss.dmr.ModelNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class UsageRecorder {
	private static final Logger LOGGER = LoggerFactory.getLogger(UsageRecorder.class);
	private static final UsageRecorder INSTANCE = new UsageRecorder();

	private final ModelNode json;
	private final Path jsonFile;

	private final Set<TemplateRecord> templateRecords = new HashSet<>();
	private final Set<ImageRecord> imageRecords = new HashSet<>();

	public static void recordImage(String imagestream, String image) {
		if (INSTANCE.imageRecords.add(new ImageRecord(imagestream, image))) {
			ModelNode imageNode = INSTANCE.json.get("images").add();

			imageNode.get("image-stream").set(imagestream);
			imageNode.get("image").set(image);

			flush();
		}
	}

	public static void recordTemplate(String name, String repository, String branch, String commitId) {
		if (INSTANCE.templateRecords.add(new TemplateRecord(name, repository, branch, commitId))) {
			ModelNode templateNode = INSTANCE.json.get("templates").add();

			templateNode.get("name").set(name);
			templateNode.get("repository").set(repository);
			templateNode.get("branch").set(branch);
			templateNode.get("commit").set(commitId);

			flush();
		}
	}

	public static void recordOpenShiftVersion(String openShiftVersion) {
		INSTANCE.json.get("versions", "openshift").set(openShiftVersion);
		flush();
	}

	public static void recordKubernetesVersion(String kubeVersion) {
		INSTANCE.json.get("versions", "kubernetes").set(kubeVersion);
		flush();
	}

	public static void storeProperty(String name, String value) {
		ModelNode propertyNode = INSTANCE.json.get("properties").add();

		propertyNode.get("name").set(name);
		propertyNode.get("value").set(value);
	}

	public static void flush() {
		INSTANCE.saveJson();
	}

	private UsageRecorder() {
		json = new ModelNode();
		json.get("versions").setEmptyObject();
		json.get("images").setEmptyList();
		json.get("templates").setEmptyList();
		json.get("properties").setEmptyList();

		jsonFile = IOUtils.findProjectRoot().resolve("usage.json");

		saveJson();
	}

	private void saveJson() {
		try (BufferedWriter writer = Files.newBufferedWriter(jsonFile)) {
			writer.write(json.toJSONString(false));
			writer.newLine();
			writer.flush();
		} catch (IOException ex) {
			LOGGER.error("Failed to save usage records", ex);
		}
	}

	private static class TemplateRecord {
		private final String name;
		private final String repository;
		private final String branch;
		private final String commit;

		TemplateRecord(String name, String repository, String branch, String commit) {
			if (StringUtils.isBlank(name) || StringUtils.isBlank(repository)) {
				throw new IllegalArgumentException("Template name and repository must not be blank");
			}

			this.name = name;
			this.repository = repository;
			this.branch = branch;
			this.commit = commit;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			TemplateRecord that = (TemplateRecord) o;

			return name.equals(that.name) &&
					repository.equals(that.repository) &&
					(branch != null ? branch.equals(that.branch) : that.branch == null) &&
					(commit != null ? commit.equals(that.commit) : that.commit == null);
		}

		@Override
		public int hashCode() {
			int result = name.hashCode();
			result = 31 * result + repository.hashCode();
			result = 31 * result + (branch != null ? branch.hashCode() : 0);
			result = 31 * result + (commit != null ? commit.hashCode() : 0);
			return result;
		}
	}

	private static class ImageRecord {
		private final String streamName;
		private final String image;

		ImageRecord(String streamName, String image) {
			if (StringUtils.isBlank(streamName) || StringUtils.isBlank(image)) {
				throw new IllegalArgumentException("Image stream name and image url must not be blank");
			}

			this.streamName = streamName;
			this.image = image;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			ImageRecord that = (ImageRecord) o;

			return streamName.equals(that.streamName) && image.equals(that.image);
		}

		@Override
		public int hashCode() {
			int result = streamName.hashCode();
			result = 31 * result + image.hashCode();
			return result;
		}
	}
}
