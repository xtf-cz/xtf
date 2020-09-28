package cz.xtf.core.openshift.helpers;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;

public class ResourceParsers {

    public static boolean isPodReady(Pod pod) {
        return pod.getStatus().getContainerStatuses().size() > 0
                && pod.getStatus().getContainerStatuses().stream().allMatch(ContainerStatus::getReady);
    }

    public static boolean isPodRunning(Pod pod) {
        return "Running".equals(pod.getStatus().getPhase());
    }

    public static boolean hasPodRestarted(Pod pod) {
        return hasPodRestartedAtLeastNTimes(pod, 1);
    }

    public static boolean hasPodRestartedAtLeastNTimes(Pod pod, int n) {
        return pod.getStatus().getContainerStatuses().size() > 0
                && pod.getStatus().getContainerStatuses().stream().anyMatch(x -> x.getRestartCount() >= n);
    }

    private ResourceParsers() {

    }
}
