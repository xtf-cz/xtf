package cz.xtf.junit5.listeners;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.junit5.config.JUnitConfig;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProjectCreator implements TestExecutionListener {
    private static final OpenShift openShift = OpenShifts.master();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (openShift.getProject() == null) {
            openShift.createProjectRequest();
            openShift.waiters().isProjectReady().waitFor();

            try {
                // Adding a label can be only done via 'namespace'. It cannot be set via 'project' API. Thus we do this
                // separately. Also, to update namespace label, it's necessary to have 'patch resource "namespaces"'
                // permission for current user and updated namespace, e.g. by having 'cluster-admin' role.
                // Otherwise you can see:
                // $ oc label namespace <name> "label1=foo"
                // Error from server (Forbidden): namespaces "<name>" is forbidden: User "<user>" cannot patch resource "namespaces" in API group "" in the namespace "<name>"
                OpenShifts.admin().namespaces().withName(openShift.getProject().getMetadata().getName()).edit().editMetadata()
                        .addToLabels(OpenShift.XTF_MANAGED_LABEL, "true").endMetadata().done();
            } catch (KubernetesClientException e) {
                // We weren't able to assign a label to the new project. Let's just print warning since this information
                // is not critical to the tests execution. Possible cause for this are insufficient permissions since
                // some projects using XTF are executed on OCP instances without 'admin' accounts available.
                log.warn("Couldn't assign label '" + OpenShift.XTF_MANAGED_LABEL + "' to the new project '"
                        + openShift.getNamespace() + "'. Possible cause are insufficient permissions.");
                log.debug(e.getMessage());
            }
        }
        if (OpenShiftConfig.pullSecret() != null) {
            openShift.setupPullSecret(OpenShiftConfig.pullSecret());
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (JUnitConfig.cleanOpenShift()) {
            boolean deleted = openShift.deleteProject();
            // For multi-module maven projects, other modules may attempt to crate project requests immediately after this modules deleteProject
            if (deleted) {
                BooleanSupplier bs = () -> openShift.getProject() == null;
                new SimpleWaiter(bs, TimeUnit.MINUTES, 2, "Waiting for old project deletion").waitFor();
            }
        }
    }
}
