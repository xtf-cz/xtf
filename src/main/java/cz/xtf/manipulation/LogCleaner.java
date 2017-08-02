package cz.xtf.manipulation;

import cz.xtf.openshift.OpenshiftUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * OpenShift log directories cleaner utility.
 */
@Slf4j
public final class LogCleaner {

	private LogCleaner() {
		// utility class, do not initialize
	}

	/**
	 * Cleans pod & build logs directories.
	 *
	 * @see #cleanPodLogDirectories()
	 * @see #cleanBuildLogDirectories()
	 */
	public static void cleanAllLogDirectories() {
		cleanPodLogDirectories();
		cleanBuildLogDirectories();
	}

	/**
	 * Cleans pod logs directories.
	 *
	 * <p>
	 * In case of {@link IOException}, the exception is logged and ignored.
	 * </p>
	 */
	public static void cleanPodLogDirectories() {
		final File podLogsDir = OpenshiftUtil.getPodLogsDir().toFile();
		log.info("action=clean-pod-logs status=START dir={}", podLogsDir.getAbsolutePath());
		try {
			if (podLogsDir.exists()) {
				FileUtils.deleteDirectory(podLogsDir);
			}
			podLogsDir.mkdirs();
			log.info("action=clean-pod-logs status=FINISH dir={}", podLogsDir.getAbsolutePath());
		} catch (IOException e) {
			log.error("action=clean-pod-logs status=ERROR dir={}", podLogsDir.getAbsolutePath(), e);
		}
	}

	/**
	 * Cleans build logs directories.
	 *
	 * <p>
	 * In case of {@link IOException}, the exception is logged and ignored.
	 * </p>
	 */
	public static void cleanBuildLogDirectories() {
		final File buildLogsDir = OpenshiftUtil.getBuildLogsDir().toFile();
		log.info("action=clean-build-logs status=START dir={}", buildLogsDir.getAbsolutePath());
		try {
			if (buildLogsDir.exists()) {
				FileUtils.deleteDirectory(buildLogsDir);
			}
			buildLogsDir.mkdirs();
			log.info("action=clean-build-logs status=FINISH dir={}", buildLogsDir.getAbsolutePath());
		} catch (IOException e) {
			log.error("action=clean-build-logs status=ERROR dir={}", buildLogsDir.getAbsolutePath(), e);
		}
	}
}
