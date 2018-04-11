package cz.xtf;

import java.util.Map;
import java.util.Properties;

public class TestConfiguration extends XTFConfiguration {

	public static final String EAP_LOCATION = "xtf.config.eap.location";
	public static final String IMAGE_EAP_PREFIX = "xtf.eap.";
	public static final String IMAGE_PREVIOUS_SUFFIX = ".previous";
	public static final String IMAGE_EAP_6 = IMAGE_EAP_PREFIX + "6";
	public static final String IMAGE_EAP_7 = IMAGE_EAP_PREFIX + "7";
	public static final String IMAGE_JDG = "xtf.jdg";
	public static final String IMAGE_JDG_CLIENT = "xtf.jdg.client";
	public static final String IMAGE_JDV = "xtf.jdv";
	public static final String IMAGE_JDV_CLIENT = "xtf.jdv.client";
	public static final String IMAGE_JDV_ODBC_TEST_IMAGE = "xtf.jdv.odbc.test";
	public static final String IMAGE_EWS_PREFIX = "org.apache.tomcat";
	public static final String IMAGE_TOMCAT7 = IMAGE_EWS_PREFIX + "7";
	public static final String IMAGE_TOMCAT8 = IMAGE_EWS_PREFIX + "8";
	public static final String IMAGE_AMQ = "xtf.amq";
	public static final String IMAGE_POSTGRES = "org.postgresql";
	public static final String IMAGE_DERBY = "org.apache.derby";
	public static final String IMAGE_MYSQL = "com.mysql";
	public static final String IMAGE_MONGO = "com.mongodb";
	public static final String IMAGE_NFS = "org.nfs";
	public static final String IMAGE_FUSE_JAVA_MAIN = "org.fuse.java_main";
	public static final String IMAGE_FUSE_KARAF = "org.fuse.karaf";
	public static final String IMAGE_FUSE_EAP = "org.fuse.eap";
	public static final String IMAGE_BRMS = "xtf.brms";
	public static final String IMAGE_BPMS = "xtf.bpms";
	public static final String IMAGE_BPMS_LDAP_TEST_IMAGE = "xtf.bpms.ldap.test";
	public static final String IMAGE_SSO = "xtf.sso";
	public static final String IMAGE_PHANTOMJS = "xtf.phantomjs";
	public static final String IMAGE_H2 = "xtf.h2";
	public static final String IMAGE_MSA = "xtf.msa";
	public static final String IMAGE_ZIPKIN = "io.zipkin.java";
	public static final String IMAGE_SQUID = "org.squid-cache";
	public static final String IMAGE_TCP_PROXY = "xtf.tcp-proxy";
	public static final String IMAGE_MM_SERVICE = "org.hawkular.service";
	public static final String IMAGE_MM_DATASTORE = "org.hawkular.datastore";

	public static final String VERSION_EAP = "xtf.version.eap";
	public static final String VERSION_JDV = "xtf.version.jdv";
	public static final String VERSION_JDG = "xtf.version.jdg";
	public static final String VERSION_EWS = "xtf.version.ews";
	public static final String VERSION_FUSE = "xtf.version.fuse";
	public static final String VERSION_KIE = "xtf.version.kie";
	public static final String VERSION_JDK = "xtf.version.jdk";
	public static final String VERSION_SSO = "xtf.version.sso";
	public static final String VERSION_AMQ = "xtf.version.amq";
	public static final String VERSION_MSA = "xtf.version.msa";

	public static final String CDK_INTERNAL_HOSTNAME = "localhost.localdomain";

	public static final String CI_USERNAME = "ci.username";
	public static final String CI_PASSWORD = "ci.password";

	private TestConfiguration() {

		super();

	}

	public static String ciUsername() {
		return get().readValue(CI_USERNAME);
	}

	public static String ciPassword() {
		return get().readValue(CI_PASSWORD);
	}

	public static String ocBinaryLocation() {
		return get().readValue(OC_BINARY_LOCATION);
	}

	public static String kieVersion() {
		return get().readValue(VERSION_KIE);
	}

	public static String getFuseVersion() {
		return get().readValue(VERSION_FUSE);
	}

	public static String getMsaVersion() {
		return get().readValue(VERSION_MSA);
	}

