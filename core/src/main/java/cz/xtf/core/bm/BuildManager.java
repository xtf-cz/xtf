package cz.xtf.core.bm;

import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuildManager {
    private final OpenShift openShift;

    public BuildManager(OpenShift openShift) {
        this.openShift = openShift;

        if (openShift.getProject(openShift.getNamespace()) == null) {
            openShift.createProjectRequest();
            openShift.waiters().isProjectReady().waitFor();

            try {
                // Adding a label can be only done via 'namespace'. It cannot be set via 'project' API. Thus we do this
                // separately. Also, to update namespace label, it's necessary to have 'patch resource "namespaces"'
                // permission for current user and updated namespace, e.g. by having 'cluster-admin' role.
                // Otherwise you can see:
                // $ oc label namespace <name> "label1=foo"
                // Error from server (Forbidden): namespaces "<name>" is forbidden: User "<user>" cannot patch resource "namespaces" in API group "" in the namespace "<name>"
                OpenShifts.admin().namespaces().withName(openShift.getNamespace()).edit().editMetadata().addToLabels(
                        OpenShift.XTF_MANAGED_LABEL, "true").endMetadata().done();
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

        openShift.addRoleToGroup("system:image-puller", "ClusterRole", "system:authenticated");
    }

    public ManagedBuildReference deploy(ManagedBuild managedBuild) {
        if (BuildManagerConfig.forceRebuild()) {
            log.info("Force rebuilding is enabled... Building '{}' ...", managedBuild.getId());
            managedBuild.delete(openShift);
            managedBuild.build(openShift);
        } else if (!managedBuild.isPresent(openShift)) {
            log.info("Managed build '{}' is not present... Building...", managedBuild.getId());
            managedBuild.build(openShift);
        } else if (managedBuild.needsUpdate(openShift)) {
            log.info("Managed build '{}' is not up to date... Building...", managedBuild.getId());
            managedBuild.update(openShift);
        } else {
            log.info("Managed build '{}' is up to date.", managedBuild.getId());
        }

        return getBuildReference(managedBuild);
    }

    public Waiter hasBuildCompleted(ManagedBuild managedBuild) {
        return managedBuild.hasCompleted(openShift);
    }

    public ManagedBuildReference getBuildReference(ManagedBuild managedBuild) {
        return new ManagedBuildReference(managedBuild.getId(), "latest", openShift.getNamespace());
    }

    public OpenShift openShift() {
        return openShift;
    }
}
