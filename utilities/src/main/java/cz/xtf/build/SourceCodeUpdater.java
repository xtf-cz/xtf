package cz.xtf.build;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import lombok.extern.slf4j.Slf4j;

import lombok.Getter;

@Slf4j
public class SourceCodeUpdater extends SimpleFileVisitor<Path> {
		final Path expected;
		final Path other;
		final OnDiff onDiff;

		@Getter
		private boolean diffPresent = false;

		@Getter
		private boolean error = false;

		public SourceCodeUpdater(Path expected, Path other, OnDiff operation) {
			this.expected = expected.toAbsolutePath();
			this.other = other;
			this.onDiff = operation;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
			Path relative = expected.relativize(file);
			if (other.resolve(relative).toFile().exists()) {
				String checkSumStatus = null;

				try (FileInputStream fisFirst = new FileInputStream(file.toFile());
						FileInputStream fisSecond = new FileInputStream(other.resolve(relative).toFile())) {

					if (DigestUtils.md5Hex(fisFirst).equals(DigestUtils.md5Hex(fisSecond))) {
						checkSumStatus = "identical";
					} else {
						diffPresent = true;
						checkSumStatus = "different";
						log.info("Found different check sum for file: {}, merging changes", relative);
						Files.copy(file, other.resolve(relative), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
					}
				} catch (IOException e) {
					log.error("IO happend, new build will be created", e);
					error = true;
					return FileVisitResult.TERMINATE;
				}
				log.debug("Other has file: {}, md5 check sum is {}", relative, checkSumStatus);
			} else {
				diffPresent = true;
				try {
					if (onDiff == OnDiff.COPY) {
						log.info("GitRepo does not contain file: {}, adding", relative);
						Files.copy(file, other.resolve(relative));
					} else {
						log.info("GitRepo contains file that is no longer present locally: {}, deleting", relative);
						Files.delete(file);
					}
				} catch (IOException e) {
					log.error("IO happend, new build will be created", e);
					error = true;
					return FileVisitResult.TERMINATE;
				}
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) {
			if (dir.getFileName().toString().equals(".git")) {
				return FileVisitResult.SKIP_SUBTREE;
			}
			if(onDiff == OnDiff.COPY) {
				Path relative = expected.relativize(dir);
				if (other.resolve(relative).toFile().exists()) {
					log.debug("Other has dir: {}", relative);
				} else {
					diffPresent = true;
					try {
						log.info("GitRepo does not contain dir: {}, adding", relative);
						Files.copy(dir, other.resolve(relative));
					} catch (IOException e) {
						log.error("IO happend, new build will be created", e.getMessage());
						error = true;
						return FileVisitResult.TERMINATE;
					}
				}
			}
			return FileVisitResult.CONTINUE;
		}
		
		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			if(onDiff == OnDiff.DELETE) {
				Path relative = expected.relativize(dir);
				if (other.resolve(relative).toFile().exists()) {
					log.debug("Other has dir: {}", relative);
				} else {
					diffPresent = true;
					try {
						log.info("GitRepo contains dir that is no longer present locally: {}, deleting", relative);
						Files.delete(dir);
					} catch (IOException e) {
						log.error("IO happend, new build will be created", e.getMessage());
						error = true;
						return FileVisitResult.TERMINATE;
					}
				}
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) {
			log.error(exc.getMessage());
			return FileVisitResult.CONTINUE;
		}

		public static enum OnDiff {
			COPY, DELETE;
		}
}
