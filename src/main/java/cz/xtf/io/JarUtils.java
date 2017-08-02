package cz.xtf.io;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JarUtils {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(JarUtils.class);

	public static void jar(final Path sourceDirectory, final Path targetFile) {
		final Path manifestPath = sourceDirectory
				.resolve("META-INF/MANIFEST.MF");
		Manifest manifest = null;
		if (manifestPath.toFile().exists()) {
			try (final FileInputStream manifestIS = new FileInputStream(
					manifestPath.toFile())) {
				manifest = new Manifest(manifestIS);
			} catch (IOException e) {
				LOGGER.error("Error in reading manifest", e);
				throw new IllegalStateException(
						"File was not compressed successfully", e);
			}
		}
		try (final FileOutputStream fos = new FileOutputStream(
				targetFile.toFile());
				final JarOutputStream jarOutputStream = (manifest == null) ? new JarOutputStream(
						fos) : new JarOutputStream(fos, manifest)) {
			final int sourceDirectoryNameLength = sourceDirectory.toAbsolutePath().toFile().getPath().length() + 1;
			IOFileFilter skipManifest = new NotFileFilter(
					new WildcardFileFilter("MANIFEST.MF"));
			FileUtils.listFiles(sourceDirectory.toFile(), skipManifest,
					TrueFileFilter.TRUE).forEach(file -> {
						final String entryName = file.getPath().substring(sourceDirectoryNameLength);
						try {
							jarOutputStream.putNextEntry(new JarEntry(entryName));
							try (FileInputStream fis = new FileInputStream(file)) {
								IOUtils.copy(fis, jarOutputStream);
							}
						}
						catch (IOException e) {
							LOGGER.error("Error in writing entry", e);
							throw new IllegalStateException(
									"File was not compressed successfully", e);
						}
						
					});
		} catch (IOException e) {
			LOGGER.error("Error in jar", e);
			throw new IllegalStateException(
					"File was not compressed successfully", e);
		}
	}

	public static void unjar(final Path targetDirectory, final Path sourceFile) {
		try (final FileInputStream fis = new FileInputStream(sourceFile.toFile());
			final ZipInputStream jarInputStream = new ZipInputStream(fis)) {
			byte[] buffer = new byte[65536];
			for (ZipEntry entry = jarInputStream.getNextEntry(); entry != null; entry = jarInputStream
					.getNextEntry()) {
				if (entry.isDirectory()) {
					targetDirectory.resolve(entry.getName()).toFile().mkdirs();
				} else {
					// directory entries are optional
					targetDirectory.resolve(entry.getName()).getParent().toFile().mkdirs();
					try (FileOutputStream fos = new FileOutputStream(
							targetDirectory.resolve(entry.getName()).toFile())) {
						for (int cnt = jarInputStream.read(buffer); cnt != -1; cnt = jarInputStream
								.read(buffer)) {
							fos.write(buffer, 0, cnt);
						}
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("Error in unjar", e);
			throw new IllegalStateException(
					"File was not uncompressed successfully", e);
		}
	}
}
