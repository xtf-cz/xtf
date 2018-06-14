package cz.xtf.core.openshift;

import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
public class PodShell {
	private final OpenShift openShift;
	private final String podName;

	private ByteArrayOutputStream baosOutput;
	private ByteArrayOutputStream baosError;

	public PodShell(String dcName, OpenShift openShift) {
		this(openShift.getAnyPod(dcName), openShift);
	}

	public PodShell(Pod pod, OpenShift openShift) {
		this.openShift = openShift;
		this.podName = pod.getMetadata().getName();

		this.baosOutput = new ByteArrayOutputStream();
		this.baosError = new ByteArrayOutputStream();
	}

	public Waiter executeWithBash(String command) {
		return execute("bash", "-c", command);
	}

	public Waiter execute(String... commands) {
		baosOutput.reset();
		baosError.reset();

		StateExecListener execListener = new StateExecListener();

		openShift.client().pods().withName(podName).writingOutput(baosOutput).writingError(baosError).usingListener(execListener).exec(commands);

		return new SimpleWaiter(execListener::hasExecutionFinished, TimeUnit.MINUTES, 1, "Waiting for" + Arrays.toString(commands) + " execution in '" + podName + "' pod.");
	}

	public String getOutput() {
		return baosOutput.toString();
	}

	public List<String> getOutputAsList(){
		return getOutputAsList("\n");
	}

	public List<String> getOutputAsList(String delimiter){
		String[] result = getOutput().split(delimiter);
		return Arrays.asList(result);
	}

	public Map<String, String> getOutputAsMap(String keyValueDelimiter) {
		return getOutputAsMap(keyValueDelimiter, "\n");
	}

	public Map<String, String> getOutputAsMap(String keyValueDelimiter, String entryDelimiter) {
		Map<String, String> map = new HashMap<>();

		Stream.of(getOutput().split(entryDelimiter)).forEach(entry -> {
			String[] parsedEntry = entry.split(keyValueDelimiter, 2);
			map.put(parsedEntry[0], parsedEntry[1]);
		});

		return map;
	}

	public String getError() {
		return baosError.toString();
	}

	public class StateExecListener implements ExecListener {
		private final AtomicBoolean executionDone = new AtomicBoolean(false);

		@Override
		public void onOpen(Response response) {
			// DO NOTHING
		}

		@Override
		public void onFailure(Throwable throwable, Response response) {
			// DO NOTHING
		}

		@Override
		public void onClose(int i, String s) {
			executionDone.set(true);
		}

		public boolean hasExecutionFinished() {
			return executionDone.get();
		}
	}
}
