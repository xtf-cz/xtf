package cz.xtf.core.image;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.core.config.XTFConfig;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class ImageTest {

    public static final String EAP_IMAGE_URL = "registry.redhat.io/jboss-eap-8/eap8-openjdk17-builder-openshift-rhel8:latest";
    public static final String EAP_IMAGE_SUBID_NAME = "wildfly-openjdk17";
    public static final String EAP_IMAGE_SUBID_URL = "quay.io/wildfly/wildfly-s2i:latest-jdk17";

    @SystemStub
    private SystemProperties systemProperties;

    @Test
    public void testResolveImageWithSubId() {
        systemProperties.set("xtf.eap.image", EAP_IMAGE_URL);
        systemProperties.set("xtf.eap.subid", EAP_IMAGE_SUBID_NAME);
        systemProperties.set("xtf.eap." + EAP_IMAGE_SUBID_NAME + ".image", EAP_IMAGE_SUBID_URL);
        XTFConfig.loadConfig();
        Image image = Image.resolve("eap");
        Assertions.assertNotNull(image, "No image was found.");
        Assertions.assertEquals(EAP_IMAGE_SUBID_URL, image.getUrl(), "Resolved image is wrong.");

    }

    @Test
    public void testResolveImageWithoutSubidImage() {
        systemProperties.set("xtf.eap.image", EAP_IMAGE_URL);
        systemProperties.set("xtf.eap.subid", EAP_IMAGE_SUBID_NAME);
        XTFConfig.loadConfig();
        Image image = Image.resolve("eap");
        Assertions.assertNotNull(image, "No image was found.");
        Assertions.assertEquals(EAP_IMAGE_URL, image.getUrl(), "Resolved image is wrong.");
    }

    @Test
    public void testResolveImageWithoutSubidName() {
        systemProperties.set("xtf.eap.image", EAP_IMAGE_URL);
        systemProperties.set("xtf.eap." + EAP_IMAGE_SUBID_NAME + ".image", EAP_IMAGE_SUBID_URL);
        XTFConfig.loadConfig();
        Image image = Image.resolve("eap");
        Assertions.assertNotNull(image, "No image was found.");
        Assertions.assertEquals(EAP_IMAGE_URL, image.getUrl(), "Resolved image is wrong.");
    }

    @Test
    public void testResolveWithCustomRegistry() {
        systemProperties.set("xtf.eap.image", EAP_IMAGE_URL);
        systemProperties.set("xtf.eap.subid", EAP_IMAGE_SUBID_NAME);
        systemProperties.set("xtf.eap." + EAP_IMAGE_SUBID_NAME + ".image", EAP_IMAGE_SUBID_URL);
        systemProperties.set("xtf.eap.reg", "custom.registry.io");
        XTFConfig.loadConfig();
        Image image = Image.resolve("eap");
        Assertions.assertEquals("custom.registry.io", image.getRegistry());
        Assertions.assertEquals("wildfly", image.getUser());
        Assertions.assertEquals("wildfly-s2i", image.getRepo());
        Assertions.assertEquals("latest-jdk17", image.getTag());
    }

    @Test
    public void resolveWithCustomUserTest() {
        systemProperties.set("xtf.eap.image", EAP_IMAGE_URL);
        systemProperties.set("xtf.eap.subid", EAP_IMAGE_SUBID_NAME);
        systemProperties.set("xtf.eap." + EAP_IMAGE_SUBID_NAME + ".image", EAP_IMAGE_SUBID_URL);
        systemProperties.set("xtf.eap.user", "customuser");
        XTFConfig.loadConfig();
        Image image = Image.resolve("eap");
        Assertions.assertEquals("quay.io", image.getRegistry());
        Assertions.assertEquals("customuser", image.getUser());
        Assertions.assertEquals("wildfly-s2i", image.getRepo());
        Assertions.assertEquals("latest-jdk17", image.getTag());
    }

    @Test
    public void resolveWithCustomTagTest() {
        systemProperties.set("xtf.eap.image", EAP_IMAGE_URL);
        systemProperties.set("xtf.eap.subid", EAP_IMAGE_SUBID_NAME);
        systemProperties.set("xtf.eap." + EAP_IMAGE_SUBID_NAME + ".image", EAP_IMAGE_SUBID_URL);
        systemProperties.set("xtf.eap.tag", "customtag");
        XTFConfig.loadConfig();
        Image image = Image.resolve("eap");
        Assertions.assertEquals("quay.io", image.getRegistry());
        Assertions.assertEquals("wildfly", image.getUser());
        Assertions.assertEquals("wildfly-s2i", image.getRepo());
        Assertions.assertEquals("customtag", image.getTag());
    }

    @Test
    public void testNotResolvableImage() {
        XTFConfig.loadConfig();
        Assertions.assertThrows(UnknownImageException.class, () -> Image.resolve("blabla"),
                "Expected UnknownImageException to be thrown for unknown image.");
    }
}
