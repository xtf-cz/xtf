package cz.xtf.junit5.extensions;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.junit5.annotations.SkipFor;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
class SkipForConditionTest {

    @SystemStub
    private SystemProperties systemProperties;

    @BeforeEach
    void before() {
        systemProperties.set("xtf.eap.subid", "74-openjdk11");
        systemProperties.set("xtf.eap.74-openjdk11.image",
                "registry-proxy.engineering.redhat.com/rh-osbs/jboss-eap-7-eap74-openjdk8-openshift-rhel7:7.4.0-6");
    }

    @SkipFor(image = "eap", name = ".*eap-74.*", reason = "This test is skipped based on the image name.")
    class WellAnnotatedImageNameBasedSkipTest {

    }

    @SkipFor(image = "eap", imageMetadataLabelName = "centos7/s2i-base-centos7", reason = "This test is skipped based on the image docker labels")
    class WellAnnotatedImageMetadataLabelBasedSkipTest {

    }

    @SkipFor(image = "eap", imageMetadataLabelArchitecture = "s390x", reason = "This test is skipped based on the image metadata label architecture")
    class WellAnnotatedImageMetadataLabelArchitectureBasedSkipTest {

    }

    @SkipFor(image = "eap", subId = ".*74.*", reason = "This test is skipped based on the image product subId")
    class WellAnnotatedSubIdBasedSkipTest {

    }

    @SkipFor(image = "eap", name = ".*eap-xp1.*", imageMetadataLabelName = "centos7/s2i-base-centos7", reason = "This test SHOULD BE skipped based on the image name.")
    class BadlyAnnotatedImageNameBasedSkipTest {

    }

    @SkipFor(image = "eap", name = ".*eap-xp1.*", imageMetadataLabelName = "centos7/s2i-base-centos7", reason = "This test SHOULD BE skipped based on the image docker labels")
    class BadlyAnnotatedImageMetadataLabelBasedSkipTest {

    }

    @SkipFor(image = "eap", name = ".*eap-xp4.*", imageMetadataLabelArchitecture = "s390x", reason = "This test SHOULD BE skipped based on the image metadata label architecture")
    class BadlyAnnotatedImageMetadataLabelArchitectureBasedSkipTest {

    }

    @SkipFor(image = "eap", name = ".*eap-xp1.*", imageMetadataLabelName = "centos7/s2i-base-centos7", subId = ".*74.*", reason = "This test SHOULD BE skipped based on the image product subId")
    class BadlyAnnotatedSubIdBasedSkipTest {

    }

    @Test
    void testUniqueCriteriaResolutionOnWellAnnotatedClasses() {
        Stream<Class> workingClasses = Stream.of(
                WellAnnotatedImageNameBasedSkipTest.class,
                WellAnnotatedImageMetadataLabelBasedSkipTest.class,
                WellAnnotatedImageMetadataLabelArchitectureBasedSkipTest.class,
                WellAnnotatedSubIdBasedSkipTest.class);
        workingClasses.forEach(k -> {
            try {
                SkipForCondition.resolve((SkipFor) Arrays.stream(k.getAnnotationsByType(SkipFor.class)).findFirst().get());
            } catch (RuntimeException re) {
                //  we should be able to do better things here, as for instance using a specific Exception type
                if (re.getMessage().equals(
                        "Only one of 'name', 'imageMetadataLabelName', 'imageMetadataLabelArchitecture' and 'subId' can be presented in 'SkipFor' annotation.")) {
                    Assertions.fail(String.format("No exception is expected when resolving %s \"@SkipFor\" annotation",
                            k.getSimpleName()));
                }
            }
        });
    }

    @Test
    void testUniqueCriteriaResolutionOnBadlyAnnotatedClasses() {
        Stream<Class> workingClasses = Stream.of(
                BadlyAnnotatedImageNameBasedSkipTest.class,
                BadlyAnnotatedImageMetadataLabelBasedSkipTest.class,
                BadlyAnnotatedImageMetadataLabelArchitectureBasedSkipTest.class,
                BadlyAnnotatedSubIdBasedSkipTest.class);
        workingClasses.forEach(k -> {
            Exception exception = Assertions.assertThrows(RuntimeException.class, () -> SkipForCondition
                    .resolve((SkipFor) Arrays.stream(k.getAnnotationsByType(SkipFor.class)).findFirst().get()));
            Assertions.assertEquals(
                    "Only one of 'name', 'imageMetadataLabelName', 'imageMetadataLabelArchitecture' and 'subId' can be presented in 'SkipFor' annotation.",
                    exception.getMessage());
        });
    }

    @Test
    void testSubIdBasedSkipForResolution() {
        ConditionEvaluationResult conditionEvaluationResult = SkipForCondition.resolve(
                Arrays.stream(WellAnnotatedSubIdBasedSkipTest.class.getAnnotationsByType(SkipFor.class)).findFirst().get());
        Assertions.assertTrue(conditionEvaluationResult.isDisabled(), "This test should be disabled via \"subId\"");
    }
}
