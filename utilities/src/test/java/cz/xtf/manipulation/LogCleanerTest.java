package cz.xtf.manipulation;

import cz.xtf.openshift.OpenshiftUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.file.Path;
import java.nio.file.Paths;

import static cz.xtf.openshift.OpenshiftUtil.getBuildLogsDir;
import static cz.xtf.openshift.OpenshiftUtil.getPodLogsDir;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest(OpenshiftUtil.class)
public class LogCleanerTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Before
	public void setUp() {
		PowerMockito.mockStatic(OpenshiftUtil.class);
		PowerMockito.when(getPodLogsDir()).thenReturn(logsDir());
		PowerMockito.when(getBuildLogsDir()).thenReturn(logsDir());
	}

	@Test
	public void buildLogsDirShouldBeCreatedIfNotExists() {
		LogCleaner.cleanBuildLogDirectories();
		assertThat(logsDir().toFile().exists()).isTrue();
	}

	@Test
	public void podLogsDirShouldBeCreatedIfNotExists() {
		LogCleaner.cleanPodLogDirectories();
		assertThat(logsDir().toFile().exists()).isTrue();
	}

	@Test
	public void buildLogsDirShouldBeCleanedAndRecreated() throws Exception {
		temp.newFile();
		LogCleaner.cleanBuildLogDirectories();
		assertThat(logsDir().toFile().listFiles()).isEmpty();
	}

	@Test
	public void podLogsDirShouldBeCleanedAndRecreated() throws Exception {
		temp.newFile();
		LogCleaner.cleanPodLogDirectories();
		assertThat(logsDir().toFile().listFiles()).isEmpty();
	}

	private Path logsDir() {
		return Paths.get(temp.getRoot().getAbsolutePath());
	}
}
