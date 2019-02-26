package cz.xtf.core.bm;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BinaryBuild implements ManagedBuild {
	private static final String CONTENT_HASH_LABEL_KEY = "xtf.bm/content-hash";

	@Getter
	private final String id;
	@Getter
	private final Path path;

	private final String builderImage;
	private final Map<String, String> envProperties;

	private final ImageStream is;
	private final BuildConfig bc;

	private String contentHash = null;

	public BinaryBuild(String builderImage, Path path, Map<String, String> envProperties, String id) {
		this.builderImage = builderImage;
		this.path = path;
		this.envProperties = envProperties;
		this.id = id;

		this.is = this.createIsDefinition();
		this.bc = this.createBcDefinition();
	}

	@Override
	public void build(OpenShift openShift) {
		openShift.imageStreams().create(is);
		openShift.buildConfigs().create(bc);

		try {
			PipedOutputStream pos = new PipedOutputStream();
			PipedInputStream pis = new PipedInputStream(pos);

			ExecutorService executorService = Executors.newSingleThreadExecutor();
			final Future<?> future = executorService.submit(() -> writeProjectTar(pos));

			openShift.buildConfigs().withName(bc.getMetadata().getName()).instantiateBinary().fromInputStream(pis);
			future.get();
		} catch (IOException | InterruptedException | ExecutionException e) {
			log.error("Exception building {}", id, e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public void update(OpenShift openShift) {
		this.delete(openShift);
		this.build(openShift);
	}

	@Override
	public void delete(OpenShift openShift) {
		openShift.imageStreams().withName(is.getMetadata().getName()).delete();
		openShift.buildConfigs().withName(bc.getMetadata().getName()).delete();
	}

	@Override
	public boolean isPresent(OpenShift openShift) {
		boolean isPresence = openShift.imageStreams().withName(id).get() != null;
		boolean bcPresence = openShift.buildConfigs().withName(id).get() != null;

		return isPresence || bcPresence;
	}

	abstract List<EnvVar> getEnv(BuildConfig bc);

	abstract void configureBuildStrategy(BuildConfigSpecBuilder builder, String builderImage, List<EnvVar> envs);

	abstract String getImage(BuildConfig bc);

	@Override
	public boolean needsUpdate(OpenShift openShift) {
		BuildConfig activeBc = openShift.buildConfigs().withName(id).get();
		ImageStream activeIs = openShift.imageStreams().withName(id).get();

		// Check resources presence
		boolean needsUpdate = activeBc == null | activeIs == null;

		// Check image match
		if (!needsUpdate) {
			String activeBuilderImage = getImage(activeBc);
			needsUpdate = !builderImage.equals(activeBuilderImage);

			log.debug("Builder image differs? {} != {} ? {} ", builderImage, activeBuilderImage, needsUpdate);
		}

		// Check source match
		if (!needsUpdate) {
			String activeContentHash = activeBc.getMetadata().getLabels().get(CONTENT_HASH_LABEL_KEY);
			needsUpdate = !getContentHash().equals(activeContentHash);

			log.debug("Content hash differs? {}", needsUpdate);
		}

		// Check build strategy match
		if (!needsUpdate) {
			int thisCount = envProperties != null ? envProperties.size() : 0;
			List<EnvVar> activeBcEnv = getEnv(activeBc);
			int themCount = activeBcEnv != null ? activeBcEnv.size() : 0;
			needsUpdate = thisCount != themCount;

			log.debug("env count differs? {} != {} ? {}", thisCount, themCount, needsUpdate);

			if (thisCount == themCount && thisCount > 0) {
				for (EnvVar envVar : activeBcEnv) {
					if (envVar.getValue() == null) {
						if (envProperties.get(envVar.getName()) != null) {
							needsUpdate = true;

							log.debug("env {} null in BC, but not in envProperties", envVar.getValue());
							break;
						}
					} else if (!envVar.getValue().equals(envProperties.get(envVar.getName()))) {
						needsUpdate = true;

						log.debug("env {}={} in BC, but {} in envProperties", envVar.getName(), envVar.getValue(), envProperties.get(envVar.getName()));
						break;
					}
				}
			}

			log.debug("Build strategy differs? {}", needsUpdate);
		}

		// Check build status, update if failed
		if (!needsUpdate) {
			if (activeBc.getStatus() == null || activeBc.getStatus().getLastVersion() == null) {
				log.debug("No build last version");
				needsUpdate = true;
			}
			else {
				Build activeBuild = openShift.getBuild(id + "-" + activeBc.getStatus().getLastVersion());
				if (activeBuild == null || activeBuild.getStatus() == null || "Failed".equals(activeBuild.getStatus().getPhase())) {
					log.debug("Build failed");
					needsUpdate = true;
				}
			}
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
		List<EnvVar> envVarList = new LinkedList<>();

		if (envProperties != null) {
			for (Map.Entry<String, String> env : envProperties.entrySet()) {
				envVarList.add(new EnvVarBuilder().withName(env.getKey()).withValue(env.getValue()).build());
			}
		}

		ObjectMeta metadata = new ObjectMetaBuilder().withName(id).withLabels(Collections.singletonMap(CONTENT_HASH_LABEL_KEY, getContentHash())).build();
		BuildConfigSpecBuilder bcBuilder = new BuildConfigSpecBuilder();
		bcBuilder
				.withNewOutput().withNewTo().withKind("ImageStreamTag").withName(id + ":latest").endTo().endOutput()
				.withNewSource().withType("Binary").endSource();

		configureBuildStrategy(bcBuilder, builderImage, envVarList);

		return new BuildConfigBuilder().withMetadata(metadata).withSpec(bcBuilder.build()).build();
	}

	private String getContentHash() {
		if (contentHash == null) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				DigestOutputStream dos = new DigestOutputStream(NULL_OUTPUT_STREAM, md);

				writeProjectTar(dos);

				// kubernetes annotation value must not be longer than 63 chars
				contentHash = Hex.encodeHexString(dos.getMessageDigest().digest()).substring(0, 63);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}

		return contentHash;
	}

	private void writeProjectTar(OutputStream os) {
		Collection<File> filesToArchive = FileUtils.listFiles(path.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		try (TarArchiveOutputStream o = (TarArchiveOutputStream) new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.TAR, os)) {
			o.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			for (File f : filesToArchive) {
				String tarPath = path.relativize(f.toPath()).toString();
				log.trace("adding file to tar: {}", tarPath);
				ArchiveEntry entry = o.createArchiveEntry(f, tarPath);

				// we force the modTime in the tar, so that the resulting tars are binary equal if their contents are
				TarArchiveEntry tarArchiveEntry = (TarArchiveEntry) entry;
				tarArchiveEntry.setModTime(Date.from(Instant.EPOCH));

				o.putArchiveEntry(tarArchiveEntry);
				if (f.isFile()) {
					try (InputStream i = Files.newInputStream(f.toPath())) {
						IOUtils.copy(i, o);
					}
				}
				o.closeArchiveEntry();
			}
			o.finish();
		} catch (ArchiveException | IOException e) {
			throw new RuntimeException(e);
		}
	}
}