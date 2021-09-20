package cz.xtf.core.openshift;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cz.xtf.core.config.OpenShiftConfig;

public class OpenShiftsTest {

    static final String ocUrl = "https://ocp48console/amd64/linux/oc.tar";
    static final String version = "4.8.2";
    static File ocTarFile = null;
    static Path expected = null;

    @BeforeAll
    public static void init() throws URISyntaxException {
        ocTarFile = Paths.get(Thread.currentThread().getContextClassLoader().getResource("oc.tar.gz").toURI()).toFile();
        expected = Paths.get(OpenShiftConfig.binaryCachePath(), version,
                DigestUtils.md5Hex(ocUrl));
    }

    @AfterEach
    public void clean() throws IOException {
        final File directory = Paths.get(OpenShiftConfig.binaryCachePath()).toFile();
        if (directory.exists()) {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Test
    public void saveOnCacheTest() throws IOException {
        OpenShifts.saveOcOnCache(version, ocUrl, ocTarFile);
        Assertions.assertTrue(Paths.get(expected.normalize().toString(), ocTarFile.getName()).toFile().exists());
    }

    @Test
    public void getOcFromCacheTest() throws IOException {
        FileUtils.copyFile(ocTarFile, new File(expected.toFile(), ocTarFile.getName()));
        Assertions.assertTrue(OpenShifts.getOcFromCache(version, ocUrl, ocTarFile).exists());
    }

}
