package cz.xtf.build;

import cz.xtf.io.IOUtils;
import cz.xtf.maven.MavenUtil;
import org.apache.maven.it.VerificationException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class for dynamic manipulation with applications.
 */
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

	private static final Path PROJECTS_TMP_DIR;

	static {
		PROJECTS_TMP_DIR = IOUtils.TMP_DIRECTORY.resolve("projects");
		PROJECTS_TMP_DIR.toFile().mkdirs();
	}

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
