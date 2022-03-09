package cz.xtf.junit5.listeners;

import static cz.xtf.core.config.OpenShiftConfig.OPENSHIFT_NAMESPACE;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.engine.descriptor.MethodBasedTestDescriptor;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.config.TestCaseContext;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.junit5.config.JUnitConfig;
import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProjectCreator
        implements TestExecutionListener, BeforeAllCallback, BeforeEachCallback, AfterAllCallback, PostDiscoveryFilter {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        createProject(XTFConfig.get(OPENSHIFT_NAMESPACE));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (OpenShiftConfig.useNamespacePerTestcase()) {
            setTestExecutionContext(context);

            log.info("BeforeAll - Test case: " + context.getTestClass().get().getName() + " running in thread name: "
                    + Thread.currentThread().getName()
                    + " will use namespace: " + OpenShifts.master().getNamespace() + " - thread context is: "
                    + TestCaseContext.getTestCaseForCurrentThread());
            createProject(OpenShiftConfig.namespace());
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        // in case that tests in test case are executed in parallel in different threads set the test case thread for each of them
        if (OpenShiftConfig.useNamespacePerTestcase()) {
            setTestExecutionContext(context);
            log.debug("BeforeEach - Test case: " + context.getTestClass().get().getName() + "#"
                    + context.getTestMethod().get().getName() + " running in thread name: "
                    + Thread.currentThread().getName()
                    + " will use namespace: " + OpenShifts.master().getNamespace() + " - thread context is: "
                    + TestCaseContext.getTestCaseForCurrentThread());
        }
    }

    private void setTestExecutionContext(ExtensionContext context) {
        TestCaseContext.setRunningTestCase(context.getTestClass().get().getName());
    }

    private void createProject(String namespace) {
        OpenShift openShift = OpenShifts.master(namespace);

        if (openShift.getProject() == null) {
            log.info("Creating namespace: " + openShift.getNamespace());
            openShift.createProjectRequest();
            openShift.waiters().isProjectReady().waitFor();

            try {
                // Adding a label can be only done via 'namespace'. It cannot be set via 'project' API. Thus we do this
                // separately. Also, to update namespace label, it's necessary to have 'patch resource "namespaces"'
                // permission for current user and updated namespace, e.g. by having 'cluster-admin' role.
                // Otherwise you can see:
                // $ oc label namespace <name> "label1=foo"
                // Error from server (Forbidden): namespaces "<name>" is forbidden: User "<user>" cannot patch resource "namespaces" in API group "" in the namespace "<name>"
                OpenShifts.admin(namespace).namespaces().withName(openShift.getProject().getMetadata().getName())
                        .edit(new Visitor<NamespaceBuilder>() {
                            @Override
                            public void visit(NamespaceBuilder builder) {
                                builder.editMetadata()
                                        .addToLabels(OpenShift.XTF_MANAGED_LABEL, "true");
                            }
                        });
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
        log.info("Created namespace: " + openShift.getNamespace());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (OpenShiftConfig.useNamespacePerTestcase() && JUnitConfig.cleanOpenShift()) {
            deleteProject(OpenShiftConfig.namespace(), false);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (JUnitConfig.cleanOpenShift()) {
            deleteProject(XTFConfig.get(OPENSHIFT_NAMESPACE), false);
        }
    }

    private void deleteProject(String namespace, boolean waitForDeletion) {
        OpenShift openShift = OpenShifts.master(namespace);
        boolean deleted = openShift.deleteProject();
        log.info("Start deleting namespace: " + openShift.getNamespace());
        if (!deleted && waitForDeletion) {
            BooleanSupplier bs = () -> openShift.getProject() == null;
            new SimpleWaiter(bs, TimeUnit.MINUTES, 2, "Waiting for " + openShift.getNamespace() + " project deletion")
                    .waitFor();
        }
    }

    @Override
    public FilterResult apply(TestDescriptor testDescriptor) {
        if (testDescriptor instanceof MethodBasedTestDescriptor) {
            boolean disabled = Arrays.stream(((MethodBasedTestDescriptor) testDescriptor).getTestClass().getAnnotations())
                    .filter(annotation -> annotation instanceof Disabled).count() > 0;
            if (!disabled) {
                TestCaseContext.getRunningTestcase().putIfAbsent(
                        ((MethodBasedTestDescriptor) testDescriptor).getTestClass().getName(),
                        getNameSpaceForTestClass(testDescriptor));

            }
        }
        return FilterResult.included(testDescriptor.getDisplayName());
    }

    private String getNameSpaceForTestClass(TestDescriptor testDescriptor) {
        if (OpenShiftConfig.useNamespacePerTestcase()) {
            // some test case names can be really long resulting in long namespace names. This can cause issues
            // with routes which have 64 chars limit for prefix of domain name. In case of route like:
            // galleon-provisioning-xml-prio-mnovak-galleonprovisioningxmltest.apps.eapqe-024-dryf.eapqe.psi.redhat.com
            // route prefix is: galleon-provisioning-xml-prio-mnovak-galleonprovisioningxmltest
            // route suffix is: .apps.eapqe-024-dryf.eapqe.psi.redhat.com
            if ((XTFConfig.get(OPENSHIFT_NAMESPACE) + "-"
                    + testDescriptor.getParent().get().getDisplayName().toLowerCase())
                            .length() > OpenShiftConfig.getNamespaceLengthLimitForUniqueNamespacePerTest()) {

                return XTFConfig.get(OPENSHIFT_NAMESPACE) + "-"
                        + StringUtils.truncate(DigestUtils.sha256Hex(testDescriptor.getParent().get().getDisplayName()
                                .toLowerCase()),
                                OpenShiftConfig.getNamespaceLengthLimitForUniqueNamespacePerTest()
                                        - XTFConfig.get(OPENSHIFT_NAMESPACE).length());
            } else {
                return XTFConfig.get(OPENSHIFT_NAMESPACE) + "-"
                        + testDescriptor.getParent().get().getDisplayName().toLowerCase(); // namespace must not have upper case letters
            }
        } else {
            return OpenShiftConfig.namespace();
        }
    }
}
