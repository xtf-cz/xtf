package cz.xtf.junit5.extensions;

import cz.xtf.core.image.Image;
import cz.xtf.junit5.annotations.SinceVersion;
import cz.xtf.junit5.annotations.SinceVersions;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class SinceVersionCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		SinceVersion sinceVersion = AnnotationSupport.findAnnotation(context.getElement(), SinceVersion.class).orElse(null);
		SinceVersions sinceVersions = AnnotationSupport.findAnnotation(context.getElement(), SinceVersions.class).orElse(null);

		if (sinceVersion != null) {
			return this.resolve(sinceVersion);
		} else if (sinceVersions != null) {
			for (SinceVersion sv : sinceVersions.value()) {
				ConditionEvaluationResult cer = this.resolve(sv);
				if (cer.isDisabled()) return cer;
			}
			return ConditionEvaluationResult.enabled("Feature is expected to be available.");
		}

		return ConditionEvaluationResult.enabled("SinceVersion annotation isn't present on target.");
	}

	private ConditionEvaluationResult resolve(SinceVersion sinceVersion) {
		Image image = Image.resolve(sinceVersion.image());
		if (image.getRepo().equals(sinceVersion.name())) {
			if (image.isVersionAtLeast(sinceVersion.since())) {
				return ConditionEvaluationResult.enabled("'" + image.getRepo() + "' image tag is equal or bigger then expected. Tested feature should be available.");
			} else {
				String jiraInfo = sinceVersion.jira().equals("") ? "" : " See " + sinceVersion.jira() + " for more info.";
				String message = "Tested feature isn't expected to be present in " + image.getRepo() + ":" + image.getTag() + "'. At least " + sinceVersion.since() + " tag is expected." + jiraInfo;
				return ConditionEvaluationResult.disabled(message);
			}
		} else {
			return ConditionEvaluationResult.enabled("Image '" + image.getRepo() + "' is expected to contain tested feature.");
		}
	}
}
