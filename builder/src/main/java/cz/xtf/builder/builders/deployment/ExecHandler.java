package cz.xtf.builder.builders.deployment;

import io.fabric8.kubernetes.api.model.HandlerBuilder;

class ExecHandler implements Handler {

    private String[] cmdLine;

    ExecHandler(final String... cmdLine) {
        this.cmdLine = cmdLine;
    }

    @Override
    public io.fabric8.kubernetes.api.model.Handler build() {
        return new HandlerBuilder()
                .withNewExec()
                .withCommand(cmdLine)
                .endExec()
                .build();
    }
}
