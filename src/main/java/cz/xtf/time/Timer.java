package cz.xtf.time;

import java.util.concurrent.TimeUnit;

public class Timer {

	long targetMillis;
	final long interval;
	
	public Timer(final long till, final TimeUnit timeUnit) {
		this.interval = timeUnit.toMillis(till);
		reset();
	}

	public boolean elapsed() {
		return System.currentTimeMillis() > targetMillis;
	}
	
	public void reset() {
		targetMillis = System.currentTimeMillis() + interval;
	}
}
