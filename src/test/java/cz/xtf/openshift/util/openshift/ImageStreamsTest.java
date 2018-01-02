package cz.xtf.openshift.util.openshift;

import cz.xtf.openshift.OpenShiftUtil;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;

// Integration tests with ocp util working with projects
// To run tests comment out @Ignore and setup url, username and password.
@Ignore
public class ImageStreamsTest {
	private static final String defaultTestNamespace = "is-default-test-namespace";
	private static final String customTestNamespace = "is-custom-test-namespace";

	private static OpenShiftUtil getInstance() {
		try {
			String url = null;
			String username = null;
			String password = null;

			return new OpenShiftUtil(url, defaultTestNamespace, username, password);
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Malformed OpenShift URL unabled to execute tests");
		}
	}

	@BeforeClass
	public static void prepareProjects() {
		OpenShiftUtil openshift = getInstance();
		openshift.createProjectRequest();
		openshift.createProjectRequest(customTestNamespace);
	}

	@AfterClass
	public static void deleteProjects() {
		OpenShiftUtil openshift = getInstance();
		openshift.deleteProject();
		openshift.deleteProject(customTestNamespace);
	}

	@Test
	public void crdInDefaultNamespaceTest() {
		OpenShiftUtil openshift = getInstance();

		String streamName = "is-1";

		ImageStream created = openshift.createImageStream(new ImageStreamBuilder().withNewMetadata().withName(streamName).endMetadata().build());
		Assertions.assertThat(created.getMetadata().getName()).isEqualTo(streamName);
		Assertions.assertThat(created.getMetadata().getCreationTimestamp()).isNotNull();

		ImageStream is = openshift.getImageStream(streamName);
		Assertions.assertThat(is.getMetadata().getName()).isEqualTo(streamName);

		boolean deleted = openshift.deleteImageStream(streamName);
		Assertions.assertThat(deleted).isTrue();
		Assertions.assertThat(openshift.getImageStream(streamName)).isNull();
	}

	@Test
	public void crdInCustomNamespaceTest() {
		OpenShiftUtil openshift = getInstance();

		String streamName = "is-1";

		ImageStream created = openshift.createImageStream(new ImageStreamBuilder().withNewMetadata().withName(streamName).endMetadata().build(), customTestNamespace);
		Assertions.assertThat(created.getMetadata().getName()).isEqualTo(streamName);
		Assertions.assertThat(created.getMetadata().getCreationTimestamp()).isNotNull();

		ImageStream is = openshift.getImageStream(streamName, customTestNamespace);
		Assertions.assertThat(is.getMetadata().getName()).isEqualTo(streamName);

		boolean deleted = openshift.deleteImageStream(streamName, customTestNamespace);
		Assertions.assertThat(deleted).isTrue();
		Assertions.assertThat(openshift.getImageStream(streamName, customTestNamespace)).isNull();
	}
}
