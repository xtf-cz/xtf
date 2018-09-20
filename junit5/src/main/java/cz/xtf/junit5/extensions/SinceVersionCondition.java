package cz.xtf.junit5.extensions;

import cz.xtf.core.image.Image;
import cz.xtf.junit5.annotations.SinceVersion;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class SinceVersionCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		SinceVersion sinceVersion = AnnotationSupport.findAnnotation(context.getElement(), SinceVersion.class).orElse(null);
		if (sinceVersion != null) {
			Image image = Image.resolve(sinceVersion.image());
			if (image.getRepo().equals(sinceVersion.name())) {
				if (image.isVersionAtLeast(sinceVersion.since())) {
					return ConditionEvaluationResult.enabled("Image tag is equal or bigger then expected. Tested feature should be available.");
				} else {
					String jiraInfo = sinceVersion.jira().equals("") ? "" : " See " + sinceVersion.jira() + " for more info.";
					return ConditionEvaluationResult.disabled("Image tag didn't met target tag. Tested feature isn't expected to be present." + jiraInfo);
				}
			} else {
				return ConditionEvaluationResult.enabled("Expected image repo name didn't matched actual. Image is expected to contain tested feature.");
			}
		}
		return ConditionEvaluationResult.enabled("SinceVersion annotation isn't present on target.");
	}
}
