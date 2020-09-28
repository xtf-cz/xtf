package cz.xtf.core.bm.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContentHash {

    public static String get(Path path) {
        String hash = String.valueOf(RandomUtils.nextLong(0, Integer.MAX_VALUE));
        try {
            hash = executeCommand(path, "/bin/sh", "-c", "tar -cf - . | md5sum").replaceAll("([a-z0-9]*).*", "$1");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to generate to directory content hash. Falling back to random number.", e);
        }
        return hash;
    }

    private static String executeCommand(Path path, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);

        pb.directory(path.toFile());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process p = pb.start();

        int result = p.waitFor();

        if (result != 0) {
            throw new IOException("Failed to calculate hash via: " + Arrays.toString(args));
        }

        try (InputStream is = p.getInputStream(); StringWriter sw = new StringWriter()) {
            IOUtils.copy(is, sw, Charset.defaultCharset());
            return sw.toString();
        }
    }

    // Static helper class
    private ContentHash() {

    }
}
