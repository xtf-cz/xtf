package cz.xtf.build;

import cz.xtf.io.IOUtils;
import cz.xtf.maven.MavenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.it.VerificationException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Class for dynamic manipulation with applications.
 */
@Slf4j
public class Project {

	@FunctionalInterface
	public interface ProjectModifier {
		Path modify(Path pathToProject) throws IOException;
	}

	public static class PackageFileFilter implements FilenameFilter {
		@Override
		public boolean accept(File file, String s) {
			return s.matches(".*(\\.war|\\.jar|\\.ear)");
		}
	}

	/**
	 * @see #findApplicationDirectory(String, String, String)
	 */
	public static Path findApplicationDirectory(String appName) {
		return findApplicationDirectory(null, appName, null);
	}

	/**
	 * @see #findApplicationDirectory(String, String, String)
	 */
	public static Path findApplicationDirectory(String moduleName, String appName) {
		return findApplicationDirectory(moduleName, appName, null);
	}

	/**
	 * Looks up for directory with requested application and returns its path.<br/>
	 * Application is considered to be under:
	 * <ul>
	 *     <li>rootDir/moduleName/src/main/resources/apps/appModuleName/appName</li>
	 *     <li>rootDir/moduleName/src/test/resources/apps/appModuleName/appName</li>
	 * </ul>
	 * <br/>
	 * findApplicationDirectory(null, appName, null) -> activePomRootDir/src/test/resources/apps/appName
	 *
	 * @param moduleName module name in root project (left out if null)
	 * @param appName name of application
	 * @param appModuleName module in apps dir (left out if null)
	 * @return absolute path to app root directory
	 * @throws IllegalStateException if directory doesn't exist
	 */
	public static Path findApplicationDirectory(String moduleName, String appName, String appModuleName) {
		Path basePath = moduleName == null ? Paths.get("") : IOUtils.findProjectRoot().resolve(moduleName);
		Path relativePath = appModuleName == null ? Paths.get(appName) : Paths.get(appModuleName, appName);

		Path testApp = basePath.resolve("src/test/resources/apps").resolve(relativePath);
		Path mainApp = basePath.resolve("src/main/resources/apps").resolve(relativePath);

		if(testApp.toFile().exists()) {
			log.info("Found project {} at path: {}", appName, testApp.toAbsolutePath().toString());
			return testApp;
		} else if (mainApp.toFile().exists()) {
			log.info("Found project {} at path: {}", appName, mainApp.toAbsolutePath().toString());
			return mainApp;
		}

		throw new IllegalStateException("Project app not found");
	}

	private static final Path PROJECTS_TMP_DIR;

	static {
		PROJECTS_TMP_DIR = IOUtils.TMP_DIRECTORY.resolve("projects");
		PROJECTS_TMP_DIR.toFile().mkdirs();
	}

	/**
	 * Will create tmp folder with project from origin source.
	 *
	 * @param projectOrigin path to origin source
	 * @param projectName name of the project
	 * @return new instance pointing to tmp folder with project that can be dynamically modified
	 * @throws IOException in case that project fails to be copied
	 */
	public static Project from(Path projectOrigin, String projectName) throws IOException {
		Path project = Files.createTempDirectory(PROJECTS_TMP_DIR, projectName);
		Path pointer = project.resolve(projectName);
		pointer.toFile().mkdir();

		IOUtils.copy(projectOrigin, pointer);
		return new Project(project, pointer);
	}

	private Path projectDir;
	private Path versionPointer;

	private Project(Path path, Path versionPointer) {
		this.projectDir = path;
		this.versionPointer = versionPointer;
	}

	/**
	 * Takes a function that takes path and return a path with modified app.
	 *
	 * @return path to modified application
	 */
	public Project modify(ProjectModifier modifier) throws IOException {
		this.versionPointer = modifier.modify(versionPointer);
		return this;
	}

	/**
	 * Runs maven build with goals against project.
	 */
	public Project runMavenBuild(String... goals) throws VerificationException {
		MavenUtil.forProject(versionPointer).forkJvm().executeGoals(goals);
		return this;
	}

	/**
	 * Will prepare deployment structure for binary build.</br>
	 *
	 * Create directories configuration and deployments. In configuration will by copied everything from configuration
	 * folder. To the deployments dir will be copied all war, eap and jar artifacts from target dir.
	 */
	public Project prepareForBinaryBuild() throws IOException {
		Path tmpBin = projectDir.resolve("bin");
		tmpBin.toFile().mkdir();
		String[] targetPackages = versionPointer.resolve("target").toFile().list(new PackageFileFilter());

		if(targetPackages.length > 0) {
			tmpBin.resolve("deployments").toFile().mkdir();
			for(String deployment : targetPackages) {
				IOUtils.copy(versionPointer.resolve("target").resolve(deployment), tmpBin.resolve("deployments").resolve(deployment));
			}
		}

		if(versionPointer.resolve("configuration").toFile().exists()) {
			tmpBin.resolve("configuration").toFile().mkdir();
			IOUtils.copy(versionPointer.resolve("configuration"), tmpBin.resolve("configuration"));
		}

		versionPointer = tmpBin;
		return this;
	}

	/**
	 * @return path to modified application
	 */
	public Path getPath() {
		return versionPointer;
	}
}
