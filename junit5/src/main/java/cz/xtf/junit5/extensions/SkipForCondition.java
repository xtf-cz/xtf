package cz.xtf.junit5.extensions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.annotations.SkipFor;
import cz.xtf.junit5.annotations.SkipFors;
import cz.xtf.junit5.model.DockerImageMetadata;

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
                if (cer.isDisabled())
                    return cer;
            }
            return ConditionEvaluationResult.enabled("Feature is expected to be available.");
        }

        return ConditionEvaluationResult.enabled("SkipFor(s) annotation isn't present on target.");
    }

    public static ConditionEvaluationResult resolve(SkipFor skipFor) {
        if (skipFor.name().equals("") == skipFor.imageMetadataLabelName().equals("")) {
            throw new RuntimeException(
                    "Only one of 'name' and 'imageMetadataLabelName' can be presented in 'SkipFor' annotation.");
        }
        Image image = Image.resolve(skipFor.image());
        Matcher matcher;

        if (!skipFor.name().equals("")) {
            matcher = Pattern.compile(skipFor.name()).matcher(image.getRepo());
        } else {
            DockerImageMetadata metadata = DockerImageMetadata.get(OpenShifts.master(), image);
            matcher = Pattern.compile(skipFor.imageMetadataLabelName()).matcher(metadata.labels().get("name"));
        }

        if (matcher.matches()) {
            String reason = skipFor.reason().equals("") ? "" : " (" + skipFor.reason() + ")";
            return ConditionEvaluationResult
                    .disabled("Tested feature isn't expected to be available in '" + image.getRepo() + "' image." + reason);
        } else {
            return ConditionEvaluationResult.enabled("Image '" + image.getRepo() + "' is expected to contain tested feature.");
        }
    }
}
