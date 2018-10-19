package cz.xtf.util;

import cz.xtf.TestConfiguration;
import cz.xtf.TestParent;
import cz.xtf.build.BuildDefinition;
import cz.xtf.build.PathBuildDefinition;
import cz.xtf.build.XTFBuild;
import cz.xtf.io.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for FIS related hacks
 */
public class FisUtils {

	/**
	 * Populate appDirectory path with configuration/settings.xml to configure common Maven proxy.
	 * This method reflects global config and sets the configuration if proxy is enabled.
	 * If the configuration dir already exists, it's cleaned and recreated.
	 *
	 * @param appDirectory coordinates of application directory
	 */
	public static void createMavenProxyConf(Path appDirectory) {
		File m2SettingsXml = null;
		if (TestConfiguration.mavenProxyEnabled()) {
			// create only if the configuration/settings.xml doesn't exist already
			if (!appDirectory.resolve("configuration").toFile().isDirectory()) {
				appDirectory.resolve("configuration").toFile().mkdirs();
			}
			m2SettingsXml = appDirectory.resolve("configuration").resolve("settings.xml").toFile();
			//clean if exists
			if (m2SettingsXml.isFile()) {
				m2SettingsXml.delete();
			}
			try (FileWriter fw = new FileWriter(m2SettingsXml)) {
				fw.write(String.format("<settings>\n" +
						"  <mirrors>\n" +
						"    <mirror>\n" +
						"      <id>s2i-mirror</id>\n" +
						"      <url>%s</url>\n" +
						"      <mirrorOf>external:*</mirrorOf>\n" +
						"    </mirror>\n" +
						"  </mirrors>\n" +
						"</settings>\n", TestConfiguration.mavenProxyURL()));
			} catch (IOException e) {
				//no op
			}
		}
	}

	@Deprecated //Due to switch to BuildManager V2
	public static XTFBuild buildWithMavenProxyConf(XTFBuild build) {
		try {
			// We hack the build path to be able to inject a custom settings.xml.
			// So we first copy the build path (and its parent) to tmp, and modify the settings.xml...
			IOUtils.TMP_DIRECTORY.toFile().mkdirs();
			Path tmp = Files.createTempDirectory(IOUtils.TMP_DIRECTORY, build.getBuildDefinition().getAppName());

			PathBuildDefinition buildDefinition = (PathBuildDefinition) build.getBuildDefinition();

			IOUtils.copy(buildDefinition.getPathToProject(), tmp);

			Path projectSource = tmp.resolve(buildDefinition.getPathToProject().getFileName());

			createMavenProxyConf(projectSource);

			// Now we wrap the original build, all is same except the getPathToProject
			return new XTFBuild() {

				@Override
				public BuildDefinition getBuildDefinition() {
					return build.getBuildDefinition();
				}
			};
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Path pathWithParent(String appName, String parentName) {
		try {
			// We hack the build path to be able to inject a custom settings.xml.
			// So we first copy the build path (and its parent) to tmp, and modify the settings.xml...
			IOUtils.TMP_DIRECTORY.toFile().mkdirs();

			Path tmpPath = Files.createTempDirectory(IOUtils.TMP_DIRECTORY, appName);
			Path appPath = TestParent.findApplicationDirectory("test-fuse", String.format("%s/%s", parentName, appName));
			//copy application from test/resources
			IOUtils.copy(appPath, tmpPath);
			//copy parent pom with directory from test/resources
			String parentPom = "parent-fis-" + (parentName.equals("spring-boot") ? "sb" : parentName);
			if (!IOUtils.TMP_DIRECTORY.resolve(parentPom).toFile().exists()) {
				//don't overwrite if exists already
				IOUtils.copy(appPath.getParent().resolve(parentPom), Files.createDirectory(IOUtils.TMP_DIRECTORY.resolve(parentPom)));
			}

			return tmpPath;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Path pathWithMavenProxyConf(String appName, String parentName) {
		Path tmpPath = pathWithParent(appName, parentName);
		createMavenProxyConf(tmpPath);
		return tmpPath;
	}
}
