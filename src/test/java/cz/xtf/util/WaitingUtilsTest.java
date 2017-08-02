package cz.xtf.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static cz.xtf.util.WaitingUtils.DEFAULT_MILLIS_TO_WAIT;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;

@RunWith(PowerMockRunner.class)
@PrepareForTest(WaitingUtils.class)
public class WaitingUtilsTest {

	@Before
	public void setUp() throws Exception {
		PowerMockito.spy(Thread.class);
		PowerMockito.doThrow(new InterruptedException()).when(Thread.class);
		Thread.sleep(anyLong());
	}

	@Test
	public void noExceptionShouldBeThrownAndThreadShouldWaitDefaultMillis() throws Exception {
		WaitingUtils.waitSilently();
		WaitingUtils.waitSilently("sleep");

		PowerMockito.verifyStatic(times(2));
		Thread.sleep(DEFAULT_MILLIS_TO_WAIT);
	}

	@Test
	public void noExceptionShouldBeThrownAndThreadShouldWaitDefinedMillis() throws Exception {
		final long millis = 500;
		WaitingUtils.waitSilently(millis);
		WaitingUtils.waitSilently(millis, "sleep");

		PowerMockito.verifyStatic(times(2));
		Thread.sleep(millis);
	}
}
