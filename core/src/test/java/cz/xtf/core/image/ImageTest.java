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

    @Test
    public void testImageFromUrlOneToken() {
        Image image = Image.from("nginx");
        Assertions.assertTrue(image.getRegistry().isEmpty(), "Registry for image url: 'nginx' must be empty.");
        Assertions.assertTrue(image.getUser().isEmpty(), "User for image url: 'nginx' must be empty.");
        Assertions.assertEquals("nginx", image.getRepo(), "Wrong repository name.");
        Assertions.assertTrue(image.getTag().isEmpty(), "Tag for image url: 'nginx' must be empty.");
    }

    @Test
    public void testImageFromUrlTwoTokens() {
        Image image = Image.from("user/nginx");
        Assertions.assertTrue(image.getRegistry().isEmpty(), "Registry for image url: 'user/nginx' must be empty.");
        Assertions.assertEquals("user", image.getUser(), "User for image url: 'user/nginx' is wrong.");
        Assertions.assertEquals("nginx", image.getRepo(), "Wrong repository name.");
        Assertions.assertTrue(image.getTag().isEmpty(), "Tag for image url: 'user/nginx' must be empty.");
    }

    @Test
    public void testImageFromUrlThreeTokens() {
        Image image = Image.from("quay.io/jaegertracing/all-in-one:1.56");
        Assertions.assertEquals("quay.io", image.getRegistry(), "Wrong registry parsed from image url.");
        Assertions.assertEquals("jaegertracing", image.getUser(), "Wrong user parsed from image url.");
        Assertions.assertEquals("all-in-one", image.getRepo(), "Wrong repository name.");
        Assertions.assertEquals("1.56", image.getTag(), "Wrong tag parsed from image url.");
    }

    @Test
    public void testImageFromUrlFourTokens() {
        Image image = Image.from("mcr.microsoft.com/mssql/rhel/server:2022-CU13-rhel-9.1");
        Assertions.assertEquals("mcr.microsoft.com", image.getRegistry(), "Wrong registry parsed from image url.");
        Assertions.assertEquals("mssql", image.getUser(), "Wrong user parsed from image url.");
        Assertions.assertEquals("rhel/server", image.getRepo(), "Wrong repository name.");
        Assertions.assertEquals("2022-CU13-rhel-9.1", image.getTag(), "Wrong tag parsed from image url.");
    }

    @Test
    public void testImageFromUrlFourPlusTokens() {
        Image image = Image.from("mycompany.registry.com/user/team-b/subteam-c/myservice/subservice/subsubservice:1.2.3");
        Assertions.assertEquals("mycompany.registry.com", image.getRegistry(), "Wrong registry parsed from image url.");
        Assertions.assertEquals("user", image.getUser(), "Wrong user parsed from image url.");
        Assertions.assertEquals("team-b/subteam-c/myservice/subservice/subsubservice", image.getRepo(),
                "Wrong repository name.");
        Assertions.assertEquals("1.2.3", image.getTag(), "Wrong tag parsed from image url.");
    }
}
