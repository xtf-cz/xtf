package cz.xtf.junit5.listeners;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import cz.xtf.core.bm.BuildManager;
import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.bm.ManagedBuild;
import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import cz.xtf.core.waiting.WaiterException;
import cz.xtf.junit5.annotations.SinceVersion;
import cz.xtf.junit5.annotations.SkipFor;
import cz.xtf.junit5.annotations.UsesBuild;
import cz.xtf.junit5.config.JUnitConfig;
import cz.xtf.junit5.extensions.SinceVersionCondition;
import cz.xtf.junit5.extensions.SkipForCondition;
import cz.xtf.junit5.interfaces.BuildDefinition;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManagedBuildPrebuilder implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        List<BuildDefinition> buildsToBeBuilt = new LinkedList<>();
        Set<BuildDefinition> buildsSeen = new HashSet<>();

        for (TestIdentifier root : testPlan.getRoots()) {
            process(buildsToBeBuilt, buildsSeen, root);

            for (TestIdentifier descendant : testPlan.getDescendants(root)) {
                process(buildsToBeBuilt, buildsSeen, descendant);
            }
        }

        List<Runnable> deferredWaits = new LinkedList<>();
        BuildManager buildManager = null;

        // Attempting to start builds when they cannot start due to running pod limits cause random timeouts,
        // so we try to be nice and wait until there are available capacity in the build manager namespace.
        final OpenShift buildManagerOpenShift = OpenShifts.master(BuildManagerConfig.namespace());
        Waiter runningBuildsBelowCapacity = new SimpleWaiter(() -> buildManagerOpenShift.getBuilds().stream()
                .filter(build -> build.getStatus() != null && "Running".equals(build.getStatus().getPhase()))
                .count() < BuildManagerConfig.maxRunningBuilds())
                        .timeout(TimeUnit.MILLISECONDS, WaitingConfig.timeout())
                        .reason("Waiting for a free capacity for running builds in " + BuildManagerConfig.namespace()
                                + " namespace.");

        for (final BuildDefinition buildDefinition : buildsToBeBuilt) {
            // lazy creation, so that we don't attempt to create a buildmanager namespace when no builds defined (e.g. OSO tests)
            if (buildManager == null) {
                buildManager = BuildManagers.get();
            }

            try {
                runningBuildsBelowCapacity.waitFor();
            } catch (WaiterException x) {
                log.warn("Timeout waiting for free capacity", x);
            } catch (KubernetesClientException x) {
                log.warn("KubernetesClientException waiting for free capacity in {} namespace", BuildManagerConfig.namespace(),
                        x);
            }

            log.debug("Building {}", buildDefinition);
            final ManagedBuild managedBuild = buildDefinition.getManagedBuild();

            try {
                buildManager.deploy(managedBuild);

                final Waiter buildCompleted = buildManager.hasBuildCompleted(managedBuild);
                Runnable waitForBuild = () -> {
                    try {
                        boolean status = buildCompleted.waitFor();
                        if (!status) {
                            log.warn("Build {} failed!", buildDefinition);
                        }
                    } catch (WaiterException x) {
                        log.warn("Timeout building {}", buildDefinition, x);
                    } catch (KubernetesClientException x) {
                        log.warn("KubernetesClientException waiting for {}", buildDefinition, x);
                    }
                };

                // If synchronized, we wait for each individual build
                if (JUnitConfig.prebuilderSynchronized()) {
                    waitForBuild.run();
                } else {
                    deferredWaits.add(waitForBuild);
                }
            } catch (KubernetesClientException x) {
                // if the build failed, we need to treat the managed build as broken, better to delete it (so that the test itself can try again)
                log.error("Error building {}", buildDefinition, x);

                try {
                    managedBuild.delete(buildManagerOpenShift);
                } catch (KubernetesClientException y) {
                    log.error("Cannot delete managed build {}, ignoring...", buildDefinition, y);
                }
            }
        }

        // If not synchronized, we wait for the builds after all have been started
        if (!JUnitConfig.prebuilderSynchronized()) {
            for (Runnable deferredWait : deferredWaits) {
                deferredWait.run();
            }
        }
    }

    private void addBuildDefinition(List<BuildDefinition> buildsToBeBuilt, Set<BuildDefinition> buildsSeen,
            BuildDefinition buildDefinition) {
        if (!buildsSeen.contains(buildDefinition)) {
            buildsSeen.add(buildDefinition);
            buildsToBeBuilt.add(buildDefinition);
        }
    }

    private void process(List<BuildDefinition> buildsToBeBuilt, Set<BuildDefinition> buildsSeen, TestIdentifier identifier) {
        if (identifier.getSource().isPresent()) {
            TestSource testSource = identifier.getSource().get();
            if (testSource instanceof ClassSource) {
                ClassSource classSource = (ClassSource) testSource;
                Class klass = classSource.getJavaClass();

                log.debug("Processing {}", klass);

                // We don't want to prepare build when whole test case is skipped
                boolean classSkippedForStream = Arrays.stream(klass.getAnnotationsByType(SkipFor.class))
                        .anyMatch(a -> SkipForCondition.resolve((SkipFor) a).isDisabled());
                boolean classSkippedForTestedVersion = Arrays.stream(klass.getAnnotationsByType(SinceVersion.class))
                        .anyMatch(a -> SinceVersionCondition.resolve((SinceVersion) a).isDisabled());
                if (classSkippedForStream || classSkippedForTestedVersion) {
                    return;
                }

                Arrays.stream(klass.getAnnotations()).filter(a -> a.annotationType().getAnnotation(UsesBuild.class) != null)
                        .forEach(a -> {
                            try {
                                Object result = a.annotationType().getMethod("value").invoke(a);
                                if (result instanceof BuildDefinition) {
                                    addBuildDefinition(buildsToBeBuilt, buildsSeen, (BuildDefinition) result);
                                } else if (result instanceof BuildDefinition[]) {
                                    Stream.of((BuildDefinition[]) result)
                                            .forEach(x -> addBuildDefinition(buildsToBeBuilt, buildsSeen, x));
                                } else {
                                    log.error(
                                            "Value present in {} is not instance of {}, not able to get ManagedBuild to be built",
                                            result, BuildDefinition.class);
                                }
                            } catch (Exception e) {
                                log.error("Failed to invoke value() on annotation " + a.annotationType().getName(), e);
                            }
                        });
            }
        }
    }
}
