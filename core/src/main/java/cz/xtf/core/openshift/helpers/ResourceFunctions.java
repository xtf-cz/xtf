package cz.xtf.core.openshift.helpers;

import java.util.List;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.Pod;

public class ResourceFunctions {

    public static Function<List<Pod>, Boolean> areExactlyNPodsReady(int n) {
        return pods -> pods.stream().filter(ResourceParsers::isPodReady).count() == n;
    }

    public static Function<List<Pod>, Boolean> areExactlyNPodsRunning(int n) {
        return pods -> pods.stream().filter(ResourceParsers::isPodRunning).count() == n;
    }

    public static Function<List<Pod>, Boolean> haveAnyPodRestarted() {
        return haveAnyPodRestartedAtLeastNTimes(1);
    }

    public static Function<List<Pod>, Boolean> haveAnyPodRestartedAtLeastNTimes(int n) {
        return pods -> pods.stream().anyMatch(pod -> ResourceParsers.hasPodRestartedAtLeastNTimes(pod, n));
    }

    private ResourceFunctions() {

    }
}
