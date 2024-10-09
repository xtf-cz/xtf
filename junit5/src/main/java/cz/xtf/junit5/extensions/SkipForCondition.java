package cz.xtf.junit5.extensions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.image.Image;
import cz.xtf.core.image.UnknownImageException;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.annotations.SkipFor;
import cz.xtf.junit5.annotations.SkipFors;
import cz.xtf.junit5.model.DockerImageMetadata;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        if (detectMultipleSkipForCriteria(skipFor)) {
            throw new RuntimeException(
                    "Only one of 'name', 'imageMetadataLabelName', 'imageMetadataLabelArchitecture' and 'subId' can be presented in 'SkipFor' annotation.");
        }

        Image image = null;
        try {
            image = Image.resolve(skipFor.image());
        } catch (UnknownImageException uie) {
            return ConditionEvaluationResult.enabled(
                    "Cannot get image for '" + skipFor.image()
                            + "' therefore cannot evaluate skip condition properly. Continue in test.");
        }

        Matcher matcher;

        if (!skipFor.name().equals("")) {
            matcher = Pattern.compile(skipFor.name()).matcher(image.getRepo());
        } else if (!skipFor.imageMetadataLabelName().equals("")) {
            DockerImageMetadata metadata = DockerImageMetadata.get(OpenShifts.master(OpenShiftConfig.namespace()),
                    image);
            matcher = Pattern.compile(skipFor.imageMetadataLabelName()).matcher(metadata.labels().get("name"));
        } else if (!skipFor.imageMetadataLabelArchitecture().equals("")) {
            DockerImageMetadata metadata = DockerImageMetadata.get(OpenShifts.master(OpenShiftConfig.namespace()),
                    image);
            matcher = Pattern.compile(skipFor.imageMetadataLabelArchitecture()).matcher(metadata.labels().get("architecture"));
        } else {
            matcher = Pattern.compile(skipFor.subId()).matcher(XTFConfig.get("xtf." + skipFor.image() + ".subid"));
        }
        if (matcher.matches()) {
            String reason = skipFor.reason().equals("") ? "" : " (" + skipFor.reason() + ")";
            return ConditionEvaluationResult
                    .disabled("Tested feature isn't expected to be available in '" + image.getRepo() + "' image." + reason);
        } else {
            return ConditionEvaluationResult.enabled("Image '" + image.getRepo() + "' is expected to contain tested feature.");
        }
    }

    private static boolean detectMultipleSkipForCriteria(SkipFor skipFor) {
        return Stream
                .of(skipFor.name(), skipFor.imageMetadataLabelName(), skipFor.subId(), skipFor.imageMetadataLabelArchitecture())
                .filter(c -> StringUtils.isNotBlank(c)).count() > 1;
    }
}
