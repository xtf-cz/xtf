package cz.xtf.junit5.extensions;

import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.annotations.SkipFor;
import cz.xtf.junit5.annotations.SkipFors;
import cz.xtf.junit5.model.DockerImageMetadata;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class SkipForCondition implements ExecutionCondition {
	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		SkipFor skipFor = AnnotationSupport.findAnnotation(context.getElement(), SkipFor.class).orElse(null);
		SkipFors skipFors = AnnotationSupport.findAnnotation(context.getElement(), SkipFors.class).orElse(null);

		if (skipFor != null) {
			return resolve(skipFor);
		} else if (skipFors != null) {
			for (SkipFor sf : skipFors.value()) {
				ConditionEvaluationResult cer = resolve(sf);
				if (cer.isDisabled()) return cer;
			}
			return  ConditionEvaluationResult.enabled("Feature is expected to be available.");
		}

		return ConditionEvaluationResult.enabled("SkipFor(s) annotation isn't present on target.");
	}

	public static ConditionEvaluationResult resolve(SkipFor skipFor) {
		Image image = Image.resolve(skipFor.image());
		DockerImageMetadata metadata = DockerImageMetadata.prepare(OpenShifts.master(), image);
		String[] imageNameParts = metadata.getNameLabelValue().split("/");
		String actualImageName = imageNameParts.length == 2 ? imageNameParts[1] : imageNameParts[0];
		if (skipFor.name().equals(actualImageName)) {
			String reason = skipFor.reason().equals("") ? "" : " (" + skipFor.reason() + ")";
			return ConditionEvaluationResult.disabled("Tested feature isn't expected to be available in '" + image.getRepo() + "' image." + reason);
		} else {
			return ConditionEvaluationResult.enabled("Image '" + image.getRepo() + "' is expected to contain tested feature.");
		}
	}
}
