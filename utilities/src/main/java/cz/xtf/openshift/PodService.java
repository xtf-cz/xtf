package cz.xtf.openshift;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.api.model.Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PodService implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(PodService.class);

	public enum RSyncStrategy {
		rsync, tar
	}

	private final Pod pod;
	private final OpenShiftBinaryClient oc = OpenShiftBinaryClient
			.getInstance();

	private Process runningProcess;
	
	public PodService(final Pod pod) {
		this.pod = pod;
		oc.project(OpenshiftUtil.getInstance().getContext().getNamespace());
	}

	public void rsync(final Path localDir, final String remoteDir,
			final RSyncStrategy strategy, final String includes, boolean toPod) {
		final List<String> args = new ArrayList<>();
		args.add("rsync");
		if (strategy != null) {
			args.add("--strategy");
			args.add(strategy.toString());
		}
		if (includes != null) {
			args.add("--include");
			args.add(includes);
		}
		args.add(!toPod ? (pod.getMetadata().getName() + ":" + remoteDir + "/") : localDir.toFile()
				.getAbsoluteFile().getPath() + "/");
		args.add(toPod ? (pod.getMetadata().getName() + ":" + remoteDir + "/") : localDir.toFile()
				.getAbsoluteFile().getPath() + "/");
		oc.executeCommand("rsync has failed", args.toArray(new String[args.size()]));
	}

	public void rsyncTo(final Path localDir, final String remoteDir,
			final String includes) {
		rsync(localDir, remoteDir, RSyncStrategy.tar, includes, true);
	}

	public void rsyncFrom(final Path localDir, final String remoteDir,
			final String includes) {
		rsync(localDir, remoteDir, RSyncStrategy.tar, includes, false);
	}
	
	public int portForward(final int localPort, final int remotePort) {
		if (runningProcess != null && runningProcess.isAlive()) {
			throw new IllegalStateException("Another process running " + runningProcess);
		}
		runningProcess = oc.executeCommandNoWait("port-forward has failed", "port-forward", pod.getMetadata().getName(), localPort + ":" + remotePort);

		return localPort;
	}

	public int portForward(final int remotePort) throws InterruptedException, TimeoutException {
		if (runningProcess != null && runningProcess.isAlive()) {
			throw new IllegalStateException("Another process running " + runningProcess);
		}
		runningProcess = oc.executeCommandNoWaitWithOutputAndError("port-forward has failed", "port-forward", pod.getMetadata().getName(), ":" + remotePort);

		final Pattern pattern = Pattern.compile("Forwarding from 127.0.0.1:(\\d+) -> (\\d)+");

		final Semaphore s = new Semaphore(0);
		final AtomicInteger port = new AtomicInteger(0);

		// A thread that sucks the output and tries to read the source port from the log
		ExecutorService outputSucker = Executors.newFixedThreadPool(2);
		outputSucker.submit(() -> {
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream(), "UTF-8"));

				String line;
				while((line = br.readLine()) != null) {
					LOGGER.debug("oc port-forward stderr: " + line);
					if (port.get() == 0) {
						Matcher matcher = pattern.matcher(line);
						if (matcher.find()) {
							// We have read the port, let the semaphore know and continue sucking output
							port.set(Integer.parseInt(matcher.group(1)));
							LOGGER.debug("oc port-forward stderr, read local port: " + port.get());
							s.release();
						}
					}
				}

				LOGGER.debug("port forward stderr sucker ending");
			}
			catch (IOException x) {
				LOGGER.error("Error reading output of 'oc port-forward' stderr", x);
			}
		});

		outputSucker.submit(() -> {
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(runningProcess.getInputStream(), "UTF-8"));

				String line;
				while((line = br.readLine()) != null) {
					LOGGER.debug("oc port-forward stdout: " + line);
					if (port.get() == 0) {
						Matcher matcher = pattern.matcher(line);
						if (matcher.find()) {
							// We have read the port, let the semaphore know and continue sucking output
							port.set(Integer.parseInt(matcher.group(1)));
							LOGGER.debug("oc port-forward stdout, read local port: " + port.get());
							s.release();
						}
					}
				}

				LOGGER.debug("port forward stdout sucker ending");
			}
			catch (IOException x) {
				LOGGER.error("Error reading output of 'oc port-forward' stdout", x);
			}
		});

		outputSucker.shutdown();

		// We will wait until we have read the port
		if (!s.tryAcquire(1, TimeUnit.MINUTES)) {
			throw new TimeoutException("Didn't read the port in 60 seconds!");
		}

		return port.get();
	}

	public String exec(String... command) {
		final List<String> args = new ArrayList<>();
		args.add("exec");
		args.add(pod.getMetadata().getName());
		args.add("--");
		args.addAll(Arrays.asList(command));
		return oc.executeCommandWithReturn("remote execution has failed", args.toArray(new String[args.size()]));
	}

	public void execAndConsume(OpenShiftBinaryClient.CommandResultConsumer consumer, String... command) {
		final List<String> args = new ArrayList<>();
		args.add("exec");
		args.add(pod.getMetadata().getName());
		args.add("--");
		args.addAll(Arrays.asList(command));
		oc.executeCommandAndConsumeOutput("remote execution has failed", consumer, args.toArray(new String[args.size()]));
	}

	@Override
	public void close() throws Exception {
		if (runningProcess != null) {
			runningProcess.destroyForcibly();
			runningProcess = null;
		}
	}
}
