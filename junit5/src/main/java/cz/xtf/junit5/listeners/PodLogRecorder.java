package cz.xtf.junit5.listeners;

import static org.junit.platform.engine.TestExecutionResult.Status.FAILED;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PodLogRecorder implements TestExecutionListener {
    private String getTestDisplayName(TestIdentifier testIdentifier) {
        String className = testIdentifier.getParentId().get()
                .replaceAll(".*class:", "")
                .replaceAll("].*", "");
        return String.format("%s#%s", className, testIdentifier.getDisplayName());
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        final Path podLogDir = Paths.get("log", "pods");
        if (testIdentifier.isTest() && FAILED.equals(testExecutionResult.getStatus())) {
            final Path testPodLogDir = podLogDir.resolve(getTestDisplayName(testIdentifier));
            final OpenShift openShift = OpenShifts.master();
            for (Pod pod : openShift.getPods()) {
                try {
                    openShift.storePodLog(pod, testPodLogDir, pod.getMetadata().getName() + ".log");
                } catch (IOException e) {
                    log.warn("IOException storing pod logs", e);
                } catch (KubernetesClientException e) {
                    log.warn("KubernetesClientException getting pod logs", e);
                }
            }
        }
    }
}
