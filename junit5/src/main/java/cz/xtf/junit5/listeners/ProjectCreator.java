package cz.xtf.junit5.listeners;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.junit5.config.JUnitConfig;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class ProjectCreator implements TestExecutionListener {
	private static final OpenShift openShift = OpenShifts.master();

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		if(openShift.getProject() == null) {
			openShift.createProjectRequest();
		}
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {
		if(JUnitConfig.cleanOpenShift()) {
			boolean deleted = openShift.deleteProject();
			// For multi-module maven projects, other modules may attempt to crate project requests immediately after this modules deleteProject
			if (deleted) {
				BooleanSupplier bs = () -> openShift.getProject() == null;
				new SimpleWaiter(bs, TimeUnit.MINUTES, 2, "Waiting for old project deletion").waitFor();
			}
		}
	}
}
