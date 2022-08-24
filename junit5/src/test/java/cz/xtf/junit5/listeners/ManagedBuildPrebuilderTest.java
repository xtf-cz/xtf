package cz.xtf.junit5.listeners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.core.bm.BinaryBuild;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.junit5.annotations.SinceVersion;
import cz.xtf.junit5.annotations.SkipFor;
import cz.xtf.junit5.annotations.UsesBuild;
import cz.xtf.junit5.interfaces.BuildDefinition;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
class ManagedBuildPrebuilderTest {

    @SystemStub
    private SystemProperties systemProperties;

    @BeforeEach
    public void before() {
        systemProperties.set("xtf.eap.subid", "test");
        // tests are relying on this specific version, change with caution when needed
        systemProperties.set("xtf.eap.test.image", "quay.io/wildfly/wildfly-s2i-jdk11:26.0");
        XTFConfig.loadConfig();
    }

    @Test
    public void buildFromClassWithoutSkipConditionsIsNotSkipped() {
        Assertions.assertThat(ManagedBuildPrebuilder.shouldSkipBuildsForClass(TestClassTest.class)).isFalse();
    }

    @Test
    public void buildFromSkippedClassIsSkipped() {
        Assertions.assertThat(ManagedBuildPrebuilder.shouldSkipBuildsForClass(TestClassSkippedForImageTest.class)).isTrue();
    }

    @Test
    public void buildFromClassSkippedDueToLowVersionIsSkipped() {
        Assertions.assertThat(ManagedBuildPrebuilder.shouldSkipBuildsForClass(TestClassSkippedForLowerVersionTest.class))
                .isTrue();
    }

    @Test
    public void buildFromClassWithHighEnoughVersionIsNotSkipped() {
        System.setProperty("xtf.eap.test.image", "quay.io/wildfly/wildfly-s2i-jdk11:27.0");
        XTFConfig.loadConfig();

        Assertions.assertThat(ManagedBuildPrebuilder.shouldSkipBuildsForClass(TestClassSkippedForLowerVersionTest.class))
                .isFalse();
    }

    @Test
    public void buildFromDisabledClassIsSkipped() {
        Assertions.assertThat(ManagedBuildPrebuilder.shouldSkipBuildsForClass(TestClassDisabledTest.class)).isTrue();
    }

    @Test
    public void buildFromClassMatchingAllSkipConditionsIsSkipped() {
        Assertions.assertThat(ManagedBuildPrebuilder.shouldSkipBuildsForClass(TestClassSkippedByAllConditionsTest.class))
                .isTrue();
    }

    // --- test resources ---
    @UsesTestBuild(TestBuilds.testBuild)
    static class TestClassTest {
    }

    @SkipFor(image = "eap", name = "wildfly-s2i-jdk11", reason = "This test is skipped based on the image name.")
    @UsesTestBuild(TestBuilds.testBuild)
    static class TestClassSkippedForImageTest {
    }

    @SinceVersion(image = "eap", name = "wildfly-s2i-jdk11", since = "27.0", jira = "JIRA-1234")
    @UsesTestBuild(TestBuilds.testBuild)
    static class TestClassSkippedForLowerVersionTest {
    }

    @Disabled("Some reason given")
    @UsesTestBuild(TestBuilds.testBuild)
    static class TestClassDisabledTest {
    }

    @SkipFor(image = "eap", name = "wildfly-s2i-jdk11", reason = "This test is skipped based on the image name.")
    @SinceVersion(image = "eap", name = "wildfly-s2i-jdk11", since = "27.0", jira = "JIRA-1234")
    @Disabled("Some reason given")
    @UsesTestBuild(TestBuilds.testBuild)
    static class TestClassSkippedByAllConditionsTest {
    }

    enum TestBuilds implements BuildDefinition<BinaryBuild> {
        testBuild;

        @Override
        public BinaryBuild getManagedBuild() {
            return null;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @UsesBuild
    @interface UsesTestBuild {
        TestBuilds value();
    }

}
