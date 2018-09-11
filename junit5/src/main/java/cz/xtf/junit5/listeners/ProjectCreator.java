package cz.xtf.junit5.listeners;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.config.JUnitConfig;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class ProjectCreator implements TestExecutionListener {
	private static final OpenShift openShift = OpenShifts.master();

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		if(openShift.getProject(OpenShiftConfig.namespace()) == null) {
			openShift.createProjectRequest();
		}
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {
		if(JUnitConfig.cleanOpenShift()) {
			openShift.deleteProject();
		}
	}
}
