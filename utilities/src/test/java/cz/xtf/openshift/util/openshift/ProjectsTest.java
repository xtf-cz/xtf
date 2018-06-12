package cz.xtf.openshift.util.openshift;

import cz.xtf.openshift.OpenShiftUtil;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectRequest;
import org.assertj.core.api.Assertions;
import org.junit.*;

import java.net.MalformedURLException;
import java.util.concurrent.TimeoutException;

// Integration tests with ocp util working with projects
// To run tests comment out @Ignore and setup url, username and password.
@Ignore
public class ProjectsTest {
	private static final String testNamespace = "test-project";

	private OpenShiftUtil getInstance() {
		try {
			String url = null;
			String username = null;
			String password = null;

			return new OpenShiftUtil(url, testNamespace, username, password);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Malformed OpenShift URL unabled to execute tests");
		}
	}

	@Test
	public void createGetAndDeleteProject() throws Exception {
		OpenShiftUtil openshift = getInstance();

		String customNamespace = "custom-test-namespace";

		ProjectRequest projectRequest = openshift.createProjectRequest(customNamespace);
		Assertions.assertThat(projectRequest).isNotNull();
		Assertions.assertThat(projectRequest.getMetadata().getName()).isEqualTo(customNamespace);

		Project project = openshift.getProject(customNamespace);
		Assertions.assertThat(project).isNotNull();
		Assertions.assertThat(project.getMetadata().getName()).isEqualTo(customNamespace);

		boolean deleted = openshift.deleteProject(customNamespace);
		Assertions.assertThat(deleted).isTrue();

		while(openshift.getProject(customNamespace) != null) {
			Thread.sleep(1_000L);
		}
	}

	@Test(expected = KubernetesClientException.class)
	public void createExistingProject() throws Exception {
		OpenShiftUtil openshift = getInstance();

		ProjectRequest projectRequest = openshift.createProjectRequest();
		Assertions.assertThat(projectRequest).isNotNull();
		Assertions.assertThat(projectRequest.getMetadata().getName()).isEqualTo(testNamespace);

		Project project = openshift.getProject(testNamespace);
		Assertions.assertThat(project).isNotNull();
		Assertions.assertThat(project.getMetadata().getName()).isEqualTo(testNamespace);

		openshift.createProjectRequest(testNamespace);
	}

	@Test
	public void recreateExistingProject() throws Exception {
		OpenShiftUtil openshift = getInstance();

		ProjectRequest projectRequest1 = openshift.createProjectRequest();
		Assertions.assertThat(projectRequest1).isNotNull();
		Assertions.assertThat(projectRequest1.getMetadata().getName()).isEqualTo(testNamespace);

		Project project1 = openshift.getProject(testNamespace);
		Assertions.assertThat(project1).isNotNull();
		Assertions.assertThat(project1.getMetadata().getName()).isEqualTo(testNamespace);

		ProjectRequest projectRequest2 = openshift.recreateProject(testNamespace);
		Assertions.assertThat(projectRequest2).isNotNull();
		Assertions.assertThat(projectRequest2.getMetadata().getName()).isEqualTo(testNamespace);

		Project project2 = openshift.getProject(testNamespace);
		Assertions.assertThat(project2).isNotNull();
		Assertions.assertThat(project2.getMetadata().getName()).isEqualTo(testNamespace);

		String timestamp1 = project1.getMetadata().getCreationTimestamp();
		String timestamp2 = project2.getMetadata().getCreationTimestamp();

		Assertions.assertThat(timestamp1.compareTo(timestamp2)).isLessThan(0);
	}

	@Test
	public void recreateNonExistingProject() throws Exception {
		OpenShiftUtil openshift = getInstance();

		ProjectRequest projectRequest = openshift.recreateProject();
		Assertions.assertThat(projectRequest).isNotNull();
		Assertions.assertThat(projectRequest.getMetadata().getName()).isEqualTo(testNamespace);

		Project project = openshift.getProject(testNamespace);
		Assertions.assertThat(project).isNotNull();
		Assertions.assertThat(project.getMetadata().getName()).isEqualTo(testNamespace);
	}

	@Test
	public void deleteNonExistingProject() {
		Assertions.assertThat(getInstance().deleteProject("i-do-not-exist")).isFalse();
	}

	@After
	@Before
	public void deleteTestProject() throws InterruptedException, TimeoutException {
		OpenShiftUtil openshift = getInstance();
		boolean deleted = openshift.deleteProject(testNamespace);

		if(deleted) {
			while(openshift.getProject(testNamespace) != null) {
				Thread.sleep(1_000L);
			}
		}
	}
}
