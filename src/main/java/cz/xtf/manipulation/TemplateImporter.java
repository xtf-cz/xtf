package cz.xtf.manipulation;

import cz.xtf.http.HttpClient;
import cz.xtf.openshift.OpenshiftUtil;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.openshift.api.model.DoneableTemplate;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.dsl.TemplateResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Util class responsible for template manipulation.
 */
public final class TemplateImporter {

	private TemplateImporter() {
		// util class, do not initialize
	}

	/**
	 * Imports template into <strong>CURRENT</strong> namespace.
	 *
	 * <p>
	 * This behavior might change in the future if the
	 * <a href="https://github.com/fabric8io/kubernetes-client/issues/382>issue #382</a> is resolved.
	 * </p>
	 *
	 * @param source Template URL
	 * @return created template
	 */
	public static Template importTemplate(String source) {
		return OpenshiftUtil.getInstance().withDefaultUser(client -> {
			try (InputStream is = new ByteArrayInputStream(HttpClient.get(source).response().getBytes(UTF_8))) {
				// load the template
				final TemplateResource<Template, KubernetesList, DoneableTemplate> template
						= client.templates().load(is);
				// remove old instance
				client.templates().withName(template.get().getMetadata().getName()).delete();
				// create new template
				return template.create();
			} catch (MalformedURLException ex) {
				throw new RuntimeException("Malformed template url", ex);
			} catch (IOException ex) {
				throw new RuntimeException("Unable to import template", ex);
			}
		});
	}
}
