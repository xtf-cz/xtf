package cz.xtf.core.bm;

import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuildManagers {
    private static BuildManager bm;

    public static BuildManager get() {
        if (bm == null) {
            synchronized (BuildManager.class) {
                if (bm == null) {
                    String buildNamespace = BuildManagerConfig.namespace();

                    bm = new BuildManager(OpenShifts.master(buildNamespace));

                    // If the build namespace is in a separate namespace to "master" namespace, we assume it is a shared namespace
                    if (!BuildManagerConfig.namespace().equals(OpenShiftConfig.namespace())) {
                        OpenShift admin = OpenShifts.admin(buildNamespace);

                        try {
                            if (admin.getResourceQuota("max-running-builds") == null) {
                                ResourceQuota rq = new ResourceQuotaBuilder()
                                        .withNewMetadata().withName("max-running-builds").endMetadata()
                                        .withNewSpec()
                                        .addToHard("pods",
                                                new Quantity(String.format("%d", BuildManagerConfig.maxRunningBuilds())))
                                        .endSpec()
                                        .build();
                                admin.createResourceQuota(rq);
                            }
                        } catch (KubernetesClientException e) {
                            log.warn("Attempt to add hard resource quota on {} namespace failed!", buildNamespace);
                        }
                    }
                }
            }
        }
        return bm;
    }
}
