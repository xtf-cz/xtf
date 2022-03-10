package cz.xtf.builder.builders.deployment;

import io.fabric8.kubernetes.api.model.LifecycleHandlerBuilder;

class ExecHandler implements Handler {

    private String[] cmdLine;

    ExecHandler(final String... cmdLine) {
        this.cmdLine = cmdLine;
    }

    @Override
    public io.fabric8.kubernetes.api.model.LifecycleHandler build() {
        return new LifecycleHandlerBuilder()
                .withNewExec()
                .withCommand(cmdLine)
                .endExec()
                .build();
    }
}