	@Override
	protected Properties fromEnvironment() {
		final Properties props = new Properties();

		for (final Map.Entry<String, String> entry : System.getenv()
				.entrySet()) {
			switch (entry.getKey()) {
				case "IMAGE_EAP_6":
					props.setProperty(IMAGE_EAP_6, entry.getValue());
					break;
				case "IMAGE_EAP_7":
					props.setProperty(IMAGE_EAP_7, entry.getValue());
					break;
				case "IMAGE_JDG":
					props.setProperty(IMAGE_JDG, entry.getValue());
					break;
				case "IMAGE_JDG_CLIENT":
					props.setProperty(IMAGE_JDG_CLIENT, entry.getValue());
					break;
				case "IMAGE_JDV":
					props.setProperty(IMAGE_JDV, entry.getValue());
					break;
				case "IMAGE_JDV_CLIENT":
					props.setProperty(IMAGE_JDV_CLIENT, entry.getValue());
					break;
				case "IMAGE_TOMCAT7":
					props.setProperty(IMAGE_TOMCAT7, entry.getValue());
					break;
				case "IMAGE_TOMCAT8":
					props.setProperty(IMAGE_TOMCAT8, entry.getValue());
					break;
				case "IMAGE_AMQ":
					props.setProperty(IMAGE_AMQ, entry.getValue());
					break;
				case "IMAGE_POSTGRES":
					props.setProperty(IMAGE_POSTGRES, entry.getValue());
					break;
				case "IMAGE_DERBY":
					props.setProperty(IMAGE_DERBY, entry.getValue());
					break;
				case "IMAGE_MYSQL":
					props.setProperty(IMAGE_MYSQL, entry.getValue());
					break;
				case "IMAGE_MONGO":
					props.setProperty(IMAGE_MONGO, entry.getValue());
					break;
				case "IMAGE_NFS":
					props.setProperty(IMAGE_NFS, entry.getValue());
					break;
				case "IMAGE_FUSE_JAVA_MAIN":
					props.setProperty(IMAGE_FUSE_JAVA_MAIN, entry.getValue());
					break;
				case "IMAGE_FUSE_KARAF":
					props.setProperty(IMAGE_FUSE_KARAF, entry.getValue());
					break;
				case "IMAGE_FUSE_EAP":
					props.setProperty(IMAGE_FUSE_EAP, entry.getValue());
					break;
				case "IMAGE_BRMS":
					props.setProperty(IMAGE_BRMS, entry.getValue());
					break;
				case "IMAGE_BPMS":
					props.setProperty(IMAGE_BPMS, entry.getValue());
					break;
				case "IMAGE_BPMS_PREVIOUS":
					props.setProperty(IMAGE_BPMS + IMAGE_PREVIOUS_SUFFIX, entry.getValue());
					break;
				case "IMAGE_BPMS_LDAP_TEST":
					props.setProperty(IMAGE_BPMS_LDAP_TEST_IMAGE, entry.getValue());
					break;
				case "IMAGE_SSO":
					props.setProperty(IMAGE_SSO, entry.getValue());
					break;
				case "IMAGE_MSA":
					props.setProperty(IMAGE_MSA, entry.getValue());
					break;
				case "IMAGE_SQUID":
					props.setProperty(IMAGE_SQUID, entry.getValue());
					break;
				case "IMAGE_TCP_PROXY":
					props.setProperty(IMAGE_TCP_PROXY, entry.getValue());
					break;
				case "IMAGE_PHANTOMJS":
					props.setProperty(IMAGE_PHANTOMJS, entry.getValue());
					break;
				case "IMAGE_MM_SERVICE":
					props.setProperty(IMAGE_MM_SERVICE, entry.getValue());
					break;
				case "IMAGE_MM_DATASTORE":
					props.setProperty(IMAGE_MM_DATASTORE, entry.getValue());
					break;
				case "CI_USERNAME":
					props.setProperty(CI_USERNAME, entry.getValue());
					break;
				case "CI_PASSWORD":
					props.setProperty(CI_PASSWORD, entry.getValue());
					break;
				case "VERSION_EAP":
					props.setProperty(VERSION_EAP, entry.getValue());
					break;
				case "VERSION_JDV":
					props.setProperty(VERSION_JDV, entry.getValue());
					break;
				case "VERSION_JDG":
					props.setProperty(VERSION_JDG, entry.getValue());
					break;
				case "VERSION_EWS":
					props.setProperty(VERSION_EWS, entry.getValue());
					break;
				case "VERSION_FUSE":
					props.setProperty(VERSION_FUSE, entry.getValue());
					break;
				case "VERSION_KIE":
					props.setProperty(VERSION_KIE, entry.getValue());
					break;
				case "VERSION_JDK":
					props.setProperty(VERSION_JDK, entry.getValue());
					break;
				case "VERSION_SSO":
					props.setProperty(VERSION_SSO, entry.getValue());
					break;
				case "VERSION_AMQ":
					props.setProperty(VERSION_AMQ, entry.getValue());
					break;
				default:
					break;
			}
		}
		return props;
	}

	@Override
	protected Properties defaultValues(){
		final Properties properties = new Properties();
		properties.setProperty(VERSION_FUSE, "6.2.1");
		return properties;
	}
}
