package cz.xtf.builder.builders.deployment;

import io.fabric8.kubernetes.api.model.LifecycleHandler;

public interface Handler {
    LifecycleHandler build();

    static Handler createExecHandler(final String... cmdLine) {
        return new ExecHandler(cmdLine);
    }
}
