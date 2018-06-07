package cz.xtf.io;

import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

public class IOUtils {
	public static final Path TMP_DIRECTORY = Paths.get("tmp").toAbsolutePath();
	public static final Path LOG_DIRECTORY = Paths.get("log").toAbsolutePath();

	private IOUtils() {
	}

	public static void copy(InputStream src, OutputStream dest) throws IOException {
		try (ReadableByteChannel srcChannel = Channels.newChannel(src);
			WritableByteChannel destChannel = Channels.newChannel(dest)) {

			copy(srcChannel, destChannel);
		}
	}

	public static void copy(ReadableByteChannel src, WritableByteChannel dest) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);

		while (src.read(buffer) != -1) {
			buffer.flip();
			dest.write(buffer);
			buffer.compact();
		}
		buffer.flip();
		dest.write(buffer);
	}

	public static String readInputStream(InputStream input) throws IOException {
		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line).append(System.lineSeparator());
			}
		}

		return builder.toString();
	}

	public static Collection<String> readInputStreamAsCollection(InputStream input) throws IOException {
		Collection<String> result = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				result.add(line);
			}
		}

		return result;
	}

	public static Path findProjectRoot() {
		Path dir = Paths.get("").toAbsolutePath();
		for (; dir.getParent().resolve("pom.xml").toFile().exists(); dir = dir.getParent()) ;
		return dir;
	}

	/**
	 * Copies the source file/directory to target file/directory. The
	 * Files.isDirectory is used (if Path does not exist, it returns false).
	 * <ul>
	 * <li>If source is a file and target is a file, it copies the source to
	 * target (no overwrite)</li>
	 * <li>If source is a file and target is directory, the file is copied into
	 * the directory.</li>
	 * <li>If source is a directory and target is file, IOException is thrown.</li>
	 * <li>If source is a directory and target is directory, contents of source
	 * are copied to target.</li>
	 * </ul>
	 *
	 * @param source
	 * @param target
	 * @throws IOException
	 */
	public static void copy(Path source, Path target) throws IOException {
		if (Files.isDirectory(target)) {
			if (Files.isDirectory(source)) {
				if (Files.notExists(target)) {
					Files.walkFileTree(source, new CopyFilesWalker(target));
				} else {
					Files.walkFileTree(source, new CopyFilesWalker(target, true));
				}
			} else {
				Files.copy(source, target.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		} else {
			if (Files.isDirectory(source)) {
				throw new IOException("Can't copy directory into file");
			} else {
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	public static <T> boolean waitForCondition(int retries, long sleep, T baseObject, Function<T, T> valueProducer, Function<T, Boolean> condition) {
		return waitForCondition(retries, sleep, baseObject, valueProducer, condition, ex -> LoggerFactory.getLogger(IOUtils.class).warn("Error during waitForCondition", ex));
	}

	public static <T> boolean waitForCondition(int retries, long sleep, T baseObject, Function<T, T> valueProducer, Function<T, Boolean> condition, Consumer<Exception> exceptionHandler) {
		int counter = retries;
		T value = baseObject;
		do {
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException ex) {
				if (exceptionHandler != null) {
					exceptionHandler.accept(ex);
				}
			}
			value = valueProducer.apply(value);
		} while (counter-- > 0 && value != null && condition.apply(value));

		return value == null || condition.apply(valueProducer.apply(value));
	}

	public static void deleteDirectory(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			// already gone
			return;
		}
		if (Files.isDirectory(directory)) {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}

			});
		} else {
			// in case someone uses it for deleting files
			Files.delete(directory);
		}
	}

	private static class CopyFilesWalker extends SimpleFileVisitor<Path> {
		private Path target;
		private boolean skipFirstDir;

		public CopyFilesWalker(Path target) {
			this(target, false);
		}

		public CopyFilesWalker(Path target, boolean skipFirstDir) {
			this.target = target;
			this.skipFirstDir = skipFirstDir;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			if (skipFirstDir) {
				skipFirstDir = false;
			} else {
				if (dir.toAbsolutePath().normalize().equals(TMP_DIRECTORY.toAbsolutePath().normalize()) || dir.endsWith(".git")) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				target = target.resolve(dir.getFileName());
				if (!Files.exists(target)) {
					Files.createDirectory(target);
				}
			}

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);

			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			target = target.getParent();

			return FileVisitResult.CONTINUE;
		}

	}
}
