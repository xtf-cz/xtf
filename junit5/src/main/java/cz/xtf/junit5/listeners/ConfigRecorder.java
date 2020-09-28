package cz.xtf.junit5.listeners;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import cz.xtf.core.image.Image;
import cz.xtf.junit5.config.JUnitConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigRecorder implements TestExecutionListener {
    private static final File RUNTIME_IMAGES_FILE = getProjectRoot().resolve("used-images.properties").toFile();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        recordUsedImages();
    }

    private void recordUsedImages() {
        final List<String> usedImages = JUnitConfig.usedImages();
        if (usedImages.size() > 0) {
            final Properties images = new Properties();
            try (FileWriter writer = new FileWriter(RUNTIME_IMAGES_FILE)) {
                usedImages.forEach(name -> images.setProperty(name, Image.resolve(name).getUrl()));
                images.store(writer, "Images used in test");
            } catch (Exception e) {
                log.warn("Failed to record used images!");
            }
        }
    }

    private static Path getProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir.getParent().resolve("pom.xml").toFile().exists())
            dir = dir.getParent();
        return dir;
    }
}
