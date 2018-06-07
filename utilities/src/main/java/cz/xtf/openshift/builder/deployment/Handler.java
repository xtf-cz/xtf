package cz.xtf.openshift.builder.deployment;

public interface Handler {
	io.fabric8.kubernetes.api.model.Handler build();

	static Handler createExecHandler(final String... cmdLine) {
		return new ExecHandler(cmdLine);
	}
}
