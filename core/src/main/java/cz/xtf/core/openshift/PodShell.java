package cz.xtf.core.openshift;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.WaiterException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

@Slf4j
public class PodShell {
    private final OpenShift openShift;
    private final String podName;
    private final String containerName;

    private final ByteArrayOutputStream baosOutput;
    private final ByteArrayOutputStream baosError;

    public PodShell(OpenShift openShift, String dcName) {
        this(openShift, openShift.getAnyPod(dcName));
    }

    public PodShell(OpenShift openShift, Pod pod) {
        this(openShift, pod, null);
    }

    public PodShell(OpenShift openShift, Pod pod, String containerName) {
        this.openShift = openShift;
        this.podName = pod.getMetadata().getName();
        this.containerName = containerName;

        this.baosOutput = new ByteArrayOutputStream();
        this.baosError = new ByteArrayOutputStream();
    }

    public PodShellOutput executeWithBash(String command) {
        return execute("bash", "-c", command);
    }

    public PodShellOutput execute(String... commands) {
        baosOutput.reset();
        baosError.reset();

        StateExecListener execListener = new StateExecListener();

        if (containerName == null) {
            openShift.pods().withName(podName).writingOutput(baosOutput).writingError(baosError).usingListener(execListener)
                    .exec(commands);
        } else {
            openShift.pods().withName(podName).inContainer(containerName).writingOutput(baosOutput).writingError(baosError)
                    .usingListener(execListener).exec(commands);
        }

        new SimpleWaiter(execListener::hasExecutionFinished).timeout(TimeUnit.MINUTES, 1)
                .reason("Waiting for " + Arrays.toString(commands) + " execution in '" + podName + "' pod.").waitFor();
        try {
            new SimpleWaiter(() -> baosOutput.size() > 0 || baosError.size() > 0).timeout(TimeUnit.SECONDS, 10).waitFor();
        } catch (WaiterException e) {
            log.warn("Output from PodShell's execution didn't appear in 10 seconds after channel close.");
        }

        return new PodShellOutput(baosOutput.toString().trim(), baosError.toString().trim());
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
