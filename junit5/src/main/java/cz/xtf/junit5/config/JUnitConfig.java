package cz.xtf.junit5.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cz.xtf.core.config.XTFConfig;

public class JUnitConfig {
    private static final String CLEAN_OPENSHIFT = "xtf.junit.clean_openshift";
    private static final String USED_IMAGES = "xtf.junit.used_images";
    private static final String CI_USERNAME = "xtf.junit.ci.username";
    private static final String CI_PASSWORD = "xtf.junit.ci.password";
    private static final String JENKINS_RERUN = "xtf.junit.jenkins.rerun";
    private static final String PREBUILDER_SYNCHRONIZED = "xtf.junit.prebuilder.synchronized";
    private static final String RECORD_DIR = "xtf.record.dir";
    private static final String RECORD_ALWAYS = "xtf.record.always";
    private static final String RECORD_BEFORE = "xtf.record.before";

    public static String recordDir() {
        return XTFConfig.get(RECORD_DIR);
    }

    public static boolean recordAlways() {
        return XTFConfig.get(RECORD_ALWAYS) != null
                && (XTFConfig.get(RECORD_ALWAYS).equals("") || XTFConfig.get(RECORD_ALWAYS).toLowerCase().equals("true"));
    }

    public static boolean recordBefore() {
        return XTFConfig.get(RECORD_BEFORE) != null
                && (XTFConfig.get(RECORD_BEFORE).equals("") || XTFConfig.get(RECORD_BEFORE).toLowerCase().equals("true"));
    }

    public static boolean cleanOpenShift() {
        return Boolean.valueOf(XTFConfig.get(CLEAN_OPENSHIFT, "false"));
    }

    public static List<String> usedImages() {
        final String images = XTFConfig.get(USED_IMAGES);
        if (images != null) {
            return Arrays.asList(images.split(","));
        } else {
            return Collections.emptyList();
        }
    }

    public static String ciUsername() {
        return XTFConfig.get(CI_USERNAME);
    }

    public static String ciPassword() {
        return XTFConfig.get(CI_PASSWORD);
    }

    public static String jenkinsRerun() {
        return XTFConfig.get(JENKINS_RERUN);
    }

    public static boolean prebuilderSynchronized() {
        return Boolean.parseBoolean(XTFConfig.get(PREBUILDER_SYNCHRONIZED, "false"));
    }
}
